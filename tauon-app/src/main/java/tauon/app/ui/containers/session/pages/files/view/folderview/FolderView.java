package tauon.app.ui.containers.session.pages.files.view.folderview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tauon.app.App;
import tauon.app.services.SettingsConfigManager;
import tauon.app.ssh.filesystem.FileInfo;
import tauon.app.ssh.filesystem.FileType;
import tauon.app.ui.components.misc.SkinnedScrollPane;
import tauon.app.ui.containers.session.pages.files.transfer.DndTransferHandler;

import javax.swing.*;
import javax.swing.RowSorter.SortKey;
import javax.swing.event.RowSorterEvent;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class FolderView extends JPanel {
    private static final Logger LOG = LoggerFactory.getLogger(FolderView.class);
    
    private final FolderViewTableModel folderViewModel;
    private final JTable table;
    private final JScrollPane tableScroller;
    private final JScrollPane listScroller;
    private final JList<FileInfo> fileList;
    private final FolderViewEventListener listener;
    private final JPopupMenu popup;
    private final TableRowSorter<? extends Object> sorter;
    private boolean showHiddenFiles = false;
    private List<FileInfo> files;

    public FolderView(FolderViewEventListener listener, Consumer<String> statusCallback) {
        super(new BorderLayout());
        this.listener = listener;
        this.popup = new JPopupMenu();

        showHiddenFiles = SettingsConfigManager.getSettings().isShowHiddenFilesByDefault();


        folderViewModel = new FolderViewTableModel(false);

        FolderViewCellRenderer folderViewCellRenderer = new FolderViewCellRenderer();

        table = new JTable(folderViewModel);
        table.setSelectionForeground(App.skin.getDefaultSelectionForeground());
        table.setDefaultRenderer(FileInfo.class, folderViewCellRenderer);
        table.setDefaultRenderer(Long.class, folderViewCellRenderer);
        table.setDefaultRenderer(LocalDateTime.class, folderViewCellRenderer);
        table.setDefaultRenderer(Object.class, folderViewCellRenderer);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);

        listener.install(this);

        table.setIntercellSpacing(new Dimension(0, 0));
        if (!GraphicsEnvironment.isHeadless()) { // throws HeadlessException otherwise (tests)
            table.setDragEnabled(true);
        }
        table.setDropMode(DropMode.ON);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        final SortOrder[] sortingOrder = {null}; //Store main column sort order

        sorter = new TableRowSorter<>(table.getModel());
        sorter.addRowSorterListener(e -> {
            if (e.getType() == RowSorterEvent.Type.SORT_ORDER_CHANGED) {
                final List<? extends SortKey> sortKeys = e.getSource().getSortKeys();
                if (!sortKeys.isEmpty()) {
                    sortingOrder[0] = sortKeys.get(0).getSortOrder();
                }
            }
        });

        // compare name
        sorter.setComparator(0, new Comparator<>() {
            @Override
            public int compare(Object o1, Object o2) {
                FileInfo fi1 = (FileInfo) o1;
                FileInfo fi2 = (FileInfo) o2;
                //Make sure folders are always before files with respect to current sort order
                if (fi1.isDirectory()) {
                    if (!fi2.isDirectory()) {
                        return sortingOrder[0] == SortOrder.DESCENDING ? 1 : -1;
                    }
                } else {
                    if (fi2.isDirectory()) {
                        return sortingOrder[0] == SortOrder.DESCENDING ? -1 : 1;
                    }
                }

                return fi1.getName().compareToIgnoreCase(fi2.getName());
            }
        });

        // compare size
        sorter.setComparator(2, new Comparator<>() {
            @Override
            public int compare(Object o1, Object o2) {
                FileInfo fi1 = (FileInfo) o1;
                FileInfo fi2 = (FileInfo) o2;
                //Make sure folders are always before files with respect to current sort order
                if (fi1.isDirectory()) {
                    if (!fi2.isDirectory()) {
                        return sortingOrder[0] == SortOrder.DESCENDING ? 1 : -1;
                    }
                } else {
                    if (fi2.isDirectory()) {
                        return sortingOrder[0] == SortOrder.DESCENDING ? -1 : 1;
                    }
                }

                Long s1 = fi1.getSize();
                Long s2 = fi2.getSize();
                return s1.compareTo(s2);
            }
        });

        // compare type
        sorter.setComparator(3, new Comparator<>() {
            @Override
            public int compare(Object o1, Object o2) {
                String s1 = ((FileInfo) o1).getType().toString();
                String s2 = ((FileInfo) o2).getType().toString();
                return s1.compareTo(s2);
            }
        });

        // compare last modified
        sorter.setComparator(1, new Comparator<>() {
            @Override
            public int compare(Object o1, Object o2) {
                FileInfo fi1 = (FileInfo) o1;
                FileInfo fi2 = (FileInfo) o2;
                //Make sure folders are always before files with respect to current sort order
                if (fi1.isDirectory()) {
                    if (!fi2.isDirectory()) {
                        return sortingOrder[0] == SortOrder.DESCENDING ? 1 : -1;
                    }
                } else {
                    if (fi2.isDirectory()) {
                        return sortingOrder[0] == SortOrder.DESCENDING ? -1 : 1;
                    }
                }

                return fi1.getLastModified().compareTo(fi2.getLastModified());
            }
        });

        // compare permission
        sorter.setComparator(4, new Comparator<>() {
            @Override
            public int compare(Object o1, Object o2) {
                FileInfo fi1 = (FileInfo) o1;
                FileInfo fi2 = (FileInfo) o2;
                //Make sure folders are always before files with respect to current sort order
                if (fi1.isDirectory()) {
                    if (!fi2.isDirectory()) {
                        return sortingOrder[0] == SortOrder.DESCENDING ? 1 : -1;
                    }
                } else {
                    if (fi2.isDirectory()) {
                        return sortingOrder[0] == SortOrder.DESCENDING ? -1 : 1;
                    }
                }

                // Local files have no permission string nor owner
                return Objects.toString(fi1.getPermissionString(), "").compareTo(Objects.toString(fi2.getPermissionString(), ""));
            }
        });

        // compare owner
        sorter.setComparator(5, new Comparator<>() {
            @Override
            public int compare(Object o1, Object o2) {
                FileInfo fi1 = (FileInfo) o1;
                FileInfo fi2 = (FileInfo) o2;
                //Make sure folders are always before files with respect to current sort order
                if (fi1.isDirectory()) {
                    if (!fi2.isDirectory()) {
                        return sortingOrder[0] == SortOrder.DESCENDING ? 1 : -1;
                    }
                } else {
                    if (fi2.isDirectory()) {
                        return sortingOrder[0] == SortOrder.DESCENDING ? -1 : 1;
                    }
                }

                return Objects.toString(fi1.getUser(), "").compareTo(Objects.toString(fi2.getUser(), ""));
            }
        });

        table.setRowSorter(sorter);

        this.sort(1, SortOrder.DESCENDING);

        Action openFolderAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                FileInfo[] files = getSelectedFiles();
                if (files.length > 0) {
                    if (files[0].getType() == FileType.DIR || files[0].getType() == FileType.DIR_LINK) {
                        listener.navigate(files[0].getPath());
                    }
                }
            }
        };
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Enter");
        table.getActionMap().put("Enter", openFolderAction);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int rc = table.getSelectedRowCount();
            int tc = table.getRowCount();

            String text = String.format("%d of %d selected", rc, tc);
            statusCallback.accept(text);
        });

        table.addKeyListener(new FolderViewKeyHandler(table, folderViewModel));

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                System.out.println("Mouse click on table");
                if (table.getSelectionModel().getValueIsAdjusting()) {
                    System.out.println("Value adjusting");
                    selectRow(e);
                    return;
                }
                if (e.getClickCount() == 2) {
                    Point p = e.getPoint();
                    int r = table.rowAtPoint(p);
                    int x = table.getSelectedRow();
                    if (x == -1) {
                        return;
                    }
                    if (r == table.getSelectedRow()) {
                        FileInfo fileInfo = folderViewModel.getItemAt(getRow(r));
                        if (fileInfo.getType() == FileType.DIR || fileInfo.getType() == FileType.DIR_LINK) {
                            listener.navigate(fileInfo.getPath());
                        } else {
                            listener.openApp(fileInfo);
                        }
                    }
                } else if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
                    selectRow(e);
                    System.out.println("called");
                    listener.createMenu(popup, getSelectedFiles());
                    popup.pack();
                    popup.show(table, e.getX(), e.getY());
                }
            }
        });

        resizeColumnWidth(table);

        tableScroller = new SkinnedScrollPane(table);
        table.setRowHeight(folderViewCellRenderer.getHeight());

        resizeColumnWidth(table);

        System.out.println("Row height: " + folderViewCellRenderer.getHeight());

        // The list view shows the table's rows in the table's (sorted) order and shares its selection, so both views
        // agree on the order and getSelectedFiles(), menus, Enter and drag and drop work in both
        fileList = new JList<>(new SortedListModel());
        fileList.setSelectionModel(table.getSelectionModel());
        fileList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Enter");
        fileList.getActionMap().put("Enter", openFolderAction);
        if (!GraphicsEnvironment.isHeadless()) {
            fileList.setDragEnabled(true);
        }
        fileList.setDropMode(DropMode.ON);
        fileList.setBackground(App.skin.getTableBackgroundColor());
        fileList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        fileList.setVisibleRowCount(-1);
        fileList.setCellRenderer(new FolderViewListCellRenderer());
        listScroller = new SkinnedScrollPane(fileList);

        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                System.out.println("Mouse click on table " + e.getClickCount() );
                if (fileList.getSelectionModel().getValueIsAdjusting()) {
                    System.out.println("Value adjusting");
                    if(selectListRow(e))
                        return;
                }
                if (e.getClickCount() == 2) {
                    Point p = e.getPoint();
                    int r = fileList.locationToIndex(p);
                    int x = fileList.getSelectedIndex();
                    if (x == -1) {
                        return;
                    }
                    if (r == x) {
                        FileInfo fileInfo = fileList.getModel().getElementAt(r);
                        if (fileInfo.getType() == FileType.DIR || fileInfo.getType() == FileType.DIR_LINK) {
                            listener.navigate(fileInfo.getPath());
                        } else {
                            listener.openApp(fileInfo);
                        }
                    }
                } else if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
                    selectListRow(e);
                    listener.createMenu(popup, getSelectedFiles());
                    popup.pack();
                    popup.show(fileList, e.getX(), e.getY());
                }
            }
        });

        refreshViewMode();
    }

    private void selectRow(MouseEvent e) {
        int r = table.rowAtPoint(e.getPoint());
        System.out.println("Row at point: " + r);
        if (r == -1) {
            table.clearSelection();
        } else {
            if (table.getSelectedRowCount() > 0) {
                int[] rows = table.getSelectedRows();
                for (int row : rows) {
                    if (r == row) {
                        return;
                    }
                }
            }
            table.setRowSelectionInterval(r, r);
        }
    }

    private boolean selectListRow(MouseEvent e) {
        int r = fileList.locationToIndex(e.getPoint());
        System.out.println("Row at point: " + r);
        if (r == -1) {
            fileList.clearSelection();
        } else {
            if (fileList.getSelectedIndices().length > 0) {
                int[] rows = fileList.getSelectedIndices();
                for (int row : rows) {
                    if (r == row) {
                        return false;
                    }
                }
            }
            fileList.setSelectedIndex(r);
        }
        return true;
    }

    public FileInfo[] getSelectedFiles() {
        int[] indexes = table.getSelectedRows();
        FileInfo[] fs = new FileInfo[indexes.length];
        int i = 0;
        for (int index : indexes) {
            FileInfo info = folderViewModel.getItemAt(table.convertRowIndexToModel(index));
            fs[i++] = info;
        }
        return fs;
    }

    public FileInfo[] getFiles() {
        if (this.files == null) {
            return new FileInfo[0];
        } else {
            FileInfo[] fs = new FileInfo[files.size()];
            for (int i = 0; i < files.size(); i++) {
                fs[i] = files.get(i);
            }
            return fs;
        }
    }

    private int getRow(int r) {
        if (r == -1) {
            return -1;
        }
        return table.convertRowIndexToModel(r);
    }

    public void setItems(List<FileInfo> list) {
        setItems(list, false);
    }

    /**
     * Shows {@code list}; the sort order is kept.
     *
     * @param keepSelection the same folder is listed again: reselect the files selected before (by name) and keep the
     *                      scroll position
     */
    public void setItems(List<FileInfo> list, boolean keepSelection) {
        Set<String> selected = new HashSet<>();
        Point tablePosition = tableScroller.getViewport().getViewPosition();
        Point listPosition = listScroller.getViewport().getViewPosition();
        if (keepSelection) {
            for (FileInfo f : getSelectedFiles()) {
                selected.add(f.getName());
            }
        }

        this.files = list;
        applyFilter();

        if (keepSelection) {
            for (int row = 0; row < table.getRowCount(); row++) {
                if (selected.contains(folderViewModel.getItemAt(table.convertRowIndexToModel(row)).getName())) {
                    table.addRowSelectionInterval(row, row);
                }
            }
            // After the new rows are laid out
            SwingUtilities.invokeLater(() -> {
                tableScroller.getViewport().setViewPosition(tablePosition);
                listScroller.getViewport().setViewPosition(listPosition);
            });
        }
    }

    public final void resizeColumnWidth(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        final TableColumnModel columnModel = table.getColumnModel();
        for (int column = 0; column < table.getColumnCount(); column++) {
            TableColumn col = columnModel.getColumn(column);
            if (column == 0) {
                col.setPreferredWidth(200);
            } else if (column == 3) {
                col.setPreferredWidth(120);
            } else {
                col.setPreferredWidth(100);
            }
        }
    }

    public void setFolderViewTransferHandler(DndTransferHandler transferHandler) {
        this.table.setTransferHandler(transferHandler);
        this.fileList.setTransferHandler(transferHandler);
    }

    public void setShowHiddenFiles(boolean showHiddenFiles) {
        this.showHiddenFiles = showHiddenFiles;
        applyFilter();
    }

    private void applyFilter() {
        this.folderViewModel.clear();
        if (this.files == null) {
            return; // not listed yet (or the listing failed)
        }
        if (!this.showHiddenFiles) {
            List<FileInfo> list2 = new ArrayList<>();
            for (FileInfo info : this.files) {
                if (!info.getName().startsWith(".")) {
                    list2.add(info);
                }
            }
            this.folderViewModel.addAll(list2);
        } else {
            this.folderViewModel.addAll(this.files);
        }

    }

    JTable getTable() {
        return table;
    }

    JList<FileInfo> getFileList() {
        return fileList;
    }

    /** The table's rows in view (sorted) order, for the list view. */
    private class SortedListModel extends AbstractListModel<FileInfo> {
        SortedListModel() {
            sorter.addRowSorterListener(e -> fireContentsChanged(this, 0, Math.max(0, getSize() - 1)));
        }

        @Override
        public int getSize() {
            return table.getRowCount();
        }

        @Override
        public FileInfo getElementAt(int index) {
            return folderViewModel.getItemAt(table.convertRowIndexToModel(index));
        }
    }

    public void sort(int index, SortOrder sortOrder) {
        sorter.setSortKeys(List.of(new SortKey(index, sortOrder)));
        sorter.sort();
    }

    public int getSortIndex() {
        for (SortKey sortKey : sorter.getSortKeys()) {
            return sortKey.getColumn();
        }
        return -1;
    }

    public boolean isSortAsc() {
        for (SortKey sortKey : sorter.getSortKeys()) {
            return sortKey.getSortOrder() == SortOrder.ASCENDING;
        }
        return false;
    }

    /**
     * Sets view mode: list or details view
     * <p>
     * Note: caller must call revalidate and repaint after calling this method
     */
    public void refreshViewMode() {
        if (SettingsConfigManager.getSettings().isListViewEnabled()) {
            this.remove(tableScroller);
            this.add(listScroller);
        } else {
            this.remove(listScroller);
            this.add(tableScroller);
        }

        this.revalidate();
        this.repaint(0);
    }
}
