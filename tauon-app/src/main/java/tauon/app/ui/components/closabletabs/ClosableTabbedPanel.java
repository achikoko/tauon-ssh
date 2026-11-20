package tauon.app.ui.components.closabletabs;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tauon.app.App;
import tauon.app.ui.components.misc.FontAwesomeContants;
import tauon.app.ui.components.misc.SkinnedSplitPane;
import tauon.app.util.misc.Constants;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import tauon.app.ui.components.closabletabs.Location.Side;

/**
 * Tabs with a close button, which can be reordered and split by dragging them with the mouse (IntelliJ's split editors).
 * <p>
 * The panel is a tree of {@link TabGroup}s (a strip of tabs over the selected tab's content) joined by split panes.
 * A tab dragged past a few pixels moves: along a strip or onto another one (a line marks where it lands), into another
 * group's content (all of it shaded: it joins that group), or near a content's edge (that half shaded: the group is split
 * and the tab gets the new half). A group left without tabs is removed and its split collapses.
 * <p>
 * Each group has its own "+" button; {@link #getButtonsBox()} (with that button and the caller's extra buttons) sits on
 * the top-right group. The active group is the one last clicked or holding the keyboard focus:
 * {@link #getSelectedContent()} and new tabs without a {@link Location} refer to it.
 */
public class ClosableTabbedPanel extends JPanel {
    private static final Logger LOG = LoggerFactory.getLogger(ClosableTabbedPanel.class);

    /** How far the mouse moves before a press on a tab becomes a drag. */
    private static final int DRAG_SLOP = 3;
    /** Within this fraction of a content's width (or height) from an edge, a drop splits the group. */
    private static final double EDGE_ZONE = 0.25;
    /** The least a group's content keeps, across and down, for a split to be offered. */
    private static final int MIN_GROUP = 80;
    private static final int DIVIDER_SIZE = 5;

    /** Called when a group's "+" button is clicked. */
    @FunctionalInterface
    public interface NewTabCallback {
        /**
         * @param button   the "+" button clicked (e.g. to show a popup under it)
         * @param location where the new tab goes: pass it to {@link #addTab(Component, Location)}
         */
        void onNewTab(JButton button, Location location);
    }

    private final Color unselectedBg = App.skin.getSelectedTabColor();
    private final Color selectedBg = App.skin.getDefaultBackground();

    private final NewTabCallback newTabCallback;
    /** Holds the root of the tree: a group or a split pane. */
    private final JPanel treeHolder;
    private final DropOverlay overlay;
    /** The top-right group's "+" button followed by the caller's extra buttons. */
    private final JPanel buttonsBox;
    private final PropertyChangeListener focusListener = e -> onFocusChanged((Component) e.getNewValue());
    /** While a tab is dragged: Esc cancels the drag, whichever component has the focus. */
    private final KeyEventDispatcher escapeCancelsDrag = this::onKeyWhileDragging;

    private TabGroup activeGroup;
    private TabGroup buttonsBoxGroup;
    private Drag drag;
    private int groupCounter;

    /**
     * Create a tabbed pane with closable, draggable and splittable tabs
     *
     * @param newTabCallback Called whenever a group's new tab button is clicked
     */
    public ClosableTabbedPanel(final NewTabCallback newTabCallback) {
        super(null, true);
        this.newTabCallback = newTabCallback;
        setOpaque(true);

        buttonsBox = new JPanel(new GridLayout(1, 0));
        buttonsBox.setOpaque(true);
        buttonsBox.setBackground(App.skin.getDefaultBackground());
        buttonsBox.setBorder(new EmptyBorder(0, 0, 0, 0));

        overlay = new DropOverlay();
        treeHolder = new JPanel(new BorderLayout(0, 0));
        treeHolder.setOpaque(false);

        // Both fill the panel; the overlay first, so it is painted on top. It has no mouse listeners, so every mouse
        // event goes through it to the tabs and contents below.
        setLayout(new FillLayout());
        add(overlay);
        add(treeHolder);

        activeGroup = new TabGroup();
        treeHolder.add(activeGroup, BorderLayout.CENTER);
        updateButtonsBoxGroup();
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false; // the overlay overlaps the tabs
    }

    @Override
    public void addNotify() {
        super.addNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", focusListener);
    }

    @Override
    public void removeNotify() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("permanentFocusOwner", focusListener);
        super.removeNotify();
    }

    // ------------------------------------------------------------------------------------------------ public API

    /**
     * Adds a tab and selects it.
     *
     * @param body     the tab's content
     * @param location where to put it, as given to the {@link NewTabCallback}; {@code null} for the end of the active group
     * @return a handle to set the tab's title and make it closable
     */
    public TabHandle addTab(Component body, @Nullable Location location) {
        TabGroup group = activeGroup;
        int index = Location.END;
        if (location != null && location.group.owner() == this && location.group.isAlive()) {
            group = location.group;
            index = location.index;
            if (location.side != null) {
                TabGroup newGroup = new TabGroup();
                split(group, location.side, newGroup);
                updateButtonsBoxGroup();
                treeHolder.revalidate();
                treeHolder.repaint();
                group = newGroup;
                index = Location.END;
            }
        }

        TabTitleComponent title = new TabTitleComponent(body);
        group.insertTab(title, index);
        group.select(title);
        activeGroup = group;
        return title;
    }

    /** Closes a tab without asking its close callback. */
    public void close(TabHandle tabHandle) {
        TabTitleComponent title = (TabTitleComponent) tabHandle;
        TabGroup group = title.group;
        if (group == null || group.owner() != this) {
            return;
        }
        group.removeTab(title);
        if (group.isEmpty()) {
            removeGroup(group);
        }
    }

    /** Shows a tab in its group and makes that group the active one. */
    public void select(TabHandle tabHandle) {
        TabTitleComponent title = (TabTitleComponent) tabHandle;
        if (title.group != null && title.group.owner() == this) {
            title.group.select(title);
            activeGroup = title.group;
        }
    }

    /**
     * @return the end of the group {@code tab} is in (e.g. to open a tab next to it), or {@code null} if it is not in
     * this panel any more
     */
    public Location locationOf(TabHandle tab) {
        TabGroup group = ((TabTitleComponent) tab).group;
        return group != null && group.owner() == this ? new Location(group, Location.END, null) : null;
    }

    /**
     * @return a new group split off at {@code side} of the group {@code tab} is in, or {@code null} if it is not in this
     * panel any more
     */
    public Location beside(TabHandle tab, Side side) {
        TabGroup group = ((TabTitleComponent) tab).group;
        return group != null && group.owner() == this ? new Location(group, Location.END, side) : null;
    }

    /** @return the content of the active group's selected tab, or {@code null} if it has none */
    public Component getSelectedContent() {
        TabTitleComponent selected = activeGroup.selected();
        return selected == null ? null : selected.component;
    }

    /**
     * @return the top-right group's "+" button and the extra buttons; add buttons to it to show them by the tabs
     */
    public JPanel getButtonsBox() {
        return buttonsBox;
    }

    /** @return each group's selected tab content (what is on screen), left to right and top to bottom */
    public Component[] getSelectedContents() {
        List<Component> contents = new ArrayList<>();
        for (TabGroup g : groups()) {
            TabTitleComponent selected = g.selected();
            if (selected != null) {
                contents.add(selected.component);
            }
        }
        return contents.toArray(new Component[0]);
    }

    /** @return every tab's content, group by group, left to right and top to bottom */
    public Component[] getTabContents() {
        List<Component> contents = new ArrayList<>();
        for (TabGroup g : groups()) {
            for (TabTitleComponent t : g.tabs()) {
                contents.add(t.component);
            }
        }
        return contents.toArray(new Component[0]);
    }

    // ------------------------------------------------------------------------------------------------ the tree

    private Component root() {
        return treeHolder.getComponentCount() == 0 ? null : treeHolder.getComponent(0);
    }

    private List<TabGroup> groups() {
        List<TabGroup> list = new ArrayList<>();
        collectGroups(root(), list);
        return list;
    }

    private static void collectGroups(Component node, List<TabGroup> list) {
        if (node instanceof TabGroup) {
            list.add((TabGroup) node);
        } else if (node instanceof JSplitPane) {
            collectGroups(((JSplitPane) node).getLeftComponent(), list);
            collectGroups(((JSplitPane) node).getRightComponent(), list);
        }
    }

    /** Puts {@code replacement} where {@code node} is: in its split pane, or as the root. */
    private void replace(Component node, Component replacement) {
        Container parent = node.getParent();
        if (parent instanceof JSplitPane) {
            JSplitPane split = (JSplitPane) parent;
            int divider = split.getDividerLocation();
            if (split.getLeftComponent() == node) {
                split.setLeftComponent(replacement);
            } else {
                split.setRightComponent(replacement);
            }
            split.setDividerLocation(divider);
        } else {
            treeHolder.remove(node);
            treeHolder.add(replacement, BorderLayout.CENTER);
        }
    }

    /** Splits {@code target} at {@code edge}, giving {@code newGroup} that half. */
    private void split(TabGroup target, Side edge, TabGroup newGroup) {
        boolean horizontal = edge == Side.LEFT || edge == Side.RIGHT;
        int extent = horizontal ? target.getWidth() : target.getHeight();

        JSplitPane split = new SkinnedSplitPane(horizontal ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT);
        split.setDividerSize(DIVIDER_SIZE);
        split.setResizeWeight(0.5);
        split.setContinuousLayout(true);
        split.setBorder(null);

        replace(target, split);
        boolean newFirst = edge == Side.LEFT || edge == Side.TOP;
        split.setLeftComponent(newFirst ? newGroup : target);
        split.setRightComponent(newFirst ? target : newGroup);
        if (extent > 0) {
            split.setDividerLocation((extent - DIVIDER_SIZE) / 2);
        } else {
            // Not laid out yet (e.g. split while opening): put the divider in the middle once it has a size.
            split.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    int size = horizontal ? split.getWidth() : split.getHeight();
                    if (size > 0) {
                        split.removeComponentListener(this);
                        split.setDividerLocation((size - DIVIDER_SIZE) / 2);
                    }
                }
            });
        }
    }

    /** Removes an empty group, collapsing its split into the other side. The last group always stays. */
    private void removeGroup(TabGroup group) {
        Container parent = group.getParent();
        if (!(parent instanceof JSplitPane)) {
            return; // the root group
        }
        JSplitPane split = (JSplitPane) parent;
        Component sibling = split.getLeftComponent() == group ? split.getRightComponent() : split.getLeftComponent();
        split.remove(group);
        split.remove(sibling);
        replace(split, sibling);
        group.alive = false;

        if (activeGroup == group) {
            List<TabGroup> remaining = new ArrayList<>();
            collectGroups(sibling, remaining);
            activeGroup = remaining.get(0);
        }
        updateButtonsBoxGroup();
        treeHolder.revalidate();
        treeHolder.repaint();
    }

    /** Puts the buttons box on the top-right group: the right side of each row split, the top of each column one. */
    private void updateButtonsBoxGroup() {
        Component node = root();
        while (node instanceof JSplitPane) {
            JSplitPane split = (JSplitPane) node;
            node = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? split.getRightComponent() : split.getTopComponent();
        }
        TabGroup topRight = (TabGroup) node;
        if (topRight == buttonsBoxGroup) {
            return;
        }
        if (buttonsBoxGroup != null) {
            buttonsBoxGroup.showOwnButtons();
        }
        topRight.showButtonsBox(buttonsBox);
        buttonsBoxGroup = topRight;
    }

    private void onFocusChanged(Component focused) {
        for (Component c = focused; c != null; c = c.getParent()) {
            if (c instanceof TabGroup && ((TabGroup) c).owner() == this) {
                activeGroup = (TabGroup) c;
                return;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ dragging

    /** Where a dragged tab lands: into a group at an index (among its other tabs), or splitting a group at an edge. */
    private static final class Drop {
        final TabGroup group;
        final int index;
        final Side edge;
        /** Over a strip: where the line is drawn (x in the panel). */
        final Integer lineX;
        /** Over a content: what is shaded (in the panel). */
        final Rectangle shaded;

        Drop(TabGroup group, int index, Side edge, Integer lineX, Rectangle shaded) {
            this.group = group;
            this.index = index;
            this.edge = edge;
            this.lineX = lineX;
            this.shaded = shaded;
        }
    }

    private static final class Drag {
        final TabTitleComponent tab;
        final Point pressedAt;
        boolean dragging;
        Point at;
        Drop drop;

        Drag(TabTitleComponent tab, Point pressedAt) {
            this.tab = tab;
            this.pressedAt = pressedAt;
            this.at = pressedAt;
        }
    }

    private Rectangle boundsInPanel(Component c) {
        return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), this);
    }

    /** Where a tab dragged to {@code p} (in the panel) lands, or {@code null} over nothing it can land on. */
    private Drop dropAt(TabTitleComponent dragged, Point p) {
        List<TabGroup> groups = groups();
        for (TabGroup g : groups) {
            Rectangle strip = boundsInPanel(g.strip);
            if (strip.contains(p)) {
                List<Rectangle> others = new ArrayList<>();
                for (TabTitleComponent t : g.tabs()) {
                    if (t != dragged) {
                        others.add(boundsInPanel(t));
                    }
                }
                int index = 0;
                for (Rectangle r : others) {
                    if (r.getCenterX() < p.x) {
                        index++;
                    }
                }
                int lineX;
                if (index < others.size()) {
                    lineX = others.get(index).x;
                } else if (!others.isEmpty()) {
                    Rectangle last = others.get(others.size() - 1);
                    lineX = last.x + last.width;
                } else {
                    lineX = strip.x + 8;
                }
                return new Drop(g, index, null, lineX, null);
            }
        }
        for (TabGroup g : groups) {
            Rectangle content = boundsInPanel(g.cardPanel);
            if (!content.contains(p)) {
                continue;
            }
            int stripHeight = g.strip.getHeight();
            boolean alone = dragged.group == g && g.tabs().size() == 1;

            Side edge = null;
            double best = EDGE_ZONE;
            for (Side e : Side.values()) {
                boolean horizontal = e == Side.LEFT || e == Side.RIGHT;
                boolean fits = horizontal
                        ? (content.width - DIVIDER_SIZE) / 2 >= MIN_GROUP
                        : (stripHeight + content.height - DIVIDER_SIZE) / 2 >= stripHeight + MIN_GROUP;
                if (!fits) {
                    continue;
                }
                double ratio;
                switch (e) {
                    case LEFT:
                        ratio = (double) (p.x - content.x) / Math.max(1, content.width);
                        break;
                    case RIGHT:
                        ratio = (double) (content.x + content.width - 1 - p.x) / Math.max(1, content.width);
                        break;
                    case TOP:
                        ratio = (double) (p.y - content.y) / Math.max(1, content.height);
                        break;
                    default:
                        ratio = (double) (content.y + content.height - 1 - p.y) / Math.max(1, content.height);
                        break;
                }
                if (ratio < best) {
                    best = ratio;
                    edge = e;
                }
            }

            if (alone || (edge == null && dragged.group == g)) {
                // Its own group's center: where it is now, offered as the fifth place among the four edges.
                return new Drop(g, g.tabs().indexOf(dragged), null, null, content);
            }
            if (edge != null) {
                return new Drop(g, Location.END, edge, null, half(content, edge));
            }
            return new Drop(g, Location.END, null, null, content);
        }
        return null;
    }

    private static Rectangle half(Rectangle r, Side edge) {
        switch (edge) {
            case LEFT:
                return new Rectangle(r.x, r.y, r.width / 2, r.height);
            case RIGHT:
                return new Rectangle(r.x + r.width - r.width / 2, r.y, r.width / 2, r.height);
            case TOP:
                return new Rectangle(r.x, r.y, r.width, r.height / 2);
            default:
                return new Rectangle(r.x, r.y + r.height - r.height / 2, r.width, r.height / 2);
        }
    }

    private void applyDrop(TabTitleComponent tab, Drop drop) {
        TabGroup source = tab.group;
        TabGroup target = drop.group;
        if (drop.edge != null) {
            TabGroup newGroup = new TabGroup();
            split(target, drop.edge, newGroup);
            target = newGroup;
        }
        source.removeTab(tab);
        target.insertTab(tab, drop.edge != null ? Location.END : drop.index);
        target.select(tab);
        activeGroup = target;
        if (source.isEmpty() && source != target) {
            removeGroup(source);
        }
        updateButtonsBoxGroup();
        treeHolder.revalidate();
        treeHolder.repaint();
        tab.component.requestFocusInWindow();
    }

    private void onTabPressed(TabTitleComponent tab, MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            drag = new Drag(tab, SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), this));
        }
    }

    private void onTabDragged(MouseEvent e) {
        if (drag == null) {
            return;
        }
        Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), this);
        if (!drag.dragging && drag.pressedAt.distance(p) > DRAG_SLOP) {
            drag.dragging = true;
            drag.tab.setDragged(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escapeCancelsDrag);
        }
        if (drag.dragging) {
            drag.at = p;
            drag.drop = dropAt(drag.tab, p);
            overlay.repaint();
        }
    }

    private void onTabReleased(MouseEvent e) {
        Drag d = drag;
        drag = null;
        if (d == null) {
            return;
        }
        if (!d.dragging) {
            select(d.tab); // a click: a press and release that did not move far enough to drag
            return;
        }
        endDrag(d);
        if (d.drop != null && d.tab.group != null) {
            LOG.trace("Dropping tab {} into group {} (index {}, edge {})", d.tab.getName(), d.drop.group.getName(), d.drop.index, d.drop.edge);
            applyDrop(d.tab, d.drop);
        }
    }

    private boolean onKeyWhileDragging(KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE && drag != null && drag.dragging) {
            cancelDrag();
            return true; // consumed: the focused component doesn't get it
        }
        return false;
    }

    /** Esc: the tab stays where it was; the rest of the gesture (more drags, the release) is ignored. */
    void cancelDrag() {
        Drag d = drag;
        drag = null;
        if (d != null && d.dragging) {
            LOG.trace("Drag of tab {} cancelled", d.tab.getName());
            endDrag(d);
        }
    }

    private void endDrag(Drag d) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escapeCancelsDrag);
        d.tab.setDragged(false);
        setCursor(null);
        overlay.repaint();
    }

    /** Paints where a dragged tab would land, and its title by the pointer. Transparent to the mouse. */
    private final class DropOverlay extends JComponent {
        DropOverlay() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Drag d = drag;
            if (d == null || !d.dragging) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color accent = App.skin.getDefaultSelectionBackground();
                Drop drop = d.drop;
                if (drop != null && drop.shaded != null) {
                    Rectangle r = drop.shaded;
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 46));
                    g2.fillRect(r.x, r.y, r.width, r.height);
                    g2.setColor(accent);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
                } else if (drop != null && drop.lineX != null) {
                    Rectangle strip = boundsInPanel(drop.group.strip);
                    g2.setColor(accent);
                    g2.fillRect(drop.lineX - 1, strip.y, 2, strip.height);
                }

                String title = d.tab.titleLabel.getText();
                if (title != null && !title.isEmpty()) {
                    g2.setFont(d.tab.titleLabel.getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int w = fm.stringWidth(title) + 12;
                    int h = fm.getHeight() + 4;
                    int x = Math.min(d.at.x + 12, getWidth() - w);
                    int y = Math.min(d.at.y + 12, getHeight() - h);
                    g2.setColor(selectedBg);
                    g2.fillRect(x, y, w, h);
                    g2.setColor(accent);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRect(x, y, w - 1, h - 1);
                    g2.setColor(d.tab.titleLabel.getForeground());
                    g2.drawString(title, x + 6, y + 2 + fm.getAscent());
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** Makes every child as big as the panel. */
    private static final class FillLayout implements LayoutManager {
        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return size(parent, false);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return size(parent, true);
        }

        private static Dimension size(Container parent, boolean minimum) {
            Dimension d = new Dimension();
            for (Component c : parent.getComponents()) {
                Dimension cd = minimum ? c.getMinimumSize() : c.getPreferredSize();
                d.width = Math.max(d.width, cd.width);
                d.height = Math.max(d.height, cd.height);
            }
            Insets in = parent.getInsets();
            d.width += in.left + in.right;
            d.height += in.top + in.bottom;
            return d;
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            for (Component c : parent.getComponents()) {
                c.setBounds(in.left, in.top, parent.getWidth() - in.left - in.right, parent.getHeight() - in.top - in.bottom);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ groups and tabs

    /** One pane of the split: a strip of tabs over the selected tab's content. */
    final class TabGroup extends JPanel {
        final CardLayout cardLayout = new CardLayout();
        final JPanel cardPanel = new JPanel(cardLayout);
        final JPanel tabHolder = new JPanel(new GridLayout(1, 0, 0, 0));
        final JPanel strip = new JPanel(new BorderLayout(3, 3));
        final JButton newTabButton = new JButton();
        /** This group's own buttons box: just its "+" button, when the panel's buttons box is on another group. */
        final JPanel ownButtons = new JPanel(new GridLayout(1, 0));
        boolean alive = true;

        TabGroup() {
            super(new BorderLayout(0, 0), true);
            setName("group-" + (++groupCounter));
            setOpaque(true);

            tabHolder.setOpaque(true);
            cardPanel.setOpaque(false);
            strip.setOpaque(true);
            strip.add(tabHolder);

            newTabButton.setToolTipText("New tab");
            newTabButton.setFont(App.skin.getIconFont().deriveFont(Constants.MEDIUM_TEXT_SIZE));
            newTabButton.setText(FontAwesomeContants.FA_PLUS_SQUARE);
            newTabButton.putClientProperty("Nimbus.Overrides", App.skin.createTabButtonSkin());
            newTabButton.setForeground(App.skin.getInfoTextForeground());
            newTabButton.addActionListener(e -> {
                activeGroup = this;
                newTabCallback.onNewTab(newTabButton, new Location(this, Location.END, null));
            });

            ownButtons.setOpaque(true);
            ownButtons.setBackground(App.skin.getDefaultBackground());
            ownButtons.setBorder(new EmptyBorder(0, 0, 0, 0));
            showOwnButtons();

            add(strip, BorderLayout.NORTH);
            add(cardPanel);

            // A press anywhere in the group (even on a component that takes no focus) makes it the active one.
            MouseAdapter activate = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    activeGroup = TabGroup.this;
                }
            };
            tabHolder.addMouseListener(activate);
        }

        ClosableTabbedPanel owner() {
            return ClosableTabbedPanel.this;
        }

        boolean isAlive() {
            return alive && SwingUtilities.isDescendingFrom(this, ClosableTabbedPanel.this);
        }

        void showOwnButtons() {
            ownButtons.removeAll();
            ownButtons.add(newTabButton);
            strip.add(ownButtons, BorderLayout.EAST);
            strip.revalidate();
        }

        void showButtonsBox(JPanel box) {
            strip.remove(ownButtons);
            box.add(newTabButton, 0);
            strip.add(box, BorderLayout.EAST);
            strip.revalidate();
        }

        List<TabTitleComponent> tabs() {
            List<TabTitleComponent> list = new ArrayList<>();
            for (Component c : tabHolder.getComponents()) {
                list.add((TabTitleComponent) c);
            }
            return list;
        }

        boolean isEmpty() {
            return tabHolder.getComponentCount() == 0;
        }

        TabTitleComponent selected() {
            for (TabTitleComponent t : tabs()) {
                if (t.selected) {
                    return t;
                }
            }
            return null;
        }

        /** @param index among this group's tabs, or {@link Location#END} */
        void insertTab(TabTitleComponent title, int index) {
            int count = tabHolder.getComponentCount();
            tabHolder.add(title, index < 0 || index > count ? count : index);
            cardPanel.add(title.component, title.getName());
            title.group = this;
            tabHolder.revalidate();
            tabHolder.repaint();
        }

        void removeTab(TabTitleComponent title) {
            int index = tabs().indexOf(title);
            if (index < 0) {
                return;
            }
            boolean wasSelected = title.selected;
            tabHolder.remove(title);
            cardPanel.remove(title.component);
            title.group = null;
            title.unSelect();
            if (wasSelected && !isEmpty()) {
                select(tabs().get(Math.max(0, index - 1)));
            }
            tabHolder.revalidate();
            tabHolder.repaint();
        }

        void select(TabTitleComponent title) {
            cardLayout.show(cardPanel, title.getName());
            for (TabTitleComponent t : tabs()) {
                if (t != title) {
                    t.unSelect();
                }
            }
            title.select();
        }
    }

    private class TabTitleComponent extends JPanel implements TabHandle {
        final JLabel titleLabel;
        final Component component;

        TabCloseButton tabCloseButton;
        boolean selected;
        boolean dragged;
        TabGroup group;

        private Supplier<Boolean> onCloseCallback;

        TabTitleComponent(Component component) {
            super(new BorderLayout());
            this.component = component;
            setName(component.hashCode() + "");
            setBorder(
                    new CompoundBorder(new MatteBorder(0, 0, 0, 1, selectedBg),
                            new EmptyBorder(5, 10, 5, 5)
                    )
            );
            setBackground(unselectedBg);
            setOpaque(true);
            titleLabel = new JLabel();
            titleLabel.setHorizontalAlignment(JLabel.CENTER);
            this.add(titleLabel);

            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onTabPressed(TabTitleComponent.this, e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    onTabDragged(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    onTabReleased(e);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (tabCloseButton != null)
                        tabCloseButton.setHoveringTab(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (tabCloseButton != null)
                        tabCloseButton.setHoveringTab(false);
                }
            };
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
            titleLabel.addMouseListener(mouseAdapter);
            titleLabel.addMouseMotionListener(mouseAdapter);
        }

        @Override
        public void setTitle(String title) {
            titleLabel.setText(title);
        }

        @Override
        public void setClosable(Supplier<Boolean> onCloseCallback) {

            if (onCloseCallback != null) {

                if (tabCloseButton == null) {
                    tabCloseButton = new TabCloseButton();
                    tabCloseButton.setSelected(selected);
                    tabCloseButton.setForeground(App.skin.getInfoTextForeground());
                    tabCloseButton.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            LOG.trace("Asking for close tab: {}", getName());
                            if (TabTitleComponent.this.onCloseCallback.get()) {
                                LOG.trace("Closing tab: {}", getName());
                                ClosableTabbedPanel.this.close(TabTitleComponent.this);
                            } else {
                                LOG.trace("Cancelled closing tab: {}", getName());
                            }
                        }
                    });
                }

                if (tabCloseButton.getParent() == null) {
                    this.add(tabCloseButton, BorderLayout.EAST);
                }

            } else {
                if (tabCloseButton != null) {
                    this.remove(tabCloseButton);
                }
            }

            this.onCloseCallback = onCloseCallback;

        }

        void setDragged(boolean dragged) {
            this.dragged = dragged;
            repaint();
        }

        @Override
        public void paint(Graphics g) {
            if (!dragged) {
                super.paint(g);
                return;
            }
            // Painted whole into an image, then faded once: fading each child as it is painted would compound the
            // alpha where they overlap (the close button over the tab's background).
            BufferedImage image = new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()), BufferedImage.TYPE_INT_ARGB);
            Graphics2D ig = image.createGraphics();
            try {
                super.paint(ig);
            } finally {
                ig.dispose();
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                g2.drawImage(image, 0, 0, null);
            } finally {
                g2.dispose();
            }
        }

        public void unSelect() {
            selected = false;

            setBackground(unselectedBg);
            if (tabCloseButton != null)
                tabCloseButton.setSelected(false);
            revalidate();
            repaint();
        }

        public void select() {
            selected = true;

            setBackground(selectedBg);
            if (tabCloseButton != null)
                tabCloseButton.setSelected(true);
            revalidate();
            repaint();
        }
    }
}
