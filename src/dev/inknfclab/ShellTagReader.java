package dev.inknfclab;

import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShellTagReader implements AutoCloseable {
    private final Object adapter;
    private final Method readerMethod;
    private final Binder token = new Binder();
    private final CountDownLatch completed = new CountDownLatch(1);
    private final AtomicBoolean received = new AtomicBoolean();
    private final Object callback;
    private volatile Exception failure;
    private volatile IsoDep connection;
    private volatile boolean closed;

    public ShellTagReader(Class<?> adapterType, Object adapter) throws Exception {
        this.adapter = adapter;
        Class<?> callbackType = Class.forName("android.nfc.IAppCallback");
        Class<?> stub = Class.forName("android.nfc.IAppCallback$Stub");
        java.lang.reflect.Field field = stub.getDeclaredField("TRANSACTION_onTagDiscovered");
        field.setAccessible(true);
        int discoveredCode = field.getInt(null);
        Binder binder = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code != discoveredCode) return super.onTransact(code, data, reply, flags);
                data.enforceInterface("android.nfc.IAppCallback");
                Tag tag = data.readInt() == 0 ? null : Tag.CREATOR.createFromParcel(data);
                if (tag != null && !closed && received.compareAndSet(false, true))
                    new Thread(() -> readInfo(tag), "ink-shell-tag-read").start();
                if (reply != null) reply.writeNoException();
                return true;
            }
        };
        callback = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType}, (proxy, method, args) -> {
            if (method.getName().equals("asBinder")) return binder;
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("toString")) return "InkShellTagCallback";
            return null;
        });
        Method selected;
        try {
            selected = adapterType.getMethod("setReaderMode", IBinder.class, callbackType, int.class, Bundle.class, String.class);
        } catch (NoSuchMethodException older) {
            selected = adapterType.getMethod("setReaderMode", IBinder.class, callbackType, int.class, Bundle.class);
        }
        readerMethod = selected;
    }

    public void start() throws Exception {
        setReader(NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS);
        System.out.println("SHELL_READER_STARTED=true API_ARGUMENTS=" + readerMethod.getParameterTypes().length);
    }

    public void await() throws Exception {
        if (!completed.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("No Tag/D1 within 15 seconds");
        if (failure != null) throw failure;
    }

    private void setReader(int flags) throws Exception {
        Bundle extras = new Bundle();
        extras.putInt("presence", 5000);
        if (readerMethod.getParameterTypes().length == 5)
            readerMethod.invoke(adapter, token, callback, flags, extras, "com.android.shell");
        else readerMethod.invoke(adapter, token, callback, flags, extras);
    }

    private void readInfo(Tag tag) {
        try {
            System.out.println("TAG_RECEIVED=true");
            IsoDep link = IsoDep.get(tag);
            if (link == null) throw new IllegalStateException("Tag has no ISO-DEP technology");
            connection = link;
            if (closed) throw new IllegalStateException("Reader closed");
            link.connect();
            link.setTimeout(3000);
            byte[] response = link.transceive(Protocol.READ_INFO);
            Protocol.DeviceInfo info = Protocol.parseDeviceInfo(response, tag.getId());
            if (!info.supportsImageTransfer()) throw new IllegalStateException("D1 target is not compatible with current image format");
            System.out.println("D1_CONFIRMED=true WIDTH=" + info.width + " HEIGHT=" + info.height + " COLORS=" + info.colors + " FIRMWARE=" + info.firmware);
        } catch (Exception error) {
            failure = error;
        } finally {
            IsoDep link = connection;
            if (link != null) try { link.close(); } catch (Exception ignored) {}
            connection = null;
            completed.countDown();
        }
    }

    @Override public void close() throws Exception {
        closed = true;
        try {
            setReader(0);
        } finally {
            IsoDep link = connection;
            if (link != null) link.close();
            System.out.println("SHELL_READER_RELEASED=true");
        }
    }
}
