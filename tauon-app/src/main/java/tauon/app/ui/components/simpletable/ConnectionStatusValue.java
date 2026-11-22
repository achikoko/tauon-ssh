package tauon.app.ui.components.simpletable;

public enum ConnectionStatusValue {
    DISABLED,
    ERROR,
    ESTABLISHED,
    STOPPED,
    INITIATING,
    /** A child process (e.g. a VNC viewer) is running. */
    RUNNING
}
