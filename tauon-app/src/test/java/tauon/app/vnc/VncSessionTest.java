package tauon.app.vnc;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import tauon.app.settings.VncConnection;
import tauon.app.ui.components.simpletable.ConnectionStatusValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

/**
 * Runs {@link VncSession} with a stand-in viewer: a shell script that records its arguments and what it reads from
 * standard input, then behaves as the test says (keeps running, exits, fails).
 */
public class VncSessionTest {

    private Path dir;
    private Path argsFile;
    private Path stdinFile;
    private Path childPidFile;
    private final List<FakeTunnel> tunnels = new CopyOnWriteArrayList<>();
    private final AtomicInteger statusChanges = new AtomicInteger();
    private VncConnection connection;
    private VncSession session;

    private static final class FakeTunnel implements VncSession.Tunnel {
        final String host;
        final int port;
        volatile boolean closed;

        FakeTunnel(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public int getLocalPort() {
            return 54321;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Before
    public void setUp() throws IOException {
        assumeFalse("the stand-in viewer is a shell script", System.getProperty("os.name").toLowerCase().contains("win"));
        dir = Files.createTempDirectory("vnc-test");
        argsFile = dir.resolve("args");
        stdinFile = dir.resolve("stdin");
        childPidFile = dir.resolve("child.pid");
        connection = new VncConnection();
        connection.setName("gpu");
        connection.setHost("gpu-host");
        connection.setPort(5901);
        connection.setPassword("s3cr3t");
    }

    @After
    public void tearDown() throws IOException {
        if (session != null) {
            session.close();
        }
        if (dir != null) {
            try (var files = Files.list(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    Files.deleteIfExists(p);
                }
            }
            Files.deleteIfExists(dir);
        }
    }

    /** A viewer that records its args and stdin, starts a child process, then runs {@code then}. */
    private Path viewer(String then) throws IOException {
        Path script = dir.resolve("vncviewer");
        Files.writeString(script, "#!/bin/sh\n"
                + "printf '%s\\n' \"$@\" > '" + argsFile + "'\n"
                + "IFS= read -r line; printf '%s' \"$line\" > '" + stdinFile + "'\n"
                + "sleep 300 & echo $! > '" + childPidFile + "'\n"
                + then + "\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    private VncSession session(Path viewer) {
        session = new VncSession(connection, (host, port) -> {
            FakeTunnel t = new FakeTunnel(host, port);
            tunnels.add(t);
            return t;
        }, () -> Optional.ofNullable(viewer));
        session.addListener(statusChanges::incrementAndGet);
        return session;
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("timed out waiting for: " + what);
            }
            Thread.sleep(20);
        }
    }

    private long childPid() throws Exception {
        await("child pid", () -> Files.exists(childPidFile) && sizeOf(childPidFile) > 0);
        return Long.parseLong(Files.readString(childPidFile).trim());
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    @Test
    public void startsTheViewerThroughATunnelWithThePasswordOnStdin() throws Exception {
        VncSession s = session(viewer("wait"));
        assertTrue(s.start());

        assertEquals(1, tunnels.size());
        assertEquals("gpu-host", tunnels.get(0).host);
        assertEquals(5901, tunnels.get(0).port);
        assertEquals(ConnectionStatusValue.RUNNING, s.getStatus());
        assertTrue(s.isRunning());
        assertEquals(54321, s.getLocalPort());

        await("viewer wrote its stdin", () -> Files.exists(stdinFile) && Files.exists(argsFile));
        assertEquals(List.of("-autopass", "localhost::54321"), Files.readAllLines(argsFile));
        assertEquals("s3cr3t", Files.readString(stdinFile));
        assertTrue(statusChanges.get() >= 2); // INITIATING, RUNNING
    }

    @Test
    public void startWhileRunningDoesNothing() throws Exception {
        VncSession s = session(viewer("wait"));
        assertTrue(s.start());
        assertFalse(s.start());
        assertEquals(1, tunnels.size());
    }

    @Test
    public void viewerExitClosesTheTunnelAtOnce() throws Exception {
        VncSession s = session(viewer("kill $!; exit 0"));
        s.start();
        await("viewer exited", () -> !s.isRunning() && s.getStatus() == ConnectionStatusValue.STOPPED);
        assertTrue("tunnel closed with the viewer", tunnels.get(0).closed);
        assertEquals(-1, s.getLocalPort());
        assertNull(s.getLastError());
    }

    @Test
    public void viewerFailureIsAnErrorWithItsOutput() throws Exception {
        VncSession s = session(viewer("kill $!; echo 'Authentication failed' >&2; exit 3"));
        s.start();
        await("viewer failed", () -> s.getStatus() == ConnectionStatusValue.ERROR);
        assertTrue(tunnels.get(0).closed);
        assertTrue(s.getLastError(), s.getLastError().contains("code 3"));
        assertTrue(s.getLastError(), s.getLastError().contains("Authentication failed"));

        // It can be opened again
        Files.deleteIfExists(childPidFile);
        assertTrue(s.start());
        assertEquals(2, tunnels.size());
    }

    @Test
    public void closeKillsTheViewerAndItsChildren() throws Exception {
        VncSession s = session(viewer("wait"));
        s.start();
        long child = childPid();
        assertTrue(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false));

        s.close();
        assertFalse(s.isRunning());
        assertTrue(tunnels.get(0).closed);
        await("child killed", () -> !ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false));
        assertEquals(ConnectionStatusValue.STOPPED, s.getStatus());
        assertFalse(VncSession.live().contains(s));
        try {
            s.start();
            fail("a closed session can't start again");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void missingViewerOpensNothing() throws Exception {
        VncSession s = session(null);
        try {
            s.start();
            fail();
        } catch (VncSession.ViewerNotFoundException expected) {
        }
        assertTrue("no tunnel when the viewer is missing", tunnels.isEmpty());
        assertEquals(ConnectionStatusValue.ERROR, s.getStatus());
    }

    @Test
    public void tunnelFailureStartsNoViewer() throws Exception {
        Path viewer = viewer("wait");
        session = new VncSession(connection, (h, p) -> {
            throw new IOException("Session closed");
        }, () -> Optional.of(viewer));
        try {
            session.start();
            fail();
        } catch (IOException expected) {
        }
        assertEquals(ConnectionStatusValue.ERROR, session.getStatus());
        assertFalse(Files.exists(argsFile));
    }

    @Test
    public void noPasswordSendsAnEmptyLine() throws Exception {
        connection.setPassword(null);
        VncSession s = session(viewer("wait"));
        s.start();
        await("viewer read stdin", () -> Files.exists(stdinFile));
        assertEquals("", Files.readString(stdinFile));
    }

    @Test
    public void commandUsesAutopassAndAPortNotADisplay() {
        List<String> cmd = new ArrayList<>(VncSession.command(Path.of("/opt/TurboVNC/bin/vncviewer"), 40001));
        assertEquals(List.of("/opt/TurboVNC/bin/vncviewer", "-autopass", "localhost::40001"), cmd);
    }
}
