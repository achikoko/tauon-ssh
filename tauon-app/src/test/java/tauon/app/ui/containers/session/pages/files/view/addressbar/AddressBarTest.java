package tauon.app.ui.containers.session.pages.files.view.addressbar;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import tauon.app.App;
import tauon.app.ui.laf.AppSkinLight;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class AddressBarTest {

    private AddressBar bar;
    private final List<String> navigated = new ArrayList<>();

    @BeforeClass
    public static void installSkin() throws Exception {
        App.skin = new AppSkinLight();
        UIManager.setLookAndFeel(App.skin.getLaf());
    }

    @Before
    public void setUp() {
        bar = new AddressBar('/', null);
        bar.addActionListener(e -> navigated.add(e.getActionCommand()));
    }

    private static void click(JComponent c, int id, Point p) {
        c.dispatchEvent(new MouseEvent(c, id, System.currentTimeMillis(), 0, p.x, p.y, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    public void visitedFoldersFillTheDropdownMostRecentFirst() {
        bar.setText("/a");
        bar.setText("/b");
        bar.setText("/a");
        assertEquals(List.of("/a", "/b"), bar.getHistory());
        assertTrue("setText never navigates", navigated.isEmpty());

        for (int i = 0; i < AddressBar.HISTORY_SIZE + 5; i++) {
            bar.setText("/d" + i);
        }
        assertEquals(AddressBar.HISTORY_SIZE, bar.getHistory().size());
        assertEquals("/d" + (AddressBar.HISTORY_SIZE + 4), bar.getHistory().get(0));
    }

    @Test
    public void clickingAFolderInTheDropdownNavigatesToIt() {
        bar.switchToText();
        bar.setText("/a");
        bar.setText("/b"); // dropdown: /b, /a

        JList<?> list = ((ComboPopup) bar.getComboBox().getUI().getAccessibleChild(bar.getComboBox(), 0)).getList();
        Rectangle cell = list.getCellBounds(1, 1);
        assertNotNull(cell);
        // Headless, the popup list's own event processing needs the toolkit: hand the release to AddressBar's listener
        MouseEvent release = new MouseEvent(list, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                cell.x + 2, cell.y + 2, 1, false, MouseEvent.BUTTON1);
        int called = 0;
        for (java.awt.event.MouseListener l : list.getMouseListeners()) {
            if (l.getClass().getName().startsWith(AddressBar.class.getName())) {
                l.mouseReleased(release);
                called++;
            }
        }
        assertEquals("AddressBar listens to the dropdown list", 1, called);

        assertEquals(List.of("/a"), navigated);
    }

    @Test
    public void getTextInPathBarModeIsTheFolderShown() {
        bar.switchToPathBar();
        bar.setText("/home/user");
        assertEquals("/home/user", bar.getText());
    }

    private static String clickFirstCrumb(boolean unix, String path) {
        List<String> clicked = new ArrayList<>();
        AddressBarBreadCrumbs crumbs = new AddressBarBreadCrumbs(unix, null);
        crumbs.addActionListener(e -> clicked.add(e.getActionCommand()));
        crumbs.setPath(path);
        JComponent first = (JComponent) crumbs.getComponent(0);
        click(first, MouseEvent.MOUSE_CLICKED, new Point(1, 1));
        assertEquals(1, clicked.size());
        return clicked.get(0);
    }

    @Test
    public void windowsDriveCrumbGoesToTheDriveRoot() {
        assertEquals("C:\\", clickFirstCrumb(false, "C:\\Users\\me"));
    }

    @Test
    public void unixFirstCrumb() {
        assertEquals("/home", clickFirstCrumb(true, "/home/user"));
    }
}
