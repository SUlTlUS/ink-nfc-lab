package dev.inknfclab;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.EnumSet;

public final class PollingConfigSession implements AutoCloseable {
    public interface Transport {
        byte[] read() throws Exception;
        void write(byte[] configuration) throws Exception;
    }

    private final Transport transport;
    private final Path snapshot;
    private byte[] original;
    private boolean restoreRequired;

    public PollingConfigSession(Transport transport, Path snapshot) {
        this.transport = transport;
        this.snapshot = snapshot;
    }

    public void start() throws Exception {
        if (restoreRequired || original != null) throw new IllegalStateException("Session already started");
        if (Files.exists(snapshot)) throw new IllegalStateException("Unrestored snapshot exists: " + snapshot);
        original = transport.read().clone();
        Protocol.validatePolling(original);
        try (FileChannel file = FileChannel.open(snapshot,
                EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer bytes = ByteBuffer.wrap(original);
            while (bytes.hasRemaining()) file.write(bytes);
            file.force(true);
        }
        restoreRequired = true;
        byte[] target = Protocol.polling7A();
        transport.write(target);
        if (!Arrays.equals(target, transport.read())) throw new IllegalStateException("7A read-back mismatch");
    }

    @Override public void close() throws Exception {
        if (!restoreRequired) return;
        transport.write(original.clone());
        if (!Arrays.equals(original, transport.read())) throw new IllegalStateException("Restore read-back mismatch; snapshot retained");
        Files.delete(snapshot);
        restoreRequired = false;
    }

    public static void restore(Transport transport, Path snapshot) throws Exception {
        byte[] configuration = Files.readAllBytes(snapshot);
        Protocol.validatePolling(configuration);
        transport.write(configuration);
        if (!Arrays.equals(configuration, transport.read())) throw new IllegalStateException("Recovery read-back mismatch; snapshot retained");
        Files.delete(snapshot);
    }
}
