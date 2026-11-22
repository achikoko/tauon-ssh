package tauon.app.ui.containers.session.pages.files;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Replays what {@link AbstractFileBrowserView} does: a navigation from the shown folder calls
 * {@code visit(shown, listed)}, Back/Forward call {@code back(shown)}/{@code forward(shown)}, always after the
 * destination folder was listed.
 */
public class NavigationHistoryTest {

    private final NavigationHistory history = new NavigationHistory();
    private String shown;

    private void navigate(String to) {
        history.visit(shown, to);
        shown = to;
    }

    private void back() {
        String to = history.peekBack();
        history.back(shown);
        shown = to;
    }

    private void forward() {
        String to = history.peekForward();
        history.forward(shown);
        shown = to;
    }

    @Test
    public void firstListingIsNotRecorded() {
        navigate("/home/user");
        assertFalse(history.hasPrevElement());
        assertFalse(history.hasNextElement());
    }

    @Test
    public void backAfterUpReturnsToTheFolderLeft() {
        navigate("/home/user");
        navigate("/home"); // Up
        back();
        assertEquals("/home/user", shown);
        assertFalse("each navigation is recorded once", history.hasPrevElement());
    }

    @Test
    public void backAfterOpeningAFolderReturnsToTheParent() {
        navigate("/home");
        navigate("/home/user"); // double-click on "user"
        assertEquals("/home", history.peekBack());
        back();
        assertEquals("/home", shown);
    }

    @Test
    public void backAndForwardWalkTheHistory() {
        navigate("/a");
        navigate("/a/b");
        navigate("/a/b/c");
        back();
        back();
        assertEquals("/a", shown);
        forward();
        assertEquals("/a/b", shown);
        forward();
        assertEquals("/a/b/c", shown);
        assertFalse(history.hasNextElement());
        assertEquals("/a/b", history.peekBack());
    }

    @Test
    public void newNavigationDropsTheForwardHistory() {
        navigate("/a");
        navigate("/a/b");
        back();
        assertTrue(history.hasNextElement());
        navigate("/etc");
        assertFalse(history.hasNextElement());
        assertEquals("/a", history.peekBack());
    }

    @Test
    public void goingToTheShownFolderIsNotRecorded() {
        navigate("/home/user");
        navigate("/home/user/"); // e.g. Home while already at home, or the address typed with a trailing slash
        assertFalse(history.hasPrevElement());
    }

    @Test
    public void failedNavigationLeavesTheHistoryUntouched() {
        navigate("/a");
        navigate("/a/b");
        // Back to "/a" fails: the view never calls back(), so the entry is still there to retry
        assertEquals("/a", history.peekBack());
        assertTrue(history.hasPrevElement());
        assertFalse(history.hasNextElement());
    }

    @Test
    public void backWithEmptyHistoryDoesNothing() {
        history.back("/a");
        history.forward("/a");
        assertFalse(history.hasPrevElement());
        assertFalse(history.hasNextElement());
    }
}
