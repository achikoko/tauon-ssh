/**
 *
 */
package tauon.app.ui.containers.session.pages.logviewer;

import tauon.app.ssh.filesystem.FileInfo;
import tauon.app.ui.components.closabletabs.ClosableTabbedPanel;
import tauon.app.ui.components.closabletabs.Location;
import tauon.app.ui.components.closabletabs.TabHandle;
import tauon.app.ui.components.misc.FontAwesomeContants;
import tauon.app.ui.components.misc.SkinnedTextField;
import tauon.app.ui.components.page.Page;
import tauon.app.ui.containers.session.SessionContentPanel;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

import static tauon.app.services.LanguageService.getBundle;

/**
 * @author subhro
 *
 */
public class LogViewer extends Page {
    private final ClosableTabbedPanel tabs;
    private final StartPage startPage;
    private final JPanel content;
    private final SessionContentPanel sessionContent;
    private final Map<String, TabHandle> openLogs = new HashMap<>();

    /**
     *
     */
    public LogViewer(SessionContentPanel sessionContent) {
        this.sessionContent = sessionContent;
        startPage = new StartPage(this::openLog, sessionContent.getInfo().getId());
        content = new JPanel();
        tabs = new ClosableTabbedPanel((button, location) -> {
            String path = promptLogPath();
            if (path != null) {
                openLog(path, location);
            }
        });
        
        // Ignoring the TabHandle because we never will change the name nor close it.
        this.tabs.addTab(startPage, null).setTitle(getBundle().getString("app.logs.label.pinned_logs"));
        
        this.add(tabs);
    }

    @Override
    public void onLoad() {

    }

    @Override
    public String getIcon() {
        return FontAwesomeContants.FA_STICKY_NOTE;
    }

    @Override
    public String getText() {
        return getBundle().getString("app.logs.title");
    }

    public void openLog(FileInfo remotePath) {
        openLog(remotePath.getPath());
    }

    public void openLog(String remotePath) {
        openLog(remotePath, null);
    }

    public void openLog(String remotePath, Location location) {
        TabHandle open = openLogs.get(remotePath);
        if (open != null) {
            tabs.select(open);
            return;
        }
        LogContent logContent = new LogContent(sessionContent, remotePath, startPage, e -> openLogs.remove(remotePath));
        TabHandle handle = tabs.addTab(logContent, location);
        logContent.setTabHandle(handle);
        openLogs.put(remotePath, handle);
    }

    private String promptLogPath() {
        JTextField txt = new SkinnedTextField(30);
        if (JOptionPane.showOptionDialog(this,
                new Object[]{"Please provide full path of the log file",
                        txt},
                "Input", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, null,
                null) == JOptionPane.OK_OPTION && txt.getText().length() > 0) {
            return txt.getText();
        }
        return null;
    }

}
