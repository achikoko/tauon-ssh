package tauon.app.util.misc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VersionEntryTest {

    private static int cmp(String a, String b) {
        return Integer.signum(new VersionEntry(a).compareTo(new VersionEntry(b)));
    }

    @Test
    public void comparesEachNumberSeparately() {
        assertEquals(1, cmp("v4.0.0", "v3.3.1"));
        assertEquals(-1, cmp("v3.3.1", "v4.0.0"));
        assertEquals(1, cmp("v4.0.1", "v4.0.0"));
        assertEquals(0, cmp("v4.0.1", "v4.0.1"));
    }

    @Test
    public void numbersOfTenOrMoreDoNotCollide() {
        // The old checker summed digits by powers of ten: 4.0.10 and 4.1.0 were both 410.
        assertEquals(-1, cmp("v4.0.10", "v4.1.0"));
        assertEquals(1, cmp("v4.10.0", "v4.9.9"));
        assertEquals(1, cmp("v10.0.0", "v9.99.99"));
        assertEquals(1, cmp("v4.0.12345678901234567890", "v4.0.9")); // no overflow
    }

    @Test
    public void missingNumbersCountAsZeroAndPrefixIsOptional() {
        assertEquals(0, cmp("v4.0", "v4.0.0"));
        assertEquals(0, cmp("4.0.0", "v4.0.0"));
        assertEquals(0, cmp("v4.0.01", "v4.0.1"));
    }

    @Test
    public void suffixesNoLongerBreakTheChecker() {
        // v3.3.0-r1 used to throw NumberFormatException
        assertEquals(1, cmp("v4.0.0", "v3.3.0-r1"));
        assertEquals(1, cmp("v3.3.0-r1", "v3.2.1"));
    }

    @Test
    public void preReleasesSortBeforeTheirRelease() {
        assertEquals(-1, cmp("v4.0.0-rc1", "v4.0.0"));
        assertEquals(-1, cmp("v4.0.0-alpha", "v4.0.0-beta"));
        assertEquals(-1, cmp("v4.0.0-rc.2", "v4.0.0-rc.10"));
        assertEquals(-1, cmp("v4.0.0-1", "v4.0.0-alpha")); // numeric before alphanumeric
        assertEquals(-1, cmp("v4.0.0-rc", "v4.0.0-rc.1"));
        assertEquals(1, cmp("v4.0.1-rc1", "v4.0.0"));
    }

    @Test
    public void buildMetadataIsIgnored() {
        assertEquals(0, cmp("v4.0.0+build.7", "v4.0.0"));
    }

    @Test
    public void unparseableTagsAreNeverNewer() {
        assertEquals(-1, cmp("latest", "v1.0.0"));
        assertEquals(-1, cmp(null, "v1.0.0"));
        assertEquals(-1, cmp("v4.x.0", "v0.0.1"));
        assertEquals(0, cmp("garbage", "nonsense"));
        assertTrue(new VersionEntry("v4.0.0").compareTo(new VersionEntry("")) > 0);
    }
}
