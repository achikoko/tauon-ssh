package tauon.app.ui.containers.session.pages.terminal;

import tauon.app.ui.components.closabletabs.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tauon.app.exceptions.SessionClosedException;
import tauon.app.ssh.GuiHandle;

import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalTerminalHolder extends AbstractTerminalHolder implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(LocalTerminalHolder.class);
    
    private final AtomicBoolean init = new AtomicBoolean(false);
    
    private int c = 1;
    
    public LocalTerminalHolder(GuiHandle guiHandle) {
        super(guiHandle);
        
        try {
            openNewTerminal(null, false); // It is started at onLoad()
        } catch (SessionClosedException e) {
            guiHandle.reportException(e);
        }
        
    }

    @Override
    public void onLoad() {
        if (init.get()) {
            return;
        }
        init.set(true);
        LocalTerminalComponent tc = (LocalTerminalComponent) this.tabs.getSelectedContent();
        tc.start();
    }

    public void close() {
        Component[] components = tabs.getTabContents();
        for (Component c : components) {
            if (c instanceof LocalTerminalComponent) {
                System.out.println("Closing terminal: " + c);
                ((LocalTerminalComponent) c).close();
            }
        }
        super.close();
    }
    
    @Override
    public void onNewTabClicked(Location location) throws SessionClosedException {
        openNewTerminal(null, true, location);
    }
    
    public void openNewTerminal(String command, boolean start) throws SessionClosedException {
        openNewTerminal(command, start, null);
    }

    public void openNewTerminal(String command, boolean start, Location location) throws SessionClosedException {
        c++;
        LocalTerminalComponent tc = new LocalTerminalComponent(
                String.valueOf(c),
                command,
                guiHandle
        );
        tc.setTabHandle(this.tabs.addTab(tc, location));
        if(start)
            tc.start();
    }
}
