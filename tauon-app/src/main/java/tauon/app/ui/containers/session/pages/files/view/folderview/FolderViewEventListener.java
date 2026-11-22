package tauon.app.ui.containers.session.pages.files.view.folderview;

import tauon.app.ssh.filesystem.FileInfo;

import javax.swing.*;

public interface FolderViewEventListener {
    /** User navigation to {@code path} (recorded in the history); {@code null} for the home folder. */
    void navigate(String path);

    /** Shows {@code path} without recording it in the history. */

    void render(String path);

    void render(String path, boolean useCache);

    void openApp(FileInfo file);

    boolean createMenu(JPopupMenu popupMenu, FileInfo[] files);

    void install(JComponent c);

    void reload();
}
