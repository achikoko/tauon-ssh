package tauon.app.ui.containers.session.pages.files.view.addressbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tauon.app.App;
import tauon.app.ui.components.misc.SkinnedTextField;
import tauon.app.util.externaleditor.ExternalEditorHandler;
import tauon.app.util.misc.LayoutUtilities;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class AddressBar extends JPanel {
    
    private static final Logger LOG = LoggerFactory.getLogger(AddressBar.class);
    
    private final AddressBarBreadCrumbs addressBar;
    private final JComboBox<String> txtAddressBar;
    private final JButton btnEdit;
    private final JPanel addrPanel;
    private AtomicBoolean updating = new AtomicBoolean();
    private ActionListener a;
    private JPopupMenu popup;
    private final char separator;
    private final JPanel panBtn2;
    /** Folder shown, as last set by {@link #setText}. */
    private String currentPath;
    /** Most recent folders kept in the dropdown. */
    static final int HISTORY_SIZE = 20;

    public AddressBar(char separator, ActionListener popupTriggeredListener) {
        setLayout(new BorderLayout());
        addrPanel = new JPanel(new BorderLayout());
        addrPanel.setBorder(new EmptyBorder(3, 3, 3, 3));
        this.separator = separator;

        UIDefaults toolbarSkin = App.skin.createToolbarSkin();

        JButton btnRoot = new JButton();
        btnRoot.putClientProperty("Nimbus.Overrides", toolbarSkin);
        btnRoot.setFont(App.skin.getIconFont());
        btnRoot.setText("\uf0a0");
        btnRoot.addActionListener(e -> createAndShowPopup());

        DefaultComboBoxModel<String> model1 = new DefaultComboBoxModel<>();
        txtAddressBar = new JComboBox<>(model1);
        txtAddressBar.setEditor(new AddressBarComboBoxEditor());
        txtAddressBar.putClientProperty("paintNoBorder", "True");

        txtAddressBar.addActionListener(e -> {
            if (updating.get()) {
                return;
            }
            
            LOG.debug("Calling action listener. Command: {}", e.getActionCommand());
            // Enter in the editor (also after choosing an item with the arrow keys). A mouse pick in the dropdown
            // only fires "comboBoxChanged": see the list listener below
            if (e.getActionCommand().equals("comboBoxEdited")) {
                fireNavigation((String) txtAddressBar.getSelectedItem());
            }
        });
        // The dropdown list belongs to the combo's popup
        Object popup = txtAddressBar.getUI().getAccessibleChild(txtAddressBar, 0);
        if (popup instanceof ComboPopup) {
            JList<?> list = ((ComboPopup) popup).getList();
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    int index = list.locationToIndex(e.getPoint());
                    if (SwingUtilities.isLeftMouseButton(e) && index >= 0) {
                        fireNavigation(txtAddressBar.getItemAt(index));
                    }
                }
            });
        }
        
        txtAddressBar.setEditable(true);
        ComboBoxEditor cmdEdit = new BasicComboBoxEditor() {
            @Override
            protected JTextField createEditorComponent() {
                JTextField textField = new SkinnedTextField(10);
                textField.setBorder(new LineBorder(Color.black, 0));
                textField.setName("ComboBox.textField");
                return textField;
            }
        };
        txtAddressBar.setEditor(cmdEdit);

        addressBar = new AddressBarBreadCrumbs(separator == '/', popupTriggeredListener);
        addressBar.addActionListener(e -> {
            if (a != null) {
                a.actionPerformed(new ActionEvent(this, 0, e.getActionCommand()));
            }
        });

        panBtn2 = new JPanel(new BorderLayout());
        panBtn2.setBorder(new EmptyBorder(3, 3, 3, 3));

        btnEdit = new JButton();
        btnEdit.putClientProperty("Nimbus.Overrides", toolbarSkin);
        btnEdit.setFont(App.skin.getIconFont());
        btnEdit.setText("\uf023");
        btnEdit.addActionListener(e -> {
            if (!isSelected()) {
                switchToText();
            } else {
                switchToPathBar();
            }
            revalidate();
            repaint();
        });
        LayoutUtilities.equalizeSize(btnRoot, btnEdit);

        panBtn2.add(btnRoot);

        addrPanel.add(addressBar);
        add(addrPanel);
        JPanel panBtn = new JPanel(new BorderLayout());
        panBtn.setBorder(new EmptyBorder(3, 3, 3, 3));
        panBtn.add(btnEdit);
        add(panBtn, BorderLayout.EAST);
        add(panBtn2, BorderLayout.WEST);
        btnEdit.putClientProperty("toggle.selected", Boolean.FALSE);
    }

    public void switchToPathBar() {
        add(panBtn2, BorderLayout.WEST);
        addrPanel.remove(txtAddressBar);
        addrPanel.add(addressBar);
        btnEdit.setIcon(UIManager.getIcon("AddressBar.edit"));
        btnEdit.putClientProperty("toggle.selected", Boolean.FALSE);
        btnEdit.setText("\uf023");
    }

    public void switchToText() {
        addrPanel.remove(addressBar);
        addrPanel.add(txtAddressBar);
        remove(panBtn2);
        btnEdit.setIcon(UIManager.getIcon("AddressBar.toggle"));
        btnEdit.putClientProperty("toggle.selected", Boolean.TRUE);
        txtAddressBar.getEditor().selectAll();
        btnEdit.setText("\uf13e");
    }

    private void fireNavigation(String path) {
        if (a != null && path != null) {
            a.actionPerformed(new ActionEvent(this, 0, path));
        }
    }

    /** @return the text being edited in text mode, the folder shown in path-bar mode */
    public String getText() {
        return isSelected() ? (String) txtAddressBar.getEditor().getItem() : currentPath;
    }

    /** Shows {@code text}, the folder just listed, and puts it first in the dropdown history. */
    public void setText(String text) {
        LOG.debug("Setting text: {}", text);
        updating.set(true);
        try {
            currentPath = text;
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) txtAddressBar.getModel();
            model.removeElement(text);
            model.insertElementAt(text, 0);
            while (model.getSize() > HISTORY_SIZE) {
                model.removeElementAt(model.getSize() - 1);
            }
            txtAddressBar.setSelectedItem(text);
            addressBar.setPath(text);
        } finally {
            updating.set(false);
        }
    }

    /** @return the dropdown history, most recent first */
    java.util.List<String> getHistory() {
        java.util.List<String> items = new java.util.ArrayList<>();
        for (int i = 0; i < txtAddressBar.getItemCount(); i++) {
            items.add(txtAddressBar.getItemAt(i));
        }
        return items;
    }

    JComboBox<String> getComboBox() {
        return txtAddressBar;
    }

    AddressBarBreadCrumbs getBreadCrumbs() {
        return addressBar;
    }

    public void addActionListener(ActionListener e) {
        this.a = e;
    }

    private boolean isSelected() {
        return btnEdit.getClientProperty("toggle.selected") == Boolean.TRUE;
    }

    private void createAndShowPopup() {
        if (popup == null) {
            popup = new JPopupMenu();
        } else {
            popup.removeAll();
        }

        String itemPath = "item.path";
        if (separator == '/') {
            JMenuItem item = new JMenuItem("ROOT");
            item.putClientProperty(itemPath, "/");
            item.addActionListener(e -> {
                String selectedText = (String) item.getClientProperty(itemPath);
                if (a != null) {
                    a.actionPerformed(new ActionEvent(this, 0, selectedText));
                }
            });
            popup.add(item);
        } else {
            File[] roots = File.listRoots();
            for (File f : roots) {
                JMenuItem item = new JMenuItem(f.getAbsolutePath());
                item.putClientProperty(itemPath, f.getAbsolutePath());
                item.addActionListener(e -> {
                    String selectedText = (String) item.getClientProperty(itemPath);
                    if (a != null) {
                        a.actionPerformed(new ActionEvent(this, 0, selectedText));
                    }
                });
                popup.add(item);
            }
        }

        popup.show(this, 0, getHeight());
    }
}
