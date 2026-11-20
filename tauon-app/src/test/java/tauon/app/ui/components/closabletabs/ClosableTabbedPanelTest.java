package tauon.app.ui.components.closabletabs;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import tauon.app.App;
import tauon.app.ui.laf.AppSkinLight;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Drives the panel with the same mouse events a user's drag produces (press, drags, release on a tab), headless.
 */
public class ClosableTabbedPanelTest {

    private static final int W = 800;
    private static final int H = 600;

    private ClosableTabbedPanel panel;
    private final List<Location> newTabLocations = new ArrayList<>();
    private final List<JButton> newTabButtons = new ArrayList<>();
    private final JPanel c1 = content("c1");
    private final JPanel c2 = content("c2");
    private final JPanel c3 = content("c3");
    private TabHandle t1;
    private TabHandle t2;
    private TabHandle t3;

    @BeforeClass
    public static void skin() throws UnsupportedLookAndFeelException {
        App.skin = new AppSkinLight();
        UIManager.setLookAndFeel(App.skin.getLaf()); // as App does: the skin's getters read UIManager
    }

    @Before
    public void setUp() {
        panel = new ClosableTabbedPanel((button, location) -> {
            newTabButtons.add(button);
            newTabLocations.add(location);
        });
        panel.getButtonsBox().add(new JButton("extra"));
        t1 = panel.addTab(c1, null);
        t1.setTitle("one");
        t2 = panel.addTab(c2, null);
        t2.setTitle("two");
        t3 = panel.addTab(c3, null);
        t3.setTitle("three");
        t3.setClosable(() -> true);
        layout();
    }

    @Test
    public void tabsAreAddedToTheEndAndSelected() {
        assertEquals(Arrays.asList(c1, c2, c3), contents());
        assertSame(c3, panel.getSelectedContent());
        assertEquals(1, groups().size());
    }

    @Test
    public void clickSelectsATab() {
        click(t1);
        assertSame(c1, panel.getSelectedContent());
    }

    @Test
    public void tinyMovementIsStillAClick() {
        Component title = title(t1);
        Point p = center(title);
        dispatch(title, MouseEvent.MOUSE_PRESSED, p);
        dispatch(title, MouseEvent.MOUSE_DRAGGED, new Point(p.x + 2, p.y));
        dispatch(title, MouseEvent.MOUSE_RELEASED, new Point(p.x + 2, p.y));
        assertSame(c1, panel.getSelectedContent());
        assertEquals(Arrays.asList(c1, c2, c3), contents());
    }

    @Test
    public void dragAlongTheStripReorders() {
        Rectangle first = inPanel(title(t1));
        drag(t3, new Point(first.x + 3, first.y + first.height / 2));
        assertEquals(Arrays.asList(c3, c1, c2), contents());
        assertSame(c3, panel.getSelectedContent());
        assertEquals(1, groups().size());

        Rectangle last = inPanel(title(t2));
        drag(t3, new Point(last.x + last.width - 3, last.y + last.height / 2));
        assertEquals(Arrays.asList(c1, c2, c3), contents());
    }

    @Test
    public void escapeCancelsTheDrag() {
        Rectangle content = inPanel(groupOf(c1).cardPanel);
        Point rightEdge = new Point(content.x + content.width - 10, content.y + content.height / 2);
        Component title = title(t1);
        pressAndDragTo(title, rightEdge);

        // Esc goes to whatever has the focus: here, a content of another tab.
        c2.dispatchEvent(new KeyEvent(c2, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        BufferedImage img = paint();
        int halfX = content.x + content.width - content.width / 2;
        assertNotEquals("no shade after Esc", App.skin.getDefaultSelectionBackground().getRGB(), img.getRGB(halfX + 1, content.y + content.height / 2));

        // The rest of the gesture does nothing.
        dispatch(title, MouseEvent.MOUSE_DRAGGED, toLocal(title, rightEdge));
        dispatch(title, MouseEvent.MOUSE_RELEASED, toLocal(title, rightEdge));
        layout();
        assertEquals(1, groups().size());
        assertEquals(Arrays.asList(c1, c2, c3), contents());
        assertSame("a cancelled drag is not a click either", c3, panel.getSelectedContent());
    }

    @Test
    public void dragToAnEdgeSplitsAndShadesThatHalf() {
        Rectangle content = inPanel(groupOf(c1).cardPanel);
        Point rightEdge = new Point(content.x + content.width - 10, content.y + content.height / 2);

        // Mid-drag the overlay shades the right half of the content.
        Component title = title(t1);
        pressAndDragTo(title, rightEdge);
        BufferedImage img = paint();
        Color accent = App.skin.getDefaultSelectionBackground();
        int halfX = content.x + content.width - content.width / 2;
        assertEquals("border of the shaded half", accent.getRGB(), img.getRGB(halfX + 1, content.y + content.height / 2));
        dispatch(title, MouseEvent.MOUSE_RELEASED, toLocal(title, rightEdge));
        layout();

        JSplitPane split = onlySplit();
        assertEquals(JSplitPane.HORIZONTAL_SPLIT, split.getOrientation());
        assertEquals(2, groups().size());
        assertSame("the dragged tab gets the right half", groupOf(c1), split.getRightComponent());
        assertSame(groupOf(c2), split.getLeftComponent());
        assertSame(groupOf(c3), split.getLeftComponent());
        assertSame("the dropped tab's group becomes active", c1, panel.getSelectedContent());
        assertTrue("split in the middle", Math.abs(split.getDividerLocation() - (W - 5) / 2) <= 1);

        // The buttons box (with the extra button) moves to the top-right group; the left one keeps its own "+".
        assertTrue(SwingUtilities.isDescendingFrom(panel.getButtonsBox(), groupOf(c1)));
        assertSame(groupOf(c1).newTabButton, panel.getButtonsBox().getComponent(0));
        assertSame(groupOf(c2).ownButtons, groupOf(c2).newTabButton.getParent());
    }

    @Test
    public void newTabButtonGivesItsGroupAsLocation() {
        splitRight(t1);
        ClosableTabbedPanel.TabGroup left = groupOf(c2);

        left.newTabButton.doClick();
        assertSame(left.newTabButton, newTabButtons.get(0));
        JPanel c4 = content("c4");
        panel.addTab(c4, newTabLocations.get(0));
        assertSame(left, groupOf(c4));
        assertSame(c4, panel.getSelectedContent());
        assertEquals(Arrays.asList(c2, c3, c4, c1), contents());

        // null: the active group (the left one, where the last tab went)
        JPanel c5 = content("c5");
        panel.addTab(c5, null);
        assertSame(left, groupOf(c5));
    }

    @Test
    public void movingTheLastTabAwayCollapsesTheSplit() {
        splitRight(t1);
        Rectangle strip = inPanel(groupOf(c2).strip);
        drag(t1, new Point(strip.x + 5, strip.y + strip.height / 2));

        assertTrue(splits().isEmpty());
        assertEquals(1, groups().size());
        assertEquals(Arrays.asList(c1, c2, c3), contents());
        assertTrue(SwingUtilities.isDescendingFrom(panel.getButtonsBox(), groupOf(c1)));
    }

    @Test
    public void dropIntoAnotherGroupsContentJoinsIt() {
        splitRight(t1);
        Rectangle content = inPanel(groupOf(c1).cardPanel);
        drag(t2, new Point(content.x + content.width / 2, content.y + content.height / 2));
        assertSame(groupOf(c1), groupOf(c2));
        assertEquals(Arrays.asList(c3, c1, c2), contents());
        assertEquals(2, groups().size());
    }

    @Test
    public void splitBottomThenCloseCollapses() {
        Rectangle content = inPanel(groupOf(c3).cardPanel);
        drag(t3, new Point(content.x + content.width / 2, content.y + content.height - 5));
        JSplitPane split = onlySplit();
        assertEquals(JSplitPane.VERTICAL_SPLIT, split.getOrientation());
        assertSame(groupOf(c3), split.getBottomComponent());

        panel.close(t3);
        layout();
        assertTrue(splits().isEmpty());
        assertEquals(Arrays.asList(c1, c2), contents());
    }

    @Test
    public void aLoneTabDroppedOnItsOwnContentStays() {
        splitRight(t1);
        Rectangle content = inPanel(groupOf(c1).cardPanel);
        drag(t1, new Point(content.x + content.width - 5, content.y + content.height / 2));
        assertEquals(2, groups().size());
        assertEquals(Arrays.asList(c2, c3, c1), contents());
    }

    @Test
    public void aLocationOfAGoneGroupFallsBackToTheActiveOne() {
        splitRight(t1);
        groupOf(c1).newTabButton.doClick();
        Location stale = newTabLocations.get(0);
        Rectangle strip = inPanel(groupOf(c2).strip);
        drag(t1, new Point(strip.x + 5, strip.y + strip.height / 2)); // the right group is gone

        JPanel c4 = content("c4");
        panel.addTab(c4, stale);
        assertSame(groupOf(c1), groupOf(c4));
    }

    @Test
    public void nestedSplitsKeepButtonsOnTheTopRightGroup() {
        splitRight(t1);
        Rectangle content = inPanel(groupOf(c1).cardPanel);
        drag(t1, new Point(content.x + content.width / 2, content.y + content.height - 5)); // alone: stays
        assertEquals(2, groups().size());

        Rectangle leftContent = inPanel(groupOf(c2).cardPanel);
        drag(t2, new Point(leftContent.x + leftContent.width / 2, leftContent.y + 5)); // left group: c2 on top
        assertEquals(3, groups().size());
        assertTrue(SwingUtilities.isDescendingFrom(panel.getButtonsBox(), groupOf(c1)));
        assertEquals(Arrays.asList(c2, c3, c1), contents());
    }

    @Test
    public void besideSplitsEvenBeforeTheFirstLayout() throws Exception {
        // As the file browser opens: remote, then local beside it, before the panel has a size.
        ClosableTabbedPanel fresh = new ClosableTabbedPanel((b, l) -> {});
        JPanel remote = content("remote");
        JPanel local = content("local");
        TabHandle remoteTab = fresh.addTab(remote, null);
        fresh.addTab(local, fresh.beside(remoteTab, Location.Side.RIGHT));

        List<JSplitPane> s = new ArrayList<>();
        collect(fresh, JSplitPane.class, s);
        assertEquals(1, s.size());
        assertSame(groupOf(remote), s.get(0).getLeftComponent());
        assertSame(groupOf(local), s.get(0).getRightComponent());
        assertSame(local, fresh.getSelectedContent());

        fresh.setSize(W, H);
        layout(fresh);
        SwingUtilities.invokeAndWait(() -> { }); // deliver the resize events
        layout(fresh);
        assertTrue("divider in the middle once sized: " + s.get(0).getDividerLocation(),
                Math.abs(s.get(0).getDividerLocation() - (W - 5) / 2) <= 1);
    }

    @Test
    public void locationOfOpensNextToATab() {
        splitRight(t1);
        JPanel c4 = content("c4");
        panel.addTab(c4, panel.locationOf(t2));
        assertSame(groupOf(c2), groupOf(c4));

        panel.close(t1);
        layout();
        assertNull("a closed tab has no location", panel.locationOf(t1));
        assertNull(panel.beside(t1, Location.Side.LEFT));
    }

    @Test
    public void selectedContentsAreEachGroupsShownTab() {
        splitRight(t1);
        assertEquals(Arrays.asList(c3, c1), Arrays.asList(panel.getSelectedContents()));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private void splitRight(TabHandle tab) {
        Rectangle content = inPanel(groupOf(c1).cardPanel);
        drag(tab, new Point(content.x + content.width - 10, content.y + content.height / 2));
        assertEquals(2, groups().size());
    }

    private static JPanel content(String name) {
        JPanel p = new JPanel();
        p.setName(name);
        return p;
    }

    private List<Component> contents() {
        return Arrays.asList(panel.getTabContents());
    }

    private static ClosableTabbedPanel.TabGroup groupOf(Component content) {
        for (Component c = content; c != null; c = c.getParent()) {
            if (c instanceof ClosableTabbedPanel.TabGroup) {
                return (ClosableTabbedPanel.TabGroup) c;
            }
        }
        return null;
    }

    private List<ClosableTabbedPanel.TabGroup> groups() {
        List<ClosableTabbedPanel.TabGroup> list = new ArrayList<>();
        for (Component c : contents()) {
            ClosableTabbedPanel.TabGroup g = groupOf(c);
            if (!list.contains(g)) {
                list.add(g);
            }
        }
        return list;
    }

    private List<JSplitPane> splits() {
        List<JSplitPane> list = new ArrayList<>();
        collect(panel, JSplitPane.class, list);
        return list;
    }

    private JSplitPane onlySplit() {
        List<JSplitPane> s = splits();
        assertEquals(1, s.size());
        return s.get(0);
    }

    private static <T> void collect(Component c, Class<T> type, List<T> out) {
        if (type.isInstance(c)) {
            out.add(type.cast(c));
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collect(child, type, out);
            }
        }
    }

    private static Component title(TabHandle handle) {
        return (Component) handle;
    }

    private void layout() {
        panel.setSize(W, H);
        layout(panel);
    }

    private static void layout(Component c) {
        if (c instanceof Container) {
            ((Container) c).doLayout();
            for (Component child : ((Container) c).getComponents()) {
                layout(child);
            }
        }
    }

    private Rectangle inPanel(Component c) {
        return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), panel);
    }

    private Point toLocal(Component c, Point inPanel) {
        return SwingUtilities.convertPoint(panel, inPanel, c);
    }

    private static Point center(Component c) {
        return new Point(c.getWidth() / 2, c.getHeight() / 2);
    }

    private static void dispatch(Component c, int id, Point p) {
        int mods = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        c.dispatchEvent(new MouseEvent(c, id, System.currentTimeMillis(), mods, p.x, p.y, 1, false, MouseEvent.BUTTON1));
    }

    private void click(TabHandle tab) {
        Component title = title(tab);
        dispatch(title, MouseEvent.MOUSE_PRESSED, center(title));
        dispatch(title, MouseEvent.MOUSE_RELEASED, center(title));
    }

    private void pressAndDragTo(Component title, Point targetInPanel) {
        Point start = center(title);
        dispatch(title, MouseEvent.MOUSE_PRESSED, start);
        Point from = SwingUtilities.convertPoint(title, start, panel);
        for (int i = 1; i <= 5; i++) {
            Point p = new Point(from.x + (targetInPanel.x - from.x) * i / 5, from.y + (targetInPanel.y - from.y) * i / 5);
            dispatch(title, MouseEvent.MOUSE_DRAGGED, toLocal(title, p));
        }
    }

    private void drag(TabHandle tab, Point targetInPanel) {
        Component title = title(tab);
        pressAndDragTo(title, targetInPanel);
        paint(); // the overlay paints whatever the drag is over
        dispatch(title, MouseEvent.MOUSE_RELEASED, toLocal(title, targetInPanel));
        layout();
    }

    private BufferedImage paint() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        panel.paint(g);
        g.dispose();
        return img;
    }
}
