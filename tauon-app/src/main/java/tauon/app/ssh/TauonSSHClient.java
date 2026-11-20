package tauon.app.ssh;

import net.schmizz.keepalive.KeepAliveProvider;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.DisconnectReason;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Parameters;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive;
import net.schmizz.sshj.userauth.method.AuthNone;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tauon.app.exceptions.OperationCancelledException;
import tauon.app.exceptions.RemoteOperationException;
import tauon.app.exceptions.SessionClosedException;
import tauon.app.services.SettingsConfigManager;
import tauon.app.services.SitesConfigManager;
import tauon.app.settings.HopEntry;
import tauon.app.settings.PortForwardingRule;
import tauon.app.settings.SiteInfo;
import tauon.app.ui.components.simpletable.ConnectionStatusValue;
import tauon.app.ui.containers.main.GraphicalHostKeyVerifier;
import tauon.app.util.misc.ExceptionUtils;
import tauon.app.util.misc.PlatformUtils;
import tauon.app.util.ssh.SshUtil;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TauonSSHClient {
    
    private static class UserPassCache{
        public char[] password;
        String user;
    }
    
    public class PortForwardingState{
        private final PortForwardingRule rule;
        private ServerSocket serverSocket;
        private Thread thread;
        private ConnectionStatusValue status = ConnectionStatusValue.DISABLED;
        
        private PortForwardingState(PortForwardingRule rule) {
            this.rule = rule;
        }
        
        public PortForwardingRule getRule() {
            return rule;
        }
        
        private void start() throws IOException {
            if(status == ConnectionStatusValue.ESTABLISHED)
                return;
            
            if (rule.getType() == PortForwardingRule.PortForwardingType.Local) {
                try {
                    serverSocket = new ServerSocket();
                    serverSocket.setReuseAddress(true);
                    serverSocket.bind(new InetSocketAddress(rule.getLocalHost(), rule.getLocalPort()));
                    
                    SSHClient ssh = sshConnectedHop.sshj;
                    thread = new Thread(() -> {
                        try {
                            ssh.newLocalPortForwarder(
                                            new Parameters(rule.getLocalHost(), rule.getLocalPort(), rule.getRemoteHost(), rule.getRemotePort()), serverSocket)
                                    .listen();
                            setStatus(ConnectionStatusValue.STOPPED);
                        } catch (IOException e) {
                            thread = null;
                            setStatus(ConnectionStatusValue.ERROR);
                            guiHandle.reportPortForwardingFailed(rule, e);
                        } finally {
                            thread = null;
                        }
                    });
                    
                    thread.start();
                    
                    setStatus(ConnectionStatusValue.ESTABLISHED);
                }  catch (ConnectionException | TransportException | BindException e) {
                    setStatus(ConnectionStatusValue.ERROR);
                    guiHandle.reportPortForwardingFailed(rule, e);
                }
                
            }else{
                
                SSHClient ssh = sshConnectedHop.sshj;
                
                /*
                 * We make _server_ listen on port 8080, which forwards all connections to us as
                 * a channel, and we further forward all such channels to google.com:80
                 */
                try {
                    ssh.getRemotePortForwarder().bind(
                            // where the server should listen
                            new RemotePortForwarder.Forward(rule.getRemoteHost(), rule.getRemotePort()),
                            // what we do with incoming connections that are forwarded to us
                            new SocketForwardingConnectListener(new InetSocketAddress(rule.getLocalHost(), rule.getLocalPort())));
                    setStatus(ConnectionStatusValue.ESTABLISHED);
                } catch (ConnectionException | TransportException e) {
                    setStatus(ConnectionStatusValue.ERROR);
                    guiHandle.reportPortForwardingFailed(rule, e);
                }
                
            }
            
        }
        
        public void setStatus(ConnectionStatusValue status) {
            this.status = status;
            // TODO issue an event
        }
        
        public ConnectionStatusValue getStatus() {
            return status;
        }
    }
    
    /**
     * A local port forward (-L) opened on demand, on a random free port of 127.0.0.1. Closing it stops listening at
     * once; connections already forwarded end with their client.
     */
    public class LocalTunnel implements AutoCloseable {
        private final String remoteHost;
        private final int remotePort;
        private final ServerSocket serverSocket;
        private final Thread thread;

        private LocalTunnel(SSHClient ssh, String remoteHost, int remotePort) throws IOException {
            this.remoteHost = remoteHost;
            this.remotePort = remotePort;
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
            int localPort = serverSocket.getLocalPort();
            thread = new Thread(() -> {
                try {
                    ssh.newLocalPortForwarder(new Parameters("127.0.0.1", localPort, remoteHost, remotePort), serverSocket)
                            .listen();
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        LOG.warn("Tunnel 127.0.0.1:{} -> {}:{} failed", localPort, remoteHost, remotePort, e);
                    }
                } finally {
                    closeQuietly();
                }
            }, "tunnel-" + localPort + "-" + remoteHost + ":" + remotePort);
            thread.setDaemon(true);
            thread.start();
        }

        public int getLocalPort() {
            return serverSocket.getLocalPort();
        }

        public boolean isOpen() {
            return !serverSocket.isClosed();
        }

        @Override
        public void close() {
            closeQuietly();
            thread.interrupt();
        }

        private void closeQuietly() {
            synchronized (localTunnels) {
                localTunnels.remove(this);
            }
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.debug("Closing tunnel to {}:{}", remoteHost, remotePort, e);
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(TauonSSHClient.class);
    
    private final SiteInfo info;
    private final GuiHandle guiHandle;
    private final PasswordFinder passwordFinder;
    private final ExecutorService executor;
    private final boolean openPortForwarding;
    
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    private SSHConnectedHop sshConnectedHop;
    private SFTPClient sftp;
    
    private final GraphicalHostKeyVerifier hostKeyVerifier;
    
    private final List<UserPassCache> cache = new ArrayList<>();
    private final List<PortForwardingState> portForwardingStates = new ArrayList<>();
    private final List<LocalTunnel> localTunnels = new ArrayList<>();
    
    public TauonSSHClient(
            SiteInfo info, GuiHandle guiHandle, PasswordFinder passwordFinder, ExecutorService executor, boolean openPortForwarding, GraphicalHostKeyVerifier hostKeyVerifier
    ) {
        this.hostKeyVerifier = hostKeyVerifier;
        this.info = info;
        this.guiHandle = guiHandle;
        this.passwordFinder = passwordFinder;
        this.executor = executor;
        this.openPortForwarding = openPortForwarding;
    }
    
    public List<PortForwardingState> getPortsForwarding() {
        return Collections.unmodifiableList(portForwardingStates);
    }
    
    public synchronized boolean connect(boolean force) throws InterruptedException {
        
        // stills saying connected
        if(!force && isConnected()) {
            LOG.warn("Client is already connected.");
            return true;
        }
        
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Future<Boolean>> future = new AtomicReference<>();
        
        GuiHandle.BlockHandle blockHandle = guiHandle.blockUi(this, blockHandle2 -> {
            if(!cancelled.getAndSet(true)){
                Future<Boolean> f = future.get();
                if(f != null)
                    f.cancel(true);
            }
        });
        
        future.set(executor.submit(() -> {
            if(cancelled.get())
                return false;
            
            LOG.info("Disconnect previous connection if exists.");
            disconnect();
            
            LOG.info("Begin connecting client.");
            
            try {
                sshConnectedHop = new SSHConnectedHop(info);
                sshConnectedHop.connect(0);
                sshConnectedHop.sshj.getTransport().setDisconnectListener((disconnectReason, s) -> {
                    LOG.info("SSH called disconnect listener with reason: {}", disconnectReason);
                    
                    // Skip programmatically disconnections
                    if(disconnectReason == DisconnectReason.BY_APPLICATION)
                        return;
                    
                    try {
                        disconnect();
                    } catch (IOException | InterruptedException e) {
                        LOG.error("Exception while disconnecting after notified.", e);
                    }
                });
                
                for (PortForwardingRule r : info.getPortForwardingRules()) {
                    PortForwardingState portForwardingState = new PortForwardingState(r);
                    portForwardingStates.add(portForwardingState);
                    
                    if(openPortForwarding && r.isEnabled()) {
                        try {
                            portForwardingState.start();
                        } catch (Exception e) {
                            LOG.error("Port forwarding failed: {}", r, e);
                        }
                    }
                    
                }
                
                return true;
            }catch (Exception e){
                LOG.error("Connection failed.", e);
                TauonSSHClient.this.disconnect();
                return false;
            }
        }));
        
        try {
            return future.get().get();
        }catch (InterruptedException e){
            future.get().cancel(true);
            try {
                // Wait until is cancelled
                future.get().get();
            }catch (Exception ignored){
            
            }
            
            Thread.currentThread().interrupt();
            throw e;
        }catch (ExecutionException e){
            LOG.error("Execution exception while connecting.", e);
            guiHandle.reportException(e.getCause());
            return false;
        }finally {
            blockHandle.unblock();
        }
        
    }
    
    private void disconnectPortForwardingStates() throws IOException, InterruptedException {
        
        try {
            // First, close all server sockets. Threads must unlock listen() here.
            for (PortForwardingState pf : portForwardingStates) {
                Thread pfThread = pf.thread;
                if(pfThread != null) {
                    if (!pfThread.isAlive())
                        continue;
                    if (pf.serverSocket != null) {
                        pf.serverSocket.close();
                    }
                    pfThread.interrupt();
                }
            }
            
            // Second, join for them
            for (PortForwardingState pf : portForwardingStates) {
                Thread pfThread = pf.thread;
                if(pfThread != null) {
                    if (!pfThread.isAlive())
                        continue;
                    pfThread.join(10000); // Wait at most 10 seconds
                    if (pfThread.isAlive())
                        LOG.error("Thread {} was not interrupted properly.", pfThread.getName());
                }
            }
            
        }finally {
            portForwardingStates.clear();
        }
    }
    
    /**
     * Opens a local port forward to {@code remoteHost:remotePort} (as seen from the SSH server) on a random free port of
     * 127.0.0.1. It is closed by {@link LocalTunnel#close()} or when this client disconnects.
     */
    public synchronized LocalTunnel openLocalTunnel(String remoteHost, int remotePort) throws IOException, SessionClosedException {
        if (closed.get() || !isConnected()) {
            throw new SessionClosedException();
        }
        LocalTunnel tunnel = new LocalTunnel(sshConnectedHop.sshj, remoteHost, remotePort);
        synchronized (localTunnels) {
            localTunnels.add(tunnel);
        }
        return tunnel;
    }

    private void closeLocalTunnels() {
        List<LocalTunnel> open;
        synchronized (localTunnels) {
            open = new ArrayList<>(localTunnels);
        }
        open.forEach(LocalTunnel::close);
    }

    public synchronized boolean isConnected(){
        return sshConnectedHop != null && sshConnectedHop.sshj.isConnected();
    }
    
    public synchronized void close() throws IOException, InterruptedException {
        
        if(closed.getAndSet(true))
            return;
        
        disconnect();
    }
    
    private void disconnect() throws IOException, InterruptedException {
        
        closeLocalTunnels();
        disconnectPortForwardingStates();
        if(sshConnectedHop != null) {
            sshConnectedHop.disconnect();
            sshConnectedHop = null;
            sftp = null;
        }
        
    }
    
    public synchronized SFTPClient getSftpClient() throws RemoteOperationException, SessionClosedException {
        if (closed.get()) {
            throw new SessionClosedException();
        }
        
        if(!isConnected()){
            throw new RemoteOperationException.NotConnected();
        }
        
        if(sftp == null){
            try {
                sftp = sshConnectedHop.sshj.newSFTPClient();
            } catch (IOException e) {
                throw new RemoteOperationException.RealIOException(e);
            }
            this.sftp.getSFTPEngine().setTimeoutMs(sshConnectedHop.sshj.getTimeout());
            this.sftp.getSFTPEngine().getSubsystem().setAutoExpand(true);
        }
        
        return sftp;
    }
    
    public synchronized SFTPClient newSftpClient() throws RemoteOperationException, SessionClosedException {
        if (closed.get()) {
            throw new SessionClosedException();
        }
        
        if(!isConnected()){
            throw new RemoteOperationException.NotConnected();
        }
        
        try {
            SFTPClient sftp = sshConnectedHop.sshj.newSFTPClient();
            sftp.getSFTPEngine().setTimeoutMs(sshConnectedHop.sshj.getTimeout());
            sftp.getSFTPEngine().getSubsystem().setAutoExpand(true);
            return sftp;
        } catch (IOException e) {
            throw new RemoteOperationException.RealIOException(e);
        }
        
    }
    
    public synchronized Session openSession() throws RemoteOperationException, SessionClosedException {
        if (closed.get()) {
            throw new SessionClosedException();
        }
        
        if(!isConnected()){
            throw new RemoteOperationException.NotConnected();
        }
        
        try {
            
            Session session = sshConnectedHop.sshj.startSession();
            
            // TODO Look if XMING for windows works out-of-the-box, it's not bad to require it
            if(info.isXForwardingEnabled()) {
                
                Random r = new Random();
                StringBuilder faker = new StringBuilder();
                for (int i = 0; i < 32; i++) {
                    int n = r.nextInt(16);
                    if(n < 10)
                        faker.append(n);
                    else
                        faker.append((char)('a' + (char)(n-10)));
                }
                
                session.reqX11Forwarding("MIT-MAGIC-COOKIE-1", faker.toString(), 0);
                
            }
            return session;
            
        } catch (ConnectionException e) {
            throw new RemoteOperationException.NotConnected(e); // TODO throw timeout
        }catch (TransportException e) {
            LOG.error("Exception while starting session.", e);
            throw new RemoteOperationException.RealIOException(e);
        }
    }
    
    private class SSHConnectedHop {
        
        private SSHConnectedHop previousHop;
        private SSHClient sshj;
        
        // For connecting through tcp forwarding
        private ServerSocket serverSocket;
        private Thread thread;
        
        private final HopEntry hopEntry;
        
        public SSHConnectedHop(HopEntry hopEntry){
            this.hopEntry = hopEntry;
        }
        
        private void connect(int index) throws IOException, OperationCancelledException, InterruptedException {
        
//            try {
                
                ///////
                // CONNECT
                ///////
                
                DefaultConfig defaultConfig = new DefaultConfig();
                if (SettingsConfigManager.getSettings().isConnectionKeepAlive()) {
                    System.out.println("enabled KeepAliveProvider");
                    defaultConfig.setKeepAliveProvider(KeepAliveProvider.KEEP_ALIVE);
                }
                this.sshj = new SSHClient(defaultConfig);
                this.sshj.getConnection().setTimeoutMs(10000); // TODO parametrize
                if (SettingsConfigManager.getSettings().isConnectionKeepAlive()) {
                    this.sshj.getConnection().getKeepAlive().setKeepAliveInterval(5); // TODO parametrize
                }
                
                if(info.isXForwardingEnabled()){
                    
                    if (PlatformUtils.IS_LINUX) {
                        sshj.registerX11Forwarder(new tauon.app.ssh.SocketForwardingConnectListener(
                                SshUtil.socketAddress("/tmp/.X11-unix/X0")
                        ));
                    } else if (PlatformUtils.IS_MAC) {
                        sshj.registerX11Forwarder(new tauon.app.ssh.SocketForwardingConnectListener(
                                SshUtil.socketAddress("/private/tmp/com.apple.launchd.ezzemjFmFP/org.xquartz:0")
                        ));
                    } else {
                        
                        sshj.registerX11Forwarder(new net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener(
//                        UnixDomainSocketAddress.of("/tmp/.X11-unix/X0")
                                new InetSocketAddress("localhost", 6000)
                        ));
                        
                    }
                    
                }
                
                this.sshj.setConnectTimeout(SettingsConfigManager.getSettings().getConnectionTimeout() * 1000);
                this.sshj.setTimeout(SettingsConfigManager.getSettings().getConnectionTimeout() * 1000);
                if (info.getJumpHosts().isEmpty() || index >= info.getJumpHosts().size()) {
                    this.setupProxyAndSocketFactory();
                    this.sshj.addHostKeyVerifier(hostKeyVerifier);
                    this.sshj.connect(hopEntry.getHost(), hopEntry.getPort());
                } else {
                
//                    try {
                        LOG.debug("Tunneling through...");
                        tunnelThrough(index);
                        LOG.debug("adding host key verifier");
                        this.sshj.addHostKeyVerifier(hostKeyVerifier);
                        LOG.debug("Host key verifier added");
                        if (info.getJumpType() == SiteInfo.JumpType.TcpForwarding) {
                            LOG.debug("tcp forwarding...");
                            this.connectViaTcpForwarding();
                        } else {
                            LOG.debug("port forwarding...");
                            this.connectViaPortForwarding();
                        }
//                    } catch (Exception e) {
//                        LOG.error("Exception while connecting multihop", e);
//                        disconnect(); // Will be disconnected by TauonSSHClient
//                        throw e;
//                    }
                }
                
                sshj.getConnection().getKeepAlive().setKeepAliveInterval(5);
                
                if(Thread.interrupted()){
                    Thread.currentThread().interrupt();
                    throw new InterruptedException(); // Will be disconnected by TauonSSHClient
                }
                
                ///////
                // AUTHORIZE
                ///////
                
                // Connection established, now find out supported authentication
                // methods
                List<String> allowedMethodsIfNotAuthenticated = new ArrayList<>();
                
                AtomicBoolean rememberUser = new AtomicBoolean();
                boolean isUserRemembered = false;
                
                UserPassCache userPassCache;
                while (cache.size() <= index)
                    cache.add(new UserPassCache());
                userPassCache = cache.get(index);
                
                if(userPassCache.user == null || userPassCache.user.isBlank()) {
                    
                    if(hopEntry.getUser() != null && !hopEntry.getUser().isBlank()){
                        userPassCache.user = hopEntry.getUser();
                        isUserRemembered = true;
                    }else{
                        userPassCache.user = guiHandle.promptUser(hopEntry, rememberUser);
                    }
                }else{
                    isUserRemembered = true;
                }
                
                if (userPassCache.user == null || userPassCache.user.isEmpty()) {
                    throw new OperationCancelledException();
                }
                
                boolean authenticated = this.getAuthMethods(allowedMethodsIfNotAuthenticated, userPassCache.user);
                if (authenticated) {
                    return; // All right
                }
                
                // loop over servers preferred authentication methods in the same
                // order sent by server
                for (String authMethod : allowedMethodsIfNotAuthenticated) {
                    
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException(); // Will be disconnected by TauonSSHClient
                    }
                    
                    LOG.debug("Trying auth method: {}", authMethod);
                    
                    switch (authMethod) {
                        case "publickey":
                            try {
                                this.authPublicKey(index, userPassCache.user);
                                if(rememberUser.get()){
                                    info.setUser(userPassCache.user);
                                    SitesConfigManager.getInstance().setPasswordsFrom(info);
                                }
                                return; // All right
                            } catch (OperationCancelledException e) {
                                TauonSSHClient.this.disconnect();
                                throw e;
                            } catch (Exception e) {
                                LOG.debug("Error authenticating", e);
                            }
                            break;
                        
                        case "keyboard-interactive":
                            try {
                                sshj.auth(userPassCache.user, new AuthKeyboardInteractive(new SSHJInteractiveResponseProvider(guiHandle)));
                                return; // All right
                            } catch (Exception e) {
                                LOG.debug("Error authenticating", e);
                            }
                            break;
                        
                        case "password":
                            try {
                                this.authPassword(index, userPassCache.user, isUserRemembered, rememberUser.get());
                                return; // All right
                            } catch (OperationCancelledException e) {
                                TauonSSHClient.this.disconnect();
                                throw e;
                            } catch (Exception e) {
                                LOG.debug("Error authenticating", e);
                            }
                            break;
                    }
                    
                }
                
                throw new IOException("Authentication failed");
                
//            } catch (Exception e) {
//                throw ExceptionUtils.sneakyThrow(e);
//            }
        
        }
        
        private char[] promptPassword(String user, int index, AtomicBoolean rememberPassword, boolean isRetrying) throws OperationCancelledException {
            
            UserPassCache userPassCache;
            
            while (cache.size() <= index)
                cache.add(new UserPassCache());
            
            userPassCache = cache.get(index);
            
            if(isRetrying)
                userPassCache.password = null;
            
            if(userPassCache.password == null || userPassCache.password.length == 0) {
                
                if(hopEntry.getPassword() != null && !hopEntry.getPassword().isBlank()){
                    userPassCache.password = hopEntry.getPassword().toCharArray();
                }else {
                    char[] password = guiHandle.promptPassword(hopEntry, user, rememberPassword, isRetrying);
                    userPassCache.password = password;
                    return password;
                }
            }
            
            return userPassCache.password;
            
        }
        
        private boolean getAuthMethods(List<String> allowedMethodsIfNotAuthenticated, String user) {
            System.out.println("Trying to get allowed authentication methods...");
            try {
                sshj.auth(user, new AuthNone());
                return true;
            } catch (Exception e) {
                allowedMethodsIfNotAuthenticated.addAll(sshj.getUserAuth().getAllowedMethods());
                LOG.debug("List of allowed authentications: {}", allowedMethodsIfNotAuthenticated);
            }
            return false;
        }
        
        private void authPublicKey(int ignoredIndex, String user) throws Exception {
            KeyProvider provider = null;
            if (info.getPrivateKeyFile() != null && !info.getPrivateKeyFile().isEmpty()) {
                File keyFile = new File(info.getPrivateKeyFile());
                if (keyFile.exists()) {
                    provider = sshj.loadKeys(info.getPrivateKeyFile(), passwordFinder);
                    LOG.debug("Key provider: {}", provider);
                    LOG.debug("Key type: {}", provider.getType());
                }
            }
            
            if (provider == null) {
                throw new Exception("No suitable key providers");
            }
            
            sshj.authPublickey(user, provider);
        }
        
        private void authPassword(int index, String user, boolean isUserRemembered, boolean haveToRememberUser) throws OperationCancelledException {
            
            AtomicBoolean rememberPassword = isUserRemembered || haveToRememberUser ? new AtomicBoolean() : null;
            
            // keep on trying with password
            boolean isRetrying = false;
            while (true) {
                
                char[] password = promptPassword(user, index, rememberPassword, isRetrying);
                
                if (password == null || password.length == 0) {
                    throw new OperationCancelledException();
                }
                
                isRetrying = true;
                
                try {
                    String svalue = String.valueOf(password);
                    sshj.authPassword(user, password); // provide
                    // password
                    // updater
                    // PasswordUpdateProvider
                    // net.schmizz.sshj.userauth.password.PasswordUpdateProvider
                    if (haveToRememberUser || rememberPassword != null && rememberPassword.get()) {
                        if(haveToRememberUser)
                            info.setUser(user);
                        if(rememberPassword.get())
                            info.setPassword(svalue);
                        guiHandle.saveInfo(info);
                    }
                    
                    return;
                } catch (Exception e) {
                    LOG.debug("Error authenticating", e);
                }
            }
        }
        
        // recursively
        private void tunnelThrough(int index) throws OperationCancelledException, IOException, InterruptedException {
            HopEntry ent = info.getJumpHosts().get(index);
            previousHop = new SSHConnectedHop(ent);
            previousHop.connect(index+1);
        }
        
        private void connectViaTcpForwarding() throws IOException {
            this.sshj.connectVia(
                    this.previousHop.sshj.newDirectConnection(hopEntry.getHost(), hopEntry.getPort()),
                    hopEntry.getHost(),
                    hopEntry.getPort()
            );
        }
        
        private void connectViaPortForwarding() throws IOException, InterruptedException {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = serverSocket.getLocalPort();
            
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            thread = new Thread(() -> {
                try {
                    this.previousHop.sshj
                            .newLocalPortForwarder(
                                    new Parameters("127.0.0.1", port, info.getHost(), info.getPort()), serverSocket)
                            .listen();
                } catch (IOException e) {
                    throw ExceptionUtils.sneakyThrow(e);
                }
            });
            thread.setUncaughtExceptionHandler((th, e) -> thrown.set(e));
            thread.start();
            
            // Wait until bound
            while (thread.isAlive() && serverSocket.isBound() && thrown.get() == null) {
                // Yes! busy-waiting
                // TODO find the way to block server socket until bound
                //noinspection BusyWait
                Thread.sleep(100);
            }
            
            if(thrown.get() != null){
                if(thrown.get() instanceof IOException)
                    throw (IOException) thrown.get();
                else
                    throw new IOException("Unknown exception.", thrown.get());
            }
            
            this.sshj.connect("127.0.0.1", port);
        }
        
        public void disconnect() {
            
            try {
                if (sshj != null)
                    sshj.disconnect();
            } catch (Exception e) {
                LOG.error("Error while disconnecting", e);
            }
            try {
                if (previousHop != null)
                    previousHop.disconnect();
            } catch (Exception e) {
                LOG.error("Error while disconnecting", e);
            }
            try {
                if (this.serverSocket != null) {
                    this.serverSocket.close();
                    if(thread != null) {
                        thread.join(); // Try to join the thread after stopping serversocket
                    }
                }
            } catch (Exception e) {
                LOG.error("Error while disconnecting", e);
            }
        }
        
        private void setupProxyAndSocketFactory() {
            String proxyHost = info.getProxyHost();
            int proxyType = info.getProxyType();
            String proxyUser = info.getProxyUser();
            String proxyPass = info.getProxyPassword();
            int proxyPort = info.getProxyPort();
            
            Proxy.Type proxyType1 = Proxy.Type.DIRECT;
            
            if (proxyType == 1) {
                proxyType1 = Proxy.Type.HTTP;
            } else if (proxyType > 1) {
                proxyType1 = Proxy.Type.SOCKS;
            }
            
            sshj.setSocketFactory(new CustomSocketFactory(proxyHost, proxyPort, proxyUser, proxyPass, proxyType1));
        }
        
    }
    
}
