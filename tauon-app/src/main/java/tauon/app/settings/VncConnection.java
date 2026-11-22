package tauon.app.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

/**
 * A VNC server reachable from a site's SSH server, opened with an external TurboVNC viewer through an SSH tunnel.
 * <p>
 * The password is not serialized with the sites: it is stored encrypted in the passwords file, keyed by {@link #getId()}
 * (see {@code SitesConfigManager}).
 */
public class VncConnection {

    /** VNC display N listens on port 5900 + N. */
    public static final int DISPLAY_BASE_PORT = 5900;

    private String id = UUID.randomUUID().toString();
    private String name;
    /** As seen from the SSH server (usually localhost). */
    private String host = "localhost";
    private int port = DISPLAY_BASE_PORT + 1;
    private String password;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /** @return the VNC display number for this port, or -1 if the port is below 5900 */
    @JsonIgnore
    public int getDisplay() {
        return port >= DISPLAY_BASE_PORT ? port - DISPLAY_BASE_PORT : -1;
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    @JsonIgnore
    public void setPassword(String password) {
        this.password = password;
    }

    /** @return the name, or host:port if it has none */
    @JsonIgnore
    public String getDisplayName() {
        return name != null && !name.isBlank() ? name : host + ":" + port;
    }

    public VncConnection copyTo(VncConnection other) {
        other.id = id;
        other.name = name;
        other.host = host;
        other.port = port;
        other.password = password;
        return other;
    }
}
