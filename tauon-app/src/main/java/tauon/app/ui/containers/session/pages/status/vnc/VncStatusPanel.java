package tauon.app.ui.containers.session.pages.status.vnc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tauon.app.ui.components.misc.SkinnedScrollPane;
import tauon.app.ui.components.page.subpage.Subpage;
import tauon.app.ui.components.simpletable.ConnectionStatusValue;
import tauon.app.ui.components.simpletable.SimpleTable;
import tauon.app.ui.containers.session.SessionContentPanel;
import tauon.app.util.misc.FormatUtils;
import tauon.app.util.misc.PlatformUtils;
import tauon.app.vnc.TurboVncLocator;
import tauon.app.vnc.VncSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Map;

import static tauon.app.services.LanguageService.getBundle;

/**
 * Session status: the site's VNC connections. Double-clicking a row opens it with the TurboVNC viewer through an SSH
 * tunnel; the status column follows the viewer process (running or not).
 */
public class VncStatusPanel extends Subpage {

    private static final Logger LOG = LoggerFactory.getLogger(VncStatusPanel.class);

    private SimpleTable<VncSession> table;

    public VncStatusPanel(SessionContentPanel holder) {
        super(holder);
        setBorder(new EmptyBorder(10, 10, 10, 10));
    }

    @Override
    protected void createUI() {
        table = new SimpleTable.Builder<VncSession>()
                .addColumn(getBundle().getString("app.sites.vnc.label.name"), s -> s.getConnection().getName())
                .addColumn(getBundle().getString("app.sites.vnc.label.host"), s -> s.getConnection().getHost())
                .addColumn(getBundle().getString("app.sites.vnc.label.port"), s -> s.getConnection().getPort())
                .addColumn(getBundle().getString("app.status_vnc.label.local_port"), s -> s.getLocalPort() > 0 ? s.getLocalPort() : null)
                .addColumn(getBundle().getString("app.status_vnc.label.viewer"), ConnectionStatusValue.class, VncSession::getStatus)
                .build();
        table.onDoubleClick(this::open);
        add(new SkinnedScrollPane(table));

        JLabel hint = new JLabel(getBundle().getString("app.status_vnc.label.hint"));
        hint.setBorder(new EmptyBorder(10, 0, 0, 0));
        add(hint, BorderLayout.SOUTH);

        for (VncSession s : holder.getSshConnectionHandler().getVncSessions()) {
            table.getEntitiesList().add(s);
            s.addListener(() -> SwingUtilities.invokeLater(() -> onStatusChanged(s)));
        }
    }

    private void open(VncSession session) {
        if (session.isRunning() || session.getStatus() == ConnectionStatusValue.INITIATING) {
            return; // already open: nothing to do
        }
        // Opening the tunnel may connect the session (and ask for passwords): off the event thread
        Thread t = new Thread(() -> {
            try {
                session.start();
            } catch (VncSession.ViewerNotFoundException e) {
                SwingUtilities.invokeLater(this::showViewerNotFound);
            } catch (Exception e) {
                LOG.error("Could not open VNC connection {}", session.getConnection().getDisplayName(), e);
                SwingUtilities.invokeLater(() -> showError(session));
            }
        }, "vnc-open-" + session.getConnection().getDisplayName());
        t.setDaemon(true);
        t.start();
    }

    private void onStatusChanged(VncSession session) {
        table.repaint();
        // A viewer that exited with an error (e.g. wrong password): say why. Start failures are reported by open().
        if (session.getStatus() == ConnectionStatusValue.ERROR && session.getLastError() != null
                && session.getLastError().startsWith("Viewer exited")) {
            showError(session);
        }
    }

    private void showViewerNotFound() {
        String message = FormatUtils.$$(getBundle().getString("app.status_vnc.message.viewer_not_found"),
                Map.of("URL", TurboVncLocator.DOWNLOAD_URL));
        if (JOptionPane.showConfirmDialog(this, message, getBundle().getString("app.status_vnc.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
            PlatformUtils.openWeb(TurboVncLocator.DOWNLOAD_URL);
        }
    }

    private void showError(VncSession session) {
        JOptionPane.showMessageDialog(this,
                getBundle().getString("app.status_vnc.message.failed") + "\n" + session.getLastError(),
                session.getConnection().getDisplayName(), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    protected void onComponentVisible() {
    }

    @Override
    protected void onComponentHide() {
    }
}
