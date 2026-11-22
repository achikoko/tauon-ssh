package tauon.app.ui.containers.session.pages.files;

import tauon.app.util.misc.PathUtils;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Back/forward history of a file browser: folders left behind, newest first.
 * <p>
 * Callers change it only once the destination folder has been listed, so a failed navigation leaves it untouched.
 */
public class NavigationHistory {
    private final Deque<String> back = new ArrayDeque<>();
    private final Deque<String> forward = new ArrayDeque<>();

    public boolean hasPrevElement() {
        return !back.isEmpty();
    }

    public boolean hasNextElement() {
        return !forward.isEmpty();
    }

    /** @return the folder Back goes to, without removing it; {@code null} if none */
    public String peekBack() {
        return back.peek();
    }

    /** @return the folder Forward goes to, without removing it; {@code null} if none */
    public String peekForward() {
        return forward.peek();
    }

    /**
     * A new navigation from {@code from} to {@code to} succeeded: {@code from} goes to the back history and the
     * forward history is dropped. Nothing changes when there was no folder before or the folder is the same.
     */
    public void visit(String from, String to) {
        if (from == null || PathUtils.isSamePath(from, to)) {
            return;
        }
        back.push(from);
        forward.clear();
    }

    /** Back succeeded while {@code current} was shown: its entry is consumed and {@code current} can be gone forward to. */
    public void back(String current) {
        if (back.isEmpty()) {
            return;
        }
        back.pop();
        if (current != null) {
            forward.push(current);
        }
    }

    /** Forward succeeded while {@code current} was shown: its entry is consumed and {@code current} can be gone back to. */
    public void forward(String current) {
        if (forward.isEmpty()) {
            return;
        }
        forward.pop();
        if (current != null) {
            back.push(current);
        }
    }
}
