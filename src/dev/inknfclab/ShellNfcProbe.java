package dev.inknfclab;

import android.os.IBinder;
import android.os.Binder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Paths;

public final class ShellNfcProbe {
    public static void main(String[] args) {
        try {
            inspect(args);
        } catch (Throwable failure) {
            while (failure instanceof java.lang.reflect.InvocationTargetException && failure.getCause() != null)
                failure = failure.getCause();
            System.out.println("ERROR=" + failure);
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void inspect(String[] args) throws Exception {
        if (Process.myUid() != 2000) throw new SecurityException("Run through ADB shell, not su");
        if (args.length > 1 || args.length == 1 && !Arrays.asList("read", "roundtrip", "probe", "restore").contains(args[0]))
            throw new IllegalArgumentException("Use no arguments, read, roundtrip, probe or restore");
        String mode = args.length == 0 ? "inspect" : args[0];
        System.out.println("BACKEND=ADB_SHELL UID=" + Process.myUid() + " MODE=" + mode);
        inspectPermissions();
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "nfc");
        if (binder == null) throw new IllegalStateException("NFC service unavailable");
        Class<?> adapterType = Class.forName("android.nfc.INfcAdapter");
        Object adapter = Class.forName("android.nfc.INfcAdapter$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
        System.out.println("NFC_STATE=" + adapterType.getMethod("getState").invoke(adapter));
        Method[] methods = adapterType.getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method method : methods) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("vendor") || name.contains("reader") || name.contains("polling") || name.contains("rfdiscover"))
                System.out.println("API=" + method.toGenericString());
        }
        if (args.length == 1) readPolling(adapterType, adapter, mode);
        System.out.println("RESULT=OK no image commands sent");
    }

    private static void inspectPermissions() {
        try {
            Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "permissionmgr");
            Object manager = Class.forName("android.permission.IPermissionManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
            Class<?> managerType = Class.forName("android.permission.IPermissionManager");
            for (Method method : managerType.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals("checkUidPermission") && Arrays.equals(parameters, new Class<?>[]{String.class, int.class}))
                    System.out.println("UID_WRITE_SECURE_SETTINGS=" + method.invoke(manager, "android.permission.WRITE_SECURE_SETTINGS", Process.myUid()));
                if (method.getName().equals("checkPermission") && Arrays.equals(parameters, new Class<?>[]{String.class, String.class, int.class}))
                    System.out.println("PACKAGE_WRITE_SECURE_SETTINGS=" + method.invoke(manager, "com.android.shell", "android.permission.WRITE_SECURE_SETTINGS", 0));
            }
        } catch (Exception failure) {
            System.out.println("PERMISSION_DIAGNOSTIC_UNAVAILABLE=" + failure);
        }
    }

    private static void readPolling(Class<?> adapterType, Object adapter, String mode) throws Exception {
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(Paths.get("/data/local/tmp/ink-shell-polling.lock"),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
             java.nio.channels.FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Another Shell polling session is active");
            pollingTransaction(adapterType, adapter, mode);
        }
    }

    private static void pollingTransaction(Class<?> adapterType, Object adapter, String mode) throws Exception {
        if (!android.os.Build.DEVICE.equals("pudding") || android.os.Build.VERSION.SDK_INT != 37)
            throw new IllegalStateException("ST read probe limited to verified pudding/API37 profile");
        Class<?> callbackType = Class.forName("android.nfc.INfcVendorNciCallback");
        Class<?> stub = Class.forName("android.nfc.INfcVendorNciCallback$Stub");
        java.lang.reflect.Field responseField = stub.getDeclaredField("TRANSACTION_onVendorResponseReceived");
        responseField.setAccessible(true);
        int responseCode = responseField.getInt(null);
        LinkedBlockingQueue<byte[]> replies = new LinkedBlockingQueue<>();
        AtomicInteger expected = new AtomicInteger(-1);
        Binder callbackBinder = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code != responseCode) return super.onTransact(code, data, reply, flags);
                data.enforceInterface("android.nfc.INfcVendorNciCallback");
                int gid = data.readInt();
                int oid = data.readInt();
                byte[] payload = data.createByteArray();
                if (gid == 15 && oid == 1 && payload != null && payload.length > 0 && (payload[0] & 255) == expected.get())
                    replies.offer(payload);
                if (reply != null) reply.writeNoException();
                return true;
            }
        };
        Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType}, (proxy, method, values) -> {
            if (method.getName().equals("asBinder")) return callbackBinder;
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == values[0];
            if (method.getName().equals("toString")) return "InkShellVendorCallback";
            return null;
        });
        adapterType.getMethod("registerVendorExtensionCallback", callbackType).invoke(adapter, callback);
        boolean paused = false;
        try {
            int pauseStatus = (Integer) adapterType.getMethod("pausePolling", long.class).invoke(adapter, 30000L);
            System.out.println("POLLING_PAUSE_STATUS=" + pauseStatus);
            if (pauseStatus != 1) throw new IllegalStateException("Polling pause not confirmed");
            paused = true;
            PollingConfigSession.Transport transport = new PollingConfigSession.Transport() {
                public byte[] read() throws Exception {
                    byte[] configuration = exchange(adapterType, adapter, replies, expected, 0x15, new byte[0]);
                    Protocol.validatePolling(configuration);
                    System.out.println("ST_POLLING_READ=" + Protocol.hex(configuration));
                    return configuration;
                }
                public void write(byte[] configuration) throws Exception {
                    Protocol.validatePolling(configuration);
                    exchange(adapterType, adapter, replies, expected, 0x13, configuration);
                }
            };
            if (mode.equals("read")) transport.read();
            else if (mode.equals("restore")) {
                PollingConfigSession.restore(transport, Paths.get("/data/local/tmp/ink-shell-polling.snapshot"));
                System.out.println("POLLING_ORIGINAL_RESTORED=true");
            }
            else {
                try (PollingConfigSession session = new PollingConfigSession(transport, Paths.get("/data/local/tmp/ink-shell-polling.snapshot"))) {
                    session.start();
                    System.out.println("SEVEN_BIT_CONFIGURATION_VERIFIED=true RF_NOT_INDEPENDENTLY_MEASURED=true");
                    if (mode.equals("probe")) {
                        try {
                            try (ShellTagReader reader = new ShellTagReader(adapterType, adapter)) {
                                reader.start();
                                System.out.println("POLLING_RESUME_STATUS=" + adapterType.getMethod("resumePolling").invoke(adapter));
                                paused = false;
                                reader.await();
                            }
                        } finally {
                            if (!paused) {
                                int status = (Integer) adapterType.getMethod("pausePolling", long.class).invoke(adapter, 30000L);
                                if (status != 1) throw new IllegalStateException("Pause before restore failed: " + status);
                                paused = true;
                            }
                        }
                    }
                }
                System.out.println("POLLING_ORIGINAL_RESTORED=true");
            }
        } finally {
            try {
                if (paused) System.out.println("POLLING_RESUME_STATUS=" + adapterType.getMethod("resumePolling").invoke(adapter));
            } finally {
                adapterType.getMethod("unregisterVendorExtensionCallback", callbackType).invoke(adapter, callback);
            }
        }
    }

    private static byte[] exchange(Class<?> adapterType, Object adapter, LinkedBlockingQueue<byte[]> replies,
                                   AtomicInteger expected, int command, byte[] payload) throws Exception {
        byte[] packet = new byte[payload.length + 1];
        packet[0] = (byte) command;
        System.arraycopy(payload, 0, packet, 1, payload.length);
        replies.clear();
        expected.set(command);
        try {
            int status = (Integer) adapterType.getMethod("sendVendorNciMessage", int.class, int.class, int.class, byte[].class)
                    .invoke(adapter, 1, 15, 1, packet);
            if (status != 0) throw new IllegalStateException("Vendor host status=" + status);
            byte[] response = replies.poll(3, TimeUnit.SECONDS);
            if (response == null) throw new IllegalStateException("Vendor response timeout");
            System.out.println("ST_RESPONSE=" + Protocol.hex(response));
            if (response.length < 2 || response[1] != 0)
                throw new IllegalStateException("ST command rejected: " + Protocol.hex(response));
            return Arrays.copyOfRange(response, 2, response.length);
        } finally {
            expected.set(-1);
        }
    }
}
