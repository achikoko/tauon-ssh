package tauon.app.ui.components.closabletabs;

/**
 * Where a new tab goes in a {@link ClosableTabbedPanel}: a tab group (one pane of a split) and a position in it, or a new
 * group split off beside it.
 * <p>
 * Callers don't build one: they get it from the panel's new-tab callback (the group whose "+" button was clicked), from
 * {@link ClosableTabbedPanel#locationOf(TabHandle)} or from {@link ClosableTabbedPanel#beside(TabHandle, Side)}, and pass
 * it to {@link ClosableTabbedPanel#addTab(java.awt.Component, Location)}, possibly later (after a dialog or a popup menu).
 * {@code null} means the active group. A location whose group no longer exists (its last tab was closed or dragged away
 * meanwhile) also falls back to the active group.
 */
public final class Location {

    /** The side of a group a new group is split off at. */
    public enum Side {LEFT, RIGHT, TOP, BOTTOM}

    /** Appends at the end of the group. */
    static final int END = -1;

    final ClosableTabbedPanel.TabGroup group;
    final int index;
    /** Not null: split {@link #group} at this side and put the tab in the new half. */
    final Side side;

    Location(ClosableTabbedPanel.TabGroup group, int index, Side side) {
        this.group = group;
        this.index = index;
        this.side = side;
    }

    @Override
    public String toString() {
        return "Location[group=" + group.getName()
                + (side != null ? ", beside=" + side : ", index=" + (index == END ? "end" : index)) + "]";
    }
}
