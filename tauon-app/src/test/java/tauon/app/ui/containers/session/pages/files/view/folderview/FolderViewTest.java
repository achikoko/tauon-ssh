package tauon.app.ui.containers.session.pages.files.view.folderview;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import tauon.app.App;
import tauon.app.services.SettingsConfigManager;
import tauon.app.settings.Settings;
import tauon.app.ssh.filesystem.FileInfo;
import tauon.app.ssh.filesystem.FileType;
import tauon.app.ui.laf.AppSkinLight;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FolderViewTest {

    private FolderView view;
    private final List<String> navigated = new ArrayList<>();

    @BeforeClass
    public static void installSkin() throws Exception {
        App.skin = new AppSkinLight();
        UIManager.setLookAndFeel(App.skin.getLaf());
        // Default settings, without SettingsConfigManager.initialize() reading the user's config file
        java.lang.reflect.Field field = SettingsConfigManager.class.getDeclaredField("settings");
        field.setAccessible(true);
        if (field.get(SettingsConfigManager.getInstance()) == null) {
            field.set(SettingsConfigManager.getInstance(), new Settings());
        }
    }

    @Before
    public void setUp() {
        view = new FolderView(new FolderViewEventListener() {
            public void navigate(String path) { navigated.add(path); }
            public void render(String path) { }
            public void render(String path, boolean useCache) { }
            public void openApp(FileInfo file) { }
            public boolean createMenu(JPopupMenu popupMenu, FileInfo[] files) { return false; }
            public void install(JComponent c) { }
            public void reload() { }
        }, text -> { });
        view.setShowHiddenFiles(true);
    }

    /** As LocalFileSystem creates them: empty extra, so no owner, and an empty permission string. */
    private static FileInfo local(String name, FileType type, long modified) {
        return new FileInfo(name, "/tmp/" + name, 1, type, modified, -1, "local", "", 0, "", false);
    }

    private static List<String> names(JList<FileInfo> list) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < list.getModel().getSize(); i++) {
            names.add(list.getModel().getElementAt(i).getName());
        }
        return names;
    }

    @Test
    public void sortingLocalFilesByOwnerOrPermissionsDoesNotThrow() {
        assertNull("precondition: local files have no owner", local("a", FileType.FILE, 0).getUser());
        view.setItems(List.of(local("b", FileType.FILE, 1), local("a", FileType.FILE, 2), local("c", FileType.DIR, 3)));
        view.sort(5, SortOrder.ASCENDING); // owner
        view.sort(4, SortOrder.DESCENDING); // permission
        assertEquals(3, view.getTable().getRowCount());
    }

    @Test
    public void togglingHiddenFilesBeforeTheFirstListingDoesNotThrow() {
        FolderView fresh = new FolderView(new FolderViewEventListener() {
            public void navigate(String path) { }
            public void render(String path) { }
            public void render(String path, boolean useCache) { }
            public void openApp(FileInfo file) { }
            public boolean createMenu(JPopupMenu popupMenu, FileInfo[] files) { return false; }
            public void install(JComponent c) { }
            public void reload() { }
        }, text -> { });
        fresh.setShowHiddenFiles(false);
        fresh.setShowHiddenFiles(true);
        assertEquals(0, fresh.getFiles().length);
    }

    @Test
    public void listViewShowsTheTableOrder() {
        view.setItems(List.of(local("b", FileType.FILE, 1), local("c", FileType.FILE, 2), local("a", FileType.FILE, 3)));
        view.sort(0, SortOrder.ASCENDING);
        assertEquals(List.of("a", "b", "c"), names(view.getFileList()));
        view.sort(0, SortOrder.DESCENDING);
        assertEquals(List.of("c", "b", "a"), names(view.getFileList()));
    }

    @Test
    public void listViewSelectionIsTheSelectionUsedByMenus() {
        view.setItems(List.of(local("b", FileType.FILE, 1), local("c", FileType.FILE, 2), local("a", FileType.FILE, 3)));
        view.sort(0, SortOrder.ASCENDING);
        view.getFileList().setSelectedIndex(2); // "c" as shown in the list

        FileInfo[] selected = view.getSelectedFiles();
        assertEquals(1, selected.length);
        assertEquals("c", selected[0].getName());
    }

    @Test
    public void enterInTheListViewOpensTheSelectedFolder() {
        view.setItems(List.of(local("dir", FileType.DIR, 1), local("file", FileType.FILE, 2)));
        view.sort(0, SortOrder.ASCENDING);
        JList<FileInfo> list = view.getFileList();
        list.setSelectedIndex(0);

        Action enter = list.getActionMap().get(list.getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0)));
        assertNotNull(enter);
        enter.actionPerformed(null);
        assertEquals(List.of("/tmp/dir"), navigated);
    }
}
