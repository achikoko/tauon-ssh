package tauon.app.ui.dialogs.sessions;

import tauon.app.App;
import tauon.app.settings.SiteInfo;
import tauon.app.settings.VncConnection;
import tauon.app.ui.components.misc.FontAwesomeContants;
import tauon.app.ui.components.misc.SkinnedScrollPane;
import tauon.app.ui.components.misc.SkinnedTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static tauon.app.services.LanguageService.getBundle;

/**
 * Site editor tab: the site's VNC connections, opened from the session's status page with the TurboVNC viewer through
 * an SSH tunnel. Passwords are kept in the connections but saved encrypted, apart from the sites file.
 */
public class VncPanel extends JPanel {
    private final VncTableModel model;
    private final JTable table;
    private SiteInfo info;

    public VncPanel() {
        super(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 0, 0, 10));
        model = new VncTableModel();
        table = new JTable(model);

        JLabel lblTitle = new JLabel(getBundle().getString("app.sites.vnc.label.connections"));

        JScrollPane scrollPane = new SkinnedScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(400, 200));

        Box b1 = Box.createVerticalBox();
        JButton btnAdd = new JButton(FontAwesomeContants.FA_PLUS);
        btnAdd.setFont(App.skin.getIconFont());
        JButton btnDel = new JButton(FontAwesomeContants.FA_MINUS);
        btnDel.setFont(App.skin.getIconFont());
        JButton btnEdit = new JButton(FontAwesomeContants.FA_PENCIL);
        btnEdit.setFont(App.skin.getIconFont());

        btnAdd.addActionListener(e -> {
            VncConnection c = addOrEditEntry(null);
            if (c != null) {
                model.add(c);
                updateConnections();
            }
        });

        btnEdit.addActionListener(e -> {
            int index = table.getSelectedRow();
            if (index != -1) {
                if (addOrEditEntry(model.get(index)) != null) {
                    model.refreshTable();
                    updateConnections();
                }
            }
        });

        btnDel.addActionListener(e -> {
            int index = table.getSelectedRow();
            if (index != -1) {
                model.remove(index);
                updateConnections();
            }
        });

        b1.add(btnAdd);
        b1.add(Box.createVerticalStrut(10));
        b1.add(btnEdit);
        b1.add(Box.createVerticalStrut(10));
        b1.add(btnDel);
        b1.add(Box.createVerticalStrut(10));

        this.add(lblTitle, BorderLayout.NORTH);
        this.add(scrollPane);
        this.add(b1, BorderLayout.EAST);
    }

    public void setInfo(SiteInfo info) {
        this.info = info;
        model.setConnections(info.getVncConnections());
    }

    private void updateConnections() {
        this.info.setVncConnections(model.getConnections());
    }

    private VncConnection addOrEditEntry(VncConnection c) {
        JTextField txtName = new SkinnedTextField(30);
        JTextField txtHost = new SkinnedTextField(30);
        txtHost.setText("localhost");
        JSpinner spPort = new JSpinner(new SpinnerNumberModel(VncConnection.DISPLAY_BASE_PORT + 1, 1, SessionInfoPanel.DEFAULT_MAX_PORT, 1));
        JSpinner spDisplay = new JSpinner(new SpinnerNumberModel(1, 0, SessionInfoPanel.DEFAULT_MAX_PORT - VncConnection.DISPLAY_BASE_PORT, 1));
        JPasswordField txtPassword = new JPasswordField(30);

        // Display N is port 5900 + N: editing one updates the other
        boolean[] syncing = {false};
        spDisplay.addChangeListener(e -> {
            if (!syncing[0]) {
                syncing[0] = true;
                spPort.setValue(VncConnection.DISPLAY_BASE_PORT + (Integer) spDisplay.getValue());
                syncing[0] = false;
            }
        });
        spPort.addChangeListener(e -> {
            if (!syncing[0]) {
                syncing[0] = true;
                int port = (Integer) spPort.getValue();
                if (port >= VncConnection.DISPLAY_BASE_PORT) {
                    spDisplay.setValue(port - VncConnection.DISPLAY_BASE_PORT);
                }
                syncing[0] = false;
            }
        });

        if (c != null) {
            txtName.setText(c.getName());
            txtHost.setText(c.getHost());
            spPort.setValue(c.getPort());
            if (c.getPassword() != null) {
                txtPassword.setText(c.getPassword());
            }
        }

        while (JOptionPane.showOptionDialog(this,
                new Object[]{
                        getBundle().getString("app.sites.vnc.label.name"), txtName,
                        getBundle().getString("app.sites.vnc.label.host"), txtHost,
                        getBundle().getString("app.sites.vnc.label.display"), spDisplay,
                        getBundle().getString("app.sites.vnc.label.port"), spPort,
                        getBundle().getString("app.sites.vnc.label.password"), txtPassword,
                },
                getBundle().getString("app.sites.vnc.label.connection"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null,
                null) == JOptionPane.OK_OPTION) {

            String host = txtHost.getText().trim();
            int port = (Integer) spPort.getValue();
            if (host.isEmpty() || port <= 0) {
                JOptionPane.showMessageDialog(this, getBundle().getString("app.sites.label.invalid_input"));
                continue;
            }

            if (c == null) {
                c = new VncConnection();
            }
            c.setName(txtName.getText().trim());
            c.setHost(host);
            c.setPort(port);
            char[] password = txtPassword.getPassword();
            c.setPassword(password.length == 0 ? null : new String(password));
            java.util.Arrays.fill(password, '\0');
            return c;
        }
        return null;
    }

    private static class VncTableModel extends AbstractTableModel {

        private final String[] columns = {
                getBundle().getString("app.sites.vnc.label.name"),
                getBundle().getString("app.sites.vnc.label.host"),
                getBundle().getString("app.sites.vnc.label.port"),
                getBundle().getString("app.sites.vnc.label.display"),
                getBundle().getString("app.sites.vnc.label.has_password"),
        };

        private final List<VncConnection> list = new ArrayList<>();

        @Override
        public int getRowCount() {
            return list.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            VncConnection c = list.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return c.getName();
                case 1:
                    return c.getHost();
                case 2:
                    return c.getPort();
                case 3:
                    return c.getDisplay() >= 0 ? ":" + c.getDisplay() : "";
                case 4:
                    return c.getPassword() != null && !c.getPassword().isEmpty();
                default:
                    return "";
            }
        }

        private List<VncConnection> getConnections() {
            return list;
        }

        private void setConnections(List<VncConnection> connections) {
            list.clear();
            if (connections != null) {
                list.addAll(connections);
            }
            fireTableDataChanged();
        }

        private void add(VncConnection c) {
            list.add(c);
            fireTableDataChanged();
        }

        private void refreshTable() {
            fireTableDataChanged();
        }

        private void remove(int index) {
            list.remove(index);
            fireTableDataChanged();
        }

        private VncConnection get(int index) {
            return list.get(index);
        }
    }
}
