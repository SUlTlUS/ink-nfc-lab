package dev.inknfclab;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class NxpQPollWorker {
    private static volatile Tag found;
    private static volatile boolean qtagEnabled;
    private static volatile boolean readerEnabled;
    private static volatile boolean restoreFailed;
    private static final AtomicBoolean cleaned = new AtomicBoolean();
    private static Object adapter;
    private static Class<?> adapterClass;
    private static Object callback;
    private static int discoveredCode;
    private static final IBinder readerToken = new Binder();
    private static final Binder callbackBinder = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString("android.nfc.IAppCallback");
                return true;
            }
            if (code != discoveredCode) return super.onTransact(code, data, reply, flags);
            data.enforceInterface("android.nfc.IAppCallback");
            Tag tag = data.readInt() == 0 ? null : Tag.CREATOR.createFromParcel(data);
            if (tag != null && found == null) found = tag;
            if (reply != null) reply.writeNoException();
            return true;
        }
    };

    private static void event(String kind, String message) {
        System.out.println("NXP|" + kind + "|" + message.replace('\n', ' '));
        System.out.flush();
    }

    private static Object call(String method, Class<?>[] types, Object... arguments) throws Exception {
        try { return adapterClass.getMethod(method, types).invoke(adapter, arguments); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception) throw (Exception) error.getCause();
            throw error;
        }
    }

    private static void reader(int flags) throws Exception {
        Bundle options = new Bundle();
        options.putInt("presence", 5000);
        call("setReaderMode", new Class<?>[]{IBinder.class, Class.forName("android.nfc.IAppCallback"),
                int.class, Bundle.class, String.class}, readerToken, callback, flags, options,
                android.os.Process.myUid() == 2000 ? "com.android.shell" : "android");
    }

    private static int qtag(int action) throws Exception {
        return (Integer) call("sendVendorNciMessage", new Class<?>[]{int.class, int.class, int.class, byte[].class},
                1, 15, 112, new byte[]{0x31, (byte) action});
    }

    private static void initialize() throws Exception {
        int uid = android.os.Process.myUid();
        if (uid != 0 && uid != 2000) throw new SecurityException("仅允许 ADB Shell 或 Root 运行");
        IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "nfc");
        if (service == null) throw new IllegalStateException("NFC 服务不可用");
        adapterClass = Class.forName("android.nfc.INfcAdapter");
        adapter = Class.forName("android.nfc.INfcAdapter$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, service);
        if ((Integer) call("getState", new Class<?>[0]) != 3) throw new IllegalStateException("请先开启 NFC");
        Class<?> callbackClass = Class.forName("android.nfc.IAppCallback");
        Field transaction = Class.forName("android.nfc.IAppCallback$Stub")
                .getDeclaredField("TRANSACTION_onTagDiscovered");
        transaction.setAccessible(true);
        discoveredCode = transaction.getInt(null);
        callback = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass},
                (proxy, method, arguments) -> method.getName().equals("asBinder") ? callbackBinder
                        : method.getName().equals("hashCode") ? System.identityHashCode(proxy)
                        : method.getName().equals("equals") ? proxy == arguments[0]
                        : method.getName().equals("toString") ? "InkNxpReader" : null);
    }

    private static void cleanup() {
        if (!cleaned.compareAndSet(false, true)) return;
        if (readerEnabled) try { reader(0); }
        catch (Exception error) { restoreFailed = true; event("RESTORE_ERROR", "ReaderMode: " + error); }
        if (qtagEnabled) try {
            if (qtag(0) != 0) { restoreFailed = true; event("RESTORE_ERROR", "QTag 关闭未确认"); }
        } catch (Exception error) { restoreFailed = true; event("RESTORE_ERROR", "QTag: " + error); }
    }

    public static void main(String[] args) {
        int exit = 0;
        IsoDep link = null;
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(NxpQPollWorker::cleanup));
            if (args.length != 2) throw new IllegalArgumentException("用法: NxpQPollWorker 图像文件 停止标记");
            File imageFile = new File(args[0]);
            File stopFile = new File(args[1]);
            if (imageFile.length() != ImageCodec.PACKED_LENGTH)
                throw new IllegalArgumentException("图像文件长度无效");
            byte[] image = Files.readAllBytes(imageFile.toPath());
            if (image.length != ImageCodec.PACKED_LENGTH) throw new IllegalArgumentException("图像长度无效");
            if (stopFile.exists()) throw new InterruptedException("会话已取消");
            initialize();
            qtagEnabled = true;
            int status = qtag(1);
            if (status != 0) throw new IllegalStateException("NXP QTag 被拒绝：" + status);
            readerEnabled = true;
            reader(0x81);
            event("READY", "QTag API 已接受；尚未确认空口 Q-Poll，等待驱动板");
            long deadline = SystemClock.elapsedRealtime() + 30000;
            while (found == null && !stopFile.exists() && SystemClock.elapsedRealtime() < deadline)
                SystemClock.sleep(50);
            if (stopFile.exists()) throw new InterruptedException("会话已取消");
            if (found == null) throw new IllegalStateException("30 秒内未发现驱动板；不能据此认定控制器不支持 Q-Poll");
            Tag tag = found;
            event("TAG", "UID=" + Protocol.hex(tag.getId()) + " / " + Arrays.toString(tag.getTechList()));
            link = IsoDep.get(tag);
            if (link == null) throw new IllegalStateException("目标没有 ISO-DEP");
            link.connect();
            link.setTimeout(3000);
            SystemClock.sleep(1000);
            byte[] response = link.transceive(Protocol.READ_INFO);
            Protocol.DeviceInfo info = Protocol.parseDeviceInfo(response, tag.getId());
            if (!info.supportsImageTransfer()) throw new IllegalStateException("D1 型号或 UID 校验不通过，拒绝刷写");
            event("D1", Protocol.deviceInfo(response, tag.getId()));
            long uploadDeadline = SystemClock.elapsedRealtime() + 300000;
            AtomicBoolean busySeen = new AtomicBoolean();
            AtomicLong refreshStarted = new AtomicLong();
            ImageTransfer.send(link, image, new ImageTransfer.Progress() {
                @Override public void update(String message, int completed, int total) {
                    if (message.equals("图片已发送，正在触发刷新")) refreshStarted.set(SystemClock.elapsedRealtime());
                    event("PROGRESS", completed + "/" + total + " "
                            + (message.equals("屏幕刷新完成") ? "DE 首次空闲，继续确认" : message));
                }
                @Override public boolean cancelled() {
                    return stopFile.exists() || SystemClock.elapsedRealtime() > uploadDeadline;
                }
                @Override public void trace(String message) {
                    if (message.startsWith("DE RX 019000")) busySeen.set(true);
                    event("TRACE", message);
                }
            });
            long refreshDeadline = refreshStarted.get() + 90000;
            int idleCount = 0;
            while (SystemClock.elapsedRealtime() < refreshDeadline) {
                if (stopFile.exists()) throw new InterruptedException("刷新确认期间已取消");
                link.setTimeout(5000);
                byte[] state = link.transceive(Protocol.READ_STATUS);
                Protocol.requireSuccess(state, "DE 追加确认");
                if (state.length != 3 || (state[0] != 0 && state[0] != 1))
                    throw new IllegalStateException("DE 返回未知忙状态");
                if (state[0] == 1) { busySeen.set(true); idleCount = 0; }
                else idleCount++;
                if (idleCount >= 3 && (busySeen.get() || SystemClock.elapsedRealtime() - refreshStarted.get() >= 20000)) break;
                SystemClock.sleep(500);
            }
            if (idleCount < 3) throw new IllegalStateException("90 秒内未确认连续空闲");
            if (!busySeen.get()) throw new IllegalStateException("DE 连续空闲但未观察到忙状态；画面可能已刷新，仍不能确认");
            event("COMPLETE", "已观察到忙转连续三次空闲；刷新完成，请目视确认画面");
        } catch (Throwable error) {
            event("ERROR", error.toString());
            exit = 1;
        } finally {
            if (link != null) try { link.close(); } catch (Exception ignored) {}
            cleanup();
            if (restoreFailed) exit = 2;
            event("END", exit == 0 ? "已恢复普通 NFC" : "已结束；如果 NFC 异常，请关闭再开启 NFC 开关");
        }
        System.exit(exit);
    }

    private NxpQPollWorker() {}
}
