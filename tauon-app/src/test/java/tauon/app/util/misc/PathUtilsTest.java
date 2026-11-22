package tauon.app.util.misc;

import org.junit.Test;

import static org.junit.Assert.*;

public class PathUtilsTest {

    @Test
    public void isSamePathIgnoresATrailingSeparatorOnEitherSide() {
        assertTrue(PathUtils.isSamePath("/a", "/a/"));
        assertTrue(PathUtils.isSamePath("/a/", "/a"));
        assertTrue(PathUtils.isSamePath("C:\\Users", "C:\\Users\\"));
        assertTrue(PathUtils.isSamePath("/", "/"));
        assertFalse(PathUtils.isSamePath("/a", "/b"));
        assertFalse(PathUtils.isSamePath("/a", "/ab"));
    }

    @Test
    public void isSamePathKeepsRootsApart() {
        assertFalse(PathUtils.isSamePath("/", ""));
        assertTrue(PathUtils.isSamePath("C:\\", "C:\\"));
        assertFalse(PathUtils.isSamePath("C:\\", "C:"));
    }

    @Test
    public void isSamePathWithNulls() {
        assertTrue(PathUtils.isSamePath(null, null));
        assertFalse(PathUtils.isSamePath(null, "/a"));
        assertFalse(PathUtils.isSamePath("/a", null));
    }

    @Test
    public void parentOfRootIsNull() {
        // Up at "/" must do nothing (it used to fall back to the home folder)
        assertNull(PathUtils.getParent("/"));
        assertEquals("/", PathUtils.getParent("/home"));
        assertEquals("/home/", PathUtils.getParent("/home/user/"));
    }
}
