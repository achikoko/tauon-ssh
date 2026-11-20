package tauon.app.vnc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tauon.app.settings.VncConnection;
import tauon.app.ui.components.simpletable.ConnectionStatusValue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * One VNC connection of a session, opened with the external TurboVNC viewer through an SSH tunnel.
 * <p>
 * {@link #start()} opens a local port forward to the VNC server, runs {@code vncviewer -autopass localhost::PORT} and
 * writes the password to its standard input. The viewer process is monitored: as soon as it ends (closed by the user,
 * failed, killed) the tunnel is closed. {@link #close()} kills the viewer (and its child processes) and the tunnel.
 * <p>
 * Status: {@link ConnectionStatusValue#STOPPED} (not running), {@link ConnectionStatusValue#INITIATING},
 * {@link ConnectionStatusValue#RUNNING} (viewer running) or {@link ConnectionStatusValue#ERROR} (could not start, or
 * the viewer exited with an error; see {@link #getLastError()}). Listeners are called on the thread making the change.
 */
public class VncSession {

    private static final Logger LOG = LoggerFactory.getLogger(VncSession.class);
    private static final int OUTPUT_LINES_KEPT = 10;
    private static final long KILL_GRACE_MILLIS = 3000;

    /** Viewers still running when Tauon exits are killed too. */
    private static final Set<VncSession> LIVE = ConcurrentHashMap.newKeySet();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> new ArrayList<>(LIVE).forEach(VncSession::close),
                "vnc-viewers-shutdown"));
    }

    /** A local port forward to the VNC server. */
    public interface Tunnel {
        int getLocalPort();

        void close();
    }

    @FunctionalInterface
    public interface TunnelOpener {
        Tunnel open(String remoteHost, int remotePort) throws Exception;
    }

    /** The TurboVNC viewer is not installed (or the configured path is wrong). */
    public static class ViewerNotFoundException extends Exception {
        public ViewerNotFoundException() {
            super("TurboVNC viewer not found");
        }
    }

    private final VncConnection connection;
    private final TunnelOpener tunnelOpener;
    private final Supplier<Optional<Path>> viewerLocator;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Deque<String> lastOutput = new ArrayDeque<>();

    private volatile ConnectionStatusValue status = ConnectionStatusValue.STOPPED;
    private volatile String lastError;
    private Process process;
    private Tunnel tunnel;
    private boolean killing;
    private boolean closed;

    public VncSession(VncConnection connection, TunnelOpener tunnelOpener, Supplier<Optional<Path>> viewerLocator) {
        this.connection = connection;
        this.tunnelOpener = tunnelOpener;
        this.viewerLocator = viewerLocator;
    }

    public VncConnection getConnection() {
        return connection;
    }

    public ConnectionStatusValue getStatus() {
        return status;
    }

    /** @return why it is in {@link ConnectionStatusValue#ERROR}, or null */
    public String getLastError() {
        return lastError;
    }

    /** @return the local port of the tunnel while the viewer runs, or -1 */
    public synchronized int getLocalPort() {
        return tunnel != null ? tunnel.getLocalPort() : -1;
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /**
     * Opens the tunnel and the viewer. Does nothing if it is already starting or running. Blocks while the tunnel opens
     * (it may connect the session): don't call it on the event dispatch thread.
     *
     * @return false if it was already starting or running
     * @throws ViewerNotFoundException if TurboVNC is not installed; nothing is opened
     * @throws Exception               if the tunnel or the viewer could not be started; the tunnel is closed again
     */
    public boolean start() throws Exception {
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("Session closed");
            }
            if (status == ConnectionStatusValue.INITIATING || isRunning()) {
                return false;
            }
            setStatus(ConnectionStatusValue.INITIATING, null);
        }

        Tunnel openedTunnel = null;
        Process started = null;
        try {
            Path viewer = viewerLocator.get().orElseThrow(ViewerNotFoundException::new);
            openedTunnel = tunnelOpener.open(connection.getHost(), connection.getPort());

            ProcessBuilder pb = new ProcessBuilder(command(viewer, openedTunnel.getLocalPort()));
            pb.redirectErrorStream(true);
            LOG.info("Starting VNC viewer for {}: {}", connection.getDisplayName(), pb.command());
            started = pb.start();
            writePassword(started);
            drainOutput(started);

            synchronized (this) {
                if (closed) {
                    throw new IllegalStateException("Session closed while starting");
                }
                process = started;
                tunnel = openedTunnel;
                killing = false;
                LIVE.add(this);
                setStatus(ConnectionStatusValue.RUNNING, null);
            }
            Process monitored = started;
            monitored.onExit().thenAccept(p -> onExit(monitored));
            return true;
        } catch (Exception e) {
            if (started != null) {
                destroyTree(started);
            }
            if (openedTunnel != null) {
                openedTunnel.close();
            }
            setStatus(ConnectionStatusValue.ERROR, e instanceof ViewerNotFoundException ? e.getMessage() : String.valueOf(e.getMessage()));
            throw e;
        }
    }

    /** Kills the viewer (and the processes it started) and closes the tunnel. */
    public void kill() {
        Process p;
        synchronized (this) {
            p = process;
            killing = true;
        }
        if (p != null) {
            destroyTree(p);
        }
        closeTunnel();
    }

    /** Kills the viewer; it can't be started again. Called when the SSH session closes. */
    public void close() {
        synchronized (this) {
            closed = true;
        }
        kill();
        LIVE.remove(this);
    }

    static List<String> command(Path viewer, int localPort) {
        List<String> cmd = new ArrayList<>();
        cmd.add(viewer.toString());
        cmd.add("-autopass"); // read the password from stdin instead of prompting
        cmd.add("localhost::" + localPort); // '::' means a port, not a display
        return cmd;
    }

    private void writePassword(Process p) throws Exception {
        try (OutputStream in = p.getOutputStream()) {
            String password = connection.getPassword();
            byte[] bytes = ((password != null ? password : "") + "\n").getBytes(StandardCharsets.UTF_8);
            in.write(bytes);
            in.flush();
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    /** Reads the viewer's output (so it never blocks on a full pipe), keeping the last lines for error messages. */
    private void drainOutput(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    LOG.debug("[vncviewer {}] {}", connection.getDisplayName(), line);
                    synchronized (lastOutput) {
                        lastOutput.addLast(line);
                        if (lastOutput.size() > OUTPUT_LINES_KEPT) {
                            lastOutput.removeFirst();
                        }
                    }
                }
            } catch (Exception e) {
                LOG.trace("Viewer output closed", e);
            }
        }, "vncviewer-output-" + connection.getDisplayName());
        t.setDaemon(true);
        t.start();
    }

    private void onExit(Process p) {
        // First of all: the tunnel must not outlive the viewer.
        boolean wasKilled;
        synchronized (this) {
            if (p != process) {
                return;
            }
            process = null;
            wasKilled = killing;
        }
        closeTunnel();
        LIVE.remove(this);

        int code = p.exitValue();
        LOG.info("VNC viewer for {} exited with code {}", connection.getDisplayName(), code);
        if (wasKilled || code == 0) {
            setStatus(ConnectionStatusValue.STOPPED, null);
        } else {
            String output;
            synchronized (lastOutput) {
                output = String.join("\n", lastOutput);
            }
            setStatus(ConnectionStatusValue.ERROR, "Viewer exited with code " + code + (output.isEmpty() ? "" : ":\n" + output));
        }
    }

    private void closeTunnel() {
        Tunnel t;
        synchronized (this) {
            t = tunnel;
            tunnel = null;
        }
        if (t != null) {
            t.close();
        }
    }

    private static void destroyTree(Process p) {
        List<ProcessHandle> descendants = new ArrayList<>();
        p.descendants().forEach(descendants::add);
        descendants.forEach(ProcessHandle::destroy);
        p.destroy();
        try {
            if (!p.waitFor(KILL_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }

    private void setStatus(ConnectionStatusValue status, String error) {
        this.status = status;
        this.lastError = error;
        for (Runnable l : listeners) {
            try {
                l.run();
            } catch (Exception e) {
                LOG.error("VNC status listener failed", e);
            }
        }
    }

    /** For tests and diagnostics. */
    static Set<VncSession> live() {
        return Collections.unmodifiableSet(LIVE);
    }
}
