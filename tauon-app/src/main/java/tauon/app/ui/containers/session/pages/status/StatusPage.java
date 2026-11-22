/**
 *
 */
package tauon.app.ui.containers.session.pages.status;

import tauon.app.ui.components.misc.FontAwesomeContants;
import tauon.app.ui.components.page.subpage.SubpagingPage;
import tauon.app.ui.containers.session.SessionContentPanel;
import tauon.app.ui.containers.session.pages.status.portforwarding.PortForwardingStatusPanel;
import tauon.app.ui.containers.session.pages.status.vnc.VncStatusPanel;

import static tauon.app.services.LanguageService.getBundle;

/**
 * @author subhro
 *
 */
public class StatusPage extends SubpagingPage {
    
    public StatusPage(SessionContentPanel holder) {
        super(holder);
    }
    
    @Override
    public String getIcon() {
        return FontAwesomeContants.FA_RECTANGLE_LIST;
    }

    @Override
    public String getText() {
        return getBundle().getString("app.status.title");
    }

    @Override
    public void onCreateSubpages(SessionContentPanel holder) {
        addSubpage(
                "PORT_FORWARDING",
                getBundle().getString("app.status_port_forwarding.title"),
                FontAwesomeContants.FA_ARROW_RIGHT_ARROW_LEFT,
                new PortForwardingStatusPanel(holder)
        );
        addSubpage(
                "VNC",
                getBundle().getString("app.status_vnc.title"),
                FontAwesomeContants.FA_DESKTOP,
                new VncStatusPanel(holder)
        );
    }
    
}
