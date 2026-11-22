package tauon.app.ui.containers.session.pages.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tauon.app.App;
import tauon.app.exceptions.OperationCancelledException;
import tauon.app.exceptions.RemoteOperationException;
import tauon.app.exceptions.SessionClosedException;
import tauon.app.services.SettingsConfigManager;
import tauon.app.ssh.filesystem.FileInfo;
import tauon.app.ssh.filesystem.FileSystem;
import tauon.app.ui.components.closabletabs.TabHandle;
import tauon.app.ui.containers.session.pages.files.transfer.DndTransferData;
import tauon.app.ui.containers.session.pages.files.view.OverflowMenuHandler;
import tauon.app.ui.containers.session.pages.files.view.addressbar.AddressBar;
import tauon.app.ui.containers.session.pages.files.view.folderview.FolderView;
import tauon.app.ui.containers.session.pages.files.view.folderview.FolderViewEventListener;
import tauon.app.util.misc.LayoutUtilities;
import tauon.app.util.misc.PathUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A file browser tab (local or remote): toolbar, address bar and folder view.
 * <p>
 * Every user navigation goes through {@link #navigate(String)} (or Back/Forward). The folder is listed in the
 * background; {@link #path}, the address bar and the history only change once it has been listed, on the EDT.
 */
public abstract class AbstractFileBrowserView extends JPanel implements FolderViewEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractFileBrowserView.class);

    private final NavigationHistory history;
    private final JButton btnBack;
    private final JButton btnNext;
    private final OverflowMenuHandler overflowMenuHandler;
    protected AddressBar addressBar;
    protected FolderView folderView;
    /** Folder shown; {@code null} until the first listing. Only written on the EDT. */
    protected String path;
    /** Incremented on each listing request; a listing finished after a newer request is dropped. EDT only. */
    private int listingRequest;

    private TabHandle tabHandle;

    protected FileBrowser fileBrowser;

    private final UUID uuid = UUID.randomUUID();

    /** Receives the result of {@link #listFolder}. */
    protected interface ListingCallback {
        /**
         * @param listedPath the folder actually listed (e.g. the home folder when asked for {@code null})
         * @param files      its contents
         */
        void listed(String listedPath, List<FileInfo> files);
    }

    public AbstractFileBrowserView(FileBrowser fileBrowser) {
        super(new BorderLayout());
        this.fileBrowser = fileBrowser;

        UIDefaults toolbarButtonSkin = App.skin.createToolbarSkin();

        overflowMenuHandler = new OverflowMenuHandler(this, fileBrowser);
        history = new NavigationHistory();
        JPanel toolBar = new JPanel(new BorderLayout());
        createAddressBar();
        addressBar.addActionListener(e -> {
            String text = e.getActionCommand();
            LOG.debug("Address changed: {} old: {}", text, this.path);
            if (text != null && !text.isEmpty() && !PathUtils.isSamePath(this.path, text)) {
                navigate(text);
            }
        });
        Box smallToolbar = Box.createHorizontalBox();

        AbstractAction upAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                up();
            }
        };
        AbstractAction reloadAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reload();
            }
        };

        btnBack = new JButton();
        btnBack.putClientProperty("Nimbus.Overrides", toolbarButtonSkin);
        btnBack.setFont(App.skin.getIconFont());
        btnBack.setText("\uf060");
        btnBack.addActionListener(e -> back());

        btnNext = new JButton();
        btnNext.putClientProperty("Nimbus.Overrides", toolbarButtonSkin);
        btnNext.setFont(App.skin.getIconFont());
        btnNext.setText("\uf061");
        btnNext.addActionListener(e -> forward());

        JButton btnHome = new JButton();
        btnHome.putClientProperty("Nimbus.Overrides", toolbarButtonSkin);
        btnHome.setFont(App.skin.getIconFont());
        btnHome.setText("\uf015");
        btnHome.addActionListener(e -> home());

        JButton btnUp = new JButton();
        btnUp.putClientProperty("Nimbus.Overrides", toolbarButtonSkin);
        btnUp.addActionListener(upAction);
        btnUp.setFont(App.skin.getIconFont());
        btnUp.setText("\uf062");

        smallToolbar.add(Box.createHorizontalStrut(5));

        JButton btnReload = new JButton();
        btnReload.putClientProperty("Nimbus.Overrides", toolbarButtonSkin);
        btnReload.addActionListener(reloadAction);
        btnReload.setFont(App.skin.getIconFont());
        btnReload.setText("\uf021");


        JButton btnMore = new JButton();
        btnMore.putClientProperty("Nimbus.Overrides", toolbarButtonSkin);
        btnMore.setFont(App.skin.getIconFont());
        btnMore.setText("\uf142");
        btnMore.addActionListener(e -> {
            JPopupMenu popupMenu = overflowMenuHandler.getOverflowMenu();
            overflowMenuHandler.loadFavourites();
            popupMenu.pack();
            Dimension d = popupMenu.getPreferredSize();
            int x = btnMore.getWidth() - d.width;
            int y = btnMore.getHeight();
            popupMenu.show(btnMore, x, y);
        });

        LayoutUtilities.equalizeSize(btnMore, btnReload, btnUp, btnHome, btnNext, btnBack);

        smallToolbar.add(btnBack);
        smallToolbar.add(btnNext);
        smallToolbar.add(btnHome);
        smallToolbar.add(btnUp);

        Box b2 = Box.createHorizontalBox();
        b2.setBorder(new EmptyBorder(3, 0, 3, 0));
        b2.add(btnReload);
        b2.add(btnMore);

        toolBar.add(smallToolbar, BorderLayout.WEST);
        toolBar.add(addressBar);
        toolBar.add(b2, BorderLayout.EAST);
        toolBar.setBorder(new EmptyBorder(5, 5, 5, 5));

        add(toolBar, BorderLayout.NORTH);

        folderView = new FolderView(this, text -> this.fileBrowser.updateRemoteStatus(text));

        this.overflowMenuHandler.setFolderView(folderView);

        add(folderView);

        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "up");
        this.getActionMap().put("up", upAction);

        updateNavButtons();

        this.fileBrowser.registerForViewNotification(this);

    }

    public void setTabHandle(TabHandle tabHandle) {
        this.tabHandle = tabHandle;
        tabHandle.setClosable(this::close);
    }

    protected void setTabTitle(String path){
        if (tabHandle == null) {
            return;
        }
        String pref = getTitlePrefix();
        if(pref != null){
            tabHandle.setTitle("<html><b>" + pref + "</b>: " + PathUtils.getFileName(path));
        }else{
            tabHandle.setTitle(PathUtils.getFileName(path));
        }
    }

    protected abstract void createAddressBar();

    public abstract String getTitlePrefix();

    public abstract String getHostText();

    public abstract String getPathText();

    @Override
    public abstract String toString();

    public boolean close() {
        LOG.trace("Unregistering for view mode notification");
        this.fileBrowser.unRegisterForViewNotification(this);
        return true;
    }

    public String getCurrentDirectory() {
        return this.path;
    }

    public abstract boolean handleDrop(DndTransferData transferData);

    /**
     * Lists {@code target} in the background. On success calls {@code done} (from any thread); on failure reports
     * the error to the user and calls {@code failed} instead.
     *
     * @param target the folder; {@code null} for the home folder
     */
    protected abstract void listFolder(String target, boolean useCache, ListingCallback done, Runnable failed);

    /** @return the parent folder of {@code path}, or {@code null} at a root */
    protected abstract String getParentPath(String path);

    /** Goes to the parent folder; does nothing at a root. */
    protected void up() {
        String parent = this.path == null ? null : getParentPath(this.path);
        if (parent != null) {
            navigate(parent);
        }
    }

    /** Goes to the home folder. */
    protected void home() {
        navigate(null);
    }

    /**
     * Goes to {@code target}; once it is listed, the folder left behind is added to the back history and the
     * forward history is dropped.
     *
     * @param target the folder; {@code null} for the home folder
     */
    @Override
    public void navigate(String target) {
        String from = this.path;
        load(target, SettingsConfigManager.getSettings().isDirectoryCache(), listed -> history.visit(from, listed));
    }

    private void back() {
        if (!history.hasPrevElement()) {
            return;
        }
        String from = this.path;
        load(history.peekBack(), SettingsConfigManager.getSettings().isDirectoryCache(), listed -> history.back(from));
    }

    private void forward() {
        if (!history.hasNextElement()) {
            return;
        }
        String from = this.path;
        load(history.peekForward(), SettingsConfigManager.getSettings().isDirectoryCache(), listed -> history.forward(from));
    }

    /** Shows {@code path} without touching the history (refresh after an operation, first listing). */
    @Override
    public void render(String path) {
        render(path, false);
    }

    /** Shows {@code path} without touching the history (refresh after an operation, first listing). */
    @Override
    public void render(String path, boolean useCache) {
        load(path, useCache, null);
    }

    @Override
    public void reload() {
        this.render(this.path, false);
    }

    /**
     * Lists {@code target} and shows it. Callable from any thread: file operations refresh the view from their
     * worker thread.
     *
     * @param onListed runs on the EDT with the listed path, after the view shows it; {@code null} for none
     */
    private void load(String target, boolean useCache, Consumer<String> onListed) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> load(target, useCache, onListed));
            return;
        }
        int request = ++listingRequest;
        listFolder(target, useCache,
                (listedPath, files) -> SwingUtilities.invokeLater(() -> {
                    if (request != listingRequest) {
                        return; // superseded by a newer navigation
                    }
                    showListing(listedPath, files);
                    if (onListed != null) {
                        onListed.accept(listedPath);
                    }
                    updateNavButtons();
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    if (request == listingRequest && this.path != null) {
                        addressBar.setText(this.path); // drop what was typed
                    }
                }));
    }

    private void showListing(String listedPath, List<FileInfo> files) {
        this.path = listedPath;
        addressBar.setText(listedPath);
        folderView.setItems(files);
        setTabTitle(listedPath);
        fileBrowser.updateRemoteStatus(String.format("Total %d file(s)", files.size()));
    }

    public TabHandle getTabHandle() {
        return tabHandle;
    }

    private void updateNavButtons() {
        btnBack.setEnabled(history.hasPrevElement());
        btnNext.setEnabled(history.hasNextElement());
    }

    public OverflowMenuHandler getOverflowMenuHandler() {
        return this.overflowMenuHandler;
    }

    public abstract FileSystem getFileSystem() throws OperationCancelledException, RemoteOperationException, InterruptedException, SessionClosedException;

    public void refreshViewMode() {
        this.folderView.refreshViewMode();
        this.revalidate();
        this.repaint();
    }

    public FileBrowser getFileBrowser() {
        return this.fileBrowser;
    }

    public FileInfo[] getSelectedFiles(){
        return folderView.getSelectedFiles();
    }

    public UUID getUUID() {
        return uuid;
    }


}
