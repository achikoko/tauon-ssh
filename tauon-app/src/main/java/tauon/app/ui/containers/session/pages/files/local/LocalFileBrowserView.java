package tauon.app.ui.containers.session.pages.files.local;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tauon.app.exceptions.LocalOperationException;
import tauon.app.services.SettingsConfigManager;
import tauon.app.ssh.filesystem.FileInfo;
import tauon.app.ssh.filesystem.FileSystem;
import tauon.app.ssh.filesystem.LocalFileSystem;
import tauon.app.ui.containers.session.pages.files.AbstractFileBrowserView;
import tauon.app.ui.containers.session.pages.files.FileBrowser;
import tauon.app.ui.containers.session.pages.files.view.addressbar.AddressBar;
import tauon.app.ui.containers.session.pages.files.transfer.DndTransferData;
import tauon.app.ui.containers.session.pages.files.transfer.DndTransferHandler;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

import static tauon.app.services.LanguageService.getBundle;

public class LocalFileBrowserView extends AbstractFileBrowserView {
    private static final Logger LOG = LoggerFactory.getLogger(LocalFileBrowserView.class);
    
    private final LocalMenuHandler menuHandler;
    private final DndTransferHandler transferHandler;
    private final JPopupMenu addressPopup;
    private final LocalFileSystem fs = LocalFileSystem.getInstance();

    public LocalFileBrowserView(FileBrowser fileBrowser, String initialPath) {
        super(fileBrowser);
        this.menuHandler = new LocalMenuHandler(fileBrowser, this);
        this.menuHandler.initMenuHandler(this.folderView);
        this.transferHandler = new DndTransferHandler(this);
        this.folderView.setTransferHandler(transferHandler);
        this.folderView.setFolderViewTransferHandler(transferHandler);
        this.addressPopup = menuHandler.createAddressPopup();

        // An explicit folder ("Open in new tab") wins; otherwise the site's local folder, then home
        String startPath = initialPath;
        if (startPath == null && this.fileBrowser.getInfo().getLocalFolder() != null
                && this.fileBrowser.getInfo().getLocalFolder().trim().length() > 1) {
            startPath = fileBrowser.getInfo().getLocalFolder();
        }
        // A start folder that is gone falls back to the home folder (null) instead of an error
        if (startPath != null && !isDirectory(startPath)) {
            LOG.warn("Local folder {} doesn't exist, opening the home folder.", startPath);
            startPath = null;
        }
        render(startPath);
    }

    private static boolean isDirectory(String path) {
        try {
            return Files.isDirectory(Paths.get(path));
        } catch (InvalidPathException e) {
            return false;
        }
    }
    
    @Override
    public String getTitlePrefix() {
        return getBundle().getString("app.files.tabs.local.title_prefix");
    }
    
    public void createAddressBar() {
        addressBar = new AddressBar(File.separatorChar, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedPath = e.getActionCommand();
                addressPopup.setName(selectedPath);
                MouseEvent me = (MouseEvent) e.getSource();
                addressPopup.show(me.getComponent(), me.getX(), me.getY());
                System.out.println("clicked");
            }
        });
        if (SettingsConfigManager.getSettings().isShowPathBar()) {
            addressBar.switchToPathBar();
        } else {
            addressBar.switchToText();
        }
    }

    @Override
    public String toString() {
        return "Local files [" + this.path + "]";
    }

    public String getHostText() {
        return "Local files";
    }

    public String getPathText() {
        return (this.path == null || this.path.length() < 1 ? "" : this.path);
    }

    @Override
    protected void listFolder(String target, boolean useCache, ListingCallback done, Runnable failed) {
        fileBrowser.getHolder().submitLocalOperation(() -> {
            boolean listed = false;
            try {
                String folder = target == null ? fs.getHome() : target;
                File file = new File(folder);
                if (!file.exists()) {
                    throw new LocalOperationException.FileNotFound(folder);
                }
                if (!file.isDirectory()) {
                    throw new LocalOperationException.NotAFolder(file);
                }
                if (!file.canRead()) {
                    // listFiles() would return null and the folder would look empty
                    throw new LocalOperationException.AccessDenied(folder);
                }
                done.listed(folder, fs.list(folder));
                listed = true;
            } finally {
                if (!listed) {
                    failed.run();
                }
            }
        });
    }

    @Override
    protected String getParentPath(String path) {
        return new File(path).getParent();
    }

    @Override
    public void openApp(FileInfo file) {
    }

    @Override
    public boolean createMenu(JPopupMenu popup, FileInfo[] files) {
        menuHandler.createMenu(popup, files);
        return true;
    }

    @Override
    public void install(JComponent c) {

    }

    public boolean handleDrop(DndTransferData transferData) {
        System.out.println("### " + transferData.getDestination() + " " + this.hashCode());
        if (transferData.getDestination() == this) {
            return false; // TODO handle move files inside same browser
        }
        return this.fileBrowser.handleLocalDrop(transferData, this.fs, this.path);
    }

    public FileSystem getFileSystem() {
        return LocalFileSystem.getInstance();
    }
}
