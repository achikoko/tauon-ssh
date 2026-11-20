package tauon.app.ui.containers.session.pages.files;

import tauon.app.util.misc.UIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tauon.app.exceptions.RemoteOperationException;
import tauon.app.exceptions.SessionClosedException;
import tauon.app.services.SettingsConfigManager;
import tauon.app.settings.SiteInfo;
import tauon.app.ssh.GuiHandle;
import tauon.app.ssh.SSHConnectionHandler;
import tauon.app.ssh.filesystem.FileInfo;
import tauon.app.ssh.filesystem.FileSystem;
import tauon.app.ssh.filesystem.SshFileSystem;
import tauon.app.ssh.filesystem.transfer.FileTransfer;
import tauon.app.ssh.filesystem.transfer.FileTransferLocalToRemote;
import tauon.app.ssh.filesystem.transfer.FileTransferRemoteToLocal;
import tauon.app.ui.components.closabletabs.ClosableTabbedPanel;
import tauon.app.ui.components.closabletabs.Location;
import tauon.app.ui.components.closabletabs.TabHandle;
import tauon.app.ui.components.misc.FontAwesomeContants;
import tauon.app.ui.components.page.Page;
import tauon.app.ui.containers.session.SessionContentPanel;
import tauon.app.ui.containers.session.pages.files.local.LocalFileBrowserView;
import tauon.app.ui.containers.session.pages.files.ssh.SshFileBrowserView;
import tauon.app.ui.containers.session.pages.files.transfer.DndTransferData;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static tauon.app.services.LanguageService.getBundle;

public class FileBrowser extends Page {
    private static final Logger LOG = LoggerFactory.getLogger(FileBrowser.class);
    
    private final ClosableTabbedPanel tabs;
    private final SessionContentPanel holder;
    private final SiteInfo info;
    private final Map<String, List<FileInfo>> sshDirCache = new HashMap<>();
    private final AtomicBoolean init = new AtomicBoolean(false);
    private final JPopupMenu popup;
    private final Map<UUID, AbstractFileBrowserView> viewList = new HashMap<>();
    private FileTransfer ongoingFileTransfer;
    /** Where the tab chosen in the new-tab popup goes: the group whose "+" opened it. */
    private Location popupLocation;
    private JLabel lblStat1;
    private Box statusBox;
    
    private final SshFileSystem sshFileSystem;
    private final SSHConnectionHandler sshConnectionHandler;
    
    public FileBrowser(SiteInfo info, SessionContentPanel holder) {

        this.info = info;
        this.holder = holder;
        this.sshConnectionHandler = holder.getSshConnectionHandler();
        this.sshFileSystem = sshConnectionHandler.getSshFileSystem();
        
        JMenuItem localMenuItem = new JMenuItem("Local file browser");
        JMenuItem remoteMenuItem = new JMenuItem("Remote file browser");

        popup = new JPopupMenu();
        popup.add(remoteMenuItem);
        popup.add(localMenuItem);
        popup.pack();

        localMenuItem.addActionListener(e -> openLocalFileBrowserView(null, popupLocation));
        remoteMenuItem.addActionListener(e -> openSshFileBrowserView(null, popupLocation));

        // One tab panel: the remote and local browsers open side by side (onLoad), and any tab can be dragged to
        // another group or split off.
        this.tabs = new ClosableTabbedPanel((c, location) -> {
            popup.setInvoker(c);
            popupLocation = location;
            popup.show(c, 0, c.getHeight());
        });
        this.add(this.tabs);
    }

    /**
     * Opens a remote browser.
     *
     * @param location where its tab goes; {@code null} for the active group
     */
    public TabHandle openSshFileBrowserView(String path, Location location) {
        SshFileBrowserView tab = new SshFileBrowserView(this, path);
        TabHandle handle = this.tabs.addTab(tab, location);
        tab.setTabHandle(handle);
        return handle;
    }

    /**
     * Opens a local browser.
     *
     * @param location where its tab goes; {@code null} for the active group
     */
    public TabHandle openLocalFileBrowserView(String path, Location location) {
        LocalFileBrowserView tab = new LocalFileBrowserView(this, path);
        TabHandle handle = this.tabs.addTab(tab, location);
        tab.setTabHandle(handle);
        return handle;
    }

    /** @return where a tab opened from {@code view} goes: next to it, in its group */
    public Location locationNextTo(AbstractFileBrowserView view) {
        return view.getTabHandle() == null ? null : tabs.locationOf(view.getTabHandle());
    }

    public SshFileSystem getSshFileSystem() {
        return sshFileSystem;
    }

    public SiteInfo getInfo() {
        return info;
    }

    public Map<String, List<FileInfo>> getSSHDirectoryCache() {
        return this.sshDirCache;
    }

//    public void newFileTransfer(FileSystem sourceFs, FileSystem targetFs, FileInfo[] files, String targetFolder) {
//        System.out.println("Initiating new file transfer...");
////        this.ongoingFileTransfer =
//
//        holder.getAppWindow().getFileTransferManager().startFileTransfer(
//                new FileTransfer(sourceFs, targetFs, files, targetFolder, SettingsConfigManager.getSettings().getConflictAction(), holder),
//                false,
//                new FileTransferProgress.Adapter(){
//
//                    @Override
//                    public void error(String cause, Exception e, FileTransfer fileTransfer) {
//                        if (!holder.isSessionClosed()) {
//                            JOptionPane.showMessageDialog(null, getBundle().getString("general.message.operation_failed"));
//                        }
//                    }
//
//                    @Override
//                    public void done(FileTransfer fileTransfer) {
//                        reloadView();
//                    }
//                }
//        );
//
//    }

    /** Reloads the browsers on screen: each group's selected one. */
    private void reloadView() {
        for (Component c : tabs.getSelectedContents()) {
            if (c instanceof AbstractFileBrowserView) {
                ((AbstractFileBrowserView) c).reload();
            }
        }
    }
    
    @Override
    public void onLoad() {
        if (init.get()) {
            return;
        }
        init.set(true);
        // Remote on the left, local on the right, side by side
        TabHandle remote = openSshFileBrowserView(null, null);
        openLocalFileBrowserView(System.getProperty("user.home"), tabs.beside(remote, Location.Side.RIGHT));
    }

    @Override
    public String getIcon() {
        return FontAwesomeContants.FA_FOLDER;
    }

    @Override
    public String getText() {
        return getBundle().getString("app.files.title");
    }

    /**
     * @return the holder
     */
    public SessionContentPanel getHolder() {
        return holder;
    }

    public void openPath(String path) {
        openSshFileBrowserView(path, null);
    }

    public boolean isSessionClosed() {
        return this.holder.isSessionClosed();
    }

    public boolean handleLocalDrop(DndTransferData transferData, FileSystem currentFileSystem,
                                   String currentPath) {
        // TODO i18n
        if (SettingsConfigManager.getSettings().isConfirmBeforeMoveOrCopy()
                && JOptionPane.showConfirmDialog(null, "Move/copy files?") != JOptionPane.YES_OPTION) {
            return false;
        }

        try {

            System.out.println("Dropped: " + transferData);
            AbstractFileBrowserView source = transferData.getSource();
            if (source == null) {
                // Comes from local
                System.out.println("Session hash code: " + source);
                return true;
            }

//            if (source.getFileBrowser() == this) {
//                if (SettingsConfigManager.getSettings().getFileTransferMode() == Constants.TransferMode.BACKGROUND) {
                    downloadInBackground(transferData.getFiles(), currentPath);
//                    return true;
//                }
//                FileSystem sourceFs = this.getSshFileSystem();
//                if (sourceFs == null) {
//                    return false;
//                }
//                FileSystem targetFs = currentFileSystem;
//                this.newFileTransfer(sourceFs, targetFs, transferData.getFiles(), currentPath);
//            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * It only implements the methods that {@link FileTransfer} uses.
     */
    private static class ToGuiThread extends GuiHandle.Delegate {
        
        public ToGuiThread(GuiHandle delegator) {
            super(delegator);
        }
        
        @Override
        public void showMessage(String name, String instruction) {
            try {
                UIUtil.invokeAndWaitIfNeeded(() -> {
                    super.showMessage(name, instruction);
                });
            } catch (InterruptedException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        
        @Override
        public boolean promptConfirmation(String name, String instruction) {
            try {
                return UIUtil.invokeAndWaitIfNeeded(() -> {
                    return super.promptConfirmation(name, instruction);
                });
            } catch (InterruptedException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        
    }
    
    public void downloadInBackground(FileInfo[] remoteFiles, String targetLocalDirectory) {
        
        FileTransferRemoteToLocal transfer = new FileTransferRemoteToLocal(
                remoteFiles, targetLocalDirectory, new ToGuiThread(getHolder()), sshConnectionHandler,
                SettingsConfigManager.getSettings().getConflictAction()
        );
        
        holder.submitSSHOperation((guiHandle, instance) -> {
            transfer.prepareTransfer(instance.getSshFileSystem());
            
            if(transfer.isPrepared()) {
                getHolder().getAppWindow().startFileTransfer(transfer);
            }
            
        });
        
//                .getFileTransferManager().startFileTransfer(
//                transfer,
//                true,
//                new FileTransferProgress.Adapter(){
//                    @Override
//                    public void done(FileTransfer fileTransfer) {
//                        sourceFs.dispose();
//                    }
//
//                    @Override
//                    public void error(String cause, Exception e, FileTransfer fileTransfer) {
//                        sourceFs.dispose();
//                    }
//                }
//        );
    }
    
    public void uploadInBackground(FileInfo[] localFiles, String targetRemoteDirectory) throws RemoteOperationException, SessionClosedException {
        
        FileTransferLocalToRemote transfer = new FileTransferLocalToRemote(
                localFiles, targetRemoteDirectory, getHolder(), sshConnectionHandler,
                SettingsConfigManager.getSettings().getConflictAction()
        );
        
        holder.runSSHOperation((guiHandle, instance) -> {
            transfer.prepareTransfer(instance.getSshFileSystem());
        });
        
        if(transfer.isPrepared()) {
            getHolder().getAppWindow().startFileTransfer(transfer);
        }
        
    }

    public void refreshViewMode() {
        for (AbstractFileBrowserView view : this.viewList.values()) {
            view.refreshViewMode();
        }
        this.revalidate();
        this.repaint(0);
    }

    public void registerForViewNotification(AbstractFileBrowserView view) {
        this.viewList.put(view.getUUID(), view);
    }

    public void unRegisterForViewNotification(AbstractFileBrowserView view) {
        this.viewList.remove(view.getUUID());
    }

    public void updateRemoteStatus(String text) {
    }
    
    public void notifyTransferDone(FileTransfer fileTransfer) {
        reloadView();
    }
    
    public void close() {
    
    }
    
    public AbstractFileBrowserView findViewById(UUID uuid) {
        return viewList.get(uuid);
    }
    
}
