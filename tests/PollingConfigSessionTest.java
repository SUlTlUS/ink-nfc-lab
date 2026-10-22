package dev.inknfclab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class PollingConfigSessionTest {
    private static final byte[] ORIGINAL = {1, 0x2a, 2, 10, 0x7d};

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("ink-polling-test");
        Path snapshot = directory.resolve("snapshot");
        FakeTransport transport = new FakeTransport(snapshot);
        try (PollingConfigSession session = new PollingConfigSession(transport, snapshot)) {
            session.start();
            require(Arrays.equals(transport.current, Protocol.polling7A()));
            require(Arrays.equals(Files.readAllBytes(snapshot), ORIGINAL));
        }
        require(Arrays.equals(transport.current, ORIGINAL));
        require(!Files.exists(snapshot));

        transport.failFirstWrite = true;
        try (PollingConfigSession session = new PollingConfigSession(transport, snapshot)) {
            try { session.start(); throw new AssertionError("Expected failed write"); }
            catch (java.io.IOException expected) {}
        }
        require(Arrays.equals(transport.current, ORIGINAL));
        require(!Files.exists(snapshot));

        PollingConfigSession session = new PollingConfigSession(transport, snapshot);
        session.start();
        transport.corruptReads = true;
        try { session.close(); throw new AssertionError("Expected failed restore verification"); }
        catch (IllegalStateException expected) {}
        require(Files.exists(snapshot));
        transport.corruptReads = false;
        session.close();
        require(!Files.exists(snapshot));

        Files.write(snapshot, ORIGINAL);
        int writes = transport.writes;
        try (PollingConfigSession blocked = new PollingConfigSession(transport, snapshot)) {
            try { blocked.start(); throw new AssertionError("Expected existing snapshot rejection"); }
            catch (IllegalStateException expected) {}
        }
        require(transport.writes == writes);
        Files.delete(snapshot);
        Files.write(snapshot, ORIGINAL);
        transport.current = Protocol.polling7A();
        PollingConfigSession.restore(transport, snapshot);
        require(Arrays.equals(transport.current, ORIGINAL));
        require(!Files.exists(snapshot));
        Files.write(snapshot, new byte[]{1});
        writes = transport.writes;
        try { PollingConfigSession.restore(transport, snapshot); throw new AssertionError("Expected invalid snapshot rejection"); }
        catch (IllegalArgumentException expected) {}
        require(transport.writes == writes && Files.exists(snapshot));
        Files.delete(snapshot);
        Files.delete(directory);
        System.out.println("Shell polling transaction tests passed");
    }

    private static final class FakeTransport implements PollingConfigSession.Transport {
        final Path snapshot;
        byte[] current = ORIGINAL.clone();
        boolean failFirstWrite;
        boolean corruptReads;
        int writes;
        FakeTransport(Path snapshot) { this.snapshot = snapshot; }
        public byte[] read() { return corruptReads ? new byte[]{0} : current.clone(); }
        public void write(byte[] configuration) throws Exception {
            require(Files.exists(snapshot));
            writes++;
            current = configuration.clone();
            if (failFirstWrite) { failFirstWrite = false; throw new java.io.IOException("Response lost after write"); }
        }
    }

    private static void require(boolean condition) { if (!condition) throw new AssertionError(); }
}
