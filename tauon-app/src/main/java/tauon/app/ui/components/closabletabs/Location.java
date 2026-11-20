package tauon.app.ui.components.closabletabs;

/**
 * Where a new tab goes in a {@link ClosableTabbedPanel}: a tab group (one pane of a split) and a position in it.
 * <p>
 * Callers don't build one: they get it from the panel's new-tab callback (the group whose "+" button was clicked)
 * and pass it back to {@link ClosableTabbedPanel#addTab(java.awt.Component, Location)}, possibly later (after a
 * dialog or a popup menu). {@code null} means the active group. A location whose group no longer exists (its last
 * tab was closed or dragged away meanwhile) also falls back to the active group.
 */
public final class Location {

    /** Appends at the end of the group. */
    static final int END = -1;

    final ClosableTabbedPanel.TabGroup group;
    final int index;

    Location(ClosableTabbedPanel.TabGroup group, int index) {
        this.group = group;
        this.index = index;
    }

    @Override
    public String toString() {
        return "Location[group=" + group.getName() + ", index=" + (index == END ? "end" : index) + "]";
    }
}
