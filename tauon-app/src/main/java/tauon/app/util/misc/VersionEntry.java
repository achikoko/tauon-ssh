package tauon.app.util.misc;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.markusbernhardt.proxy.ProxySearch;

import java.net.ProxySelector;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static tauon.app.util.misc.Constants.API_UPDATE_URL;

/**
 * A release version, as written in a git tag (e.g. {@code v4.0.1}).
 * <p>
 * Tags follow <a href="https://romversioning.github.io/romver/">Romantic Versioning</a>:
 * {@code [v]PROJECT.MAJOR.MINOR[-PRE][+BUILD]}. Versions are compared number by number
 * (a missing number counts as 0, so {@code v4.0 == v4.0.0}). A pre-release sorts before its
 * release ({@code v4.0.0-rc1 < v4.0.0}) and build metadata is ignored, as in SemVer.
 * A tag that cannot be parsed sorts before every valid version, so it is never offered as an update.
 */
public class VersionEntry implements Comparable<VersionEntry> {

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$"
    );

    private String tagName;

    public VersionEntry() {
        // Constructor for Version checker
    }

    public VersionEntry(String tagName) {
        this.tagName = tagName;
    }

    @Override
    public int compareTo(VersionEntry o) {
        return compareVersions(tagName, o.tagName);
    }

    /**
     * @return negative, zero or positive if {@code a} is older than, equal to or newer than {@code b}.
     */
    static int compareVersions(String a, String b) {
        Matcher ma = a == null ? null : VERSION_PATTERN.matcher(a.trim());
        Matcher mb = b == null ? null : VERSION_PATTERN.matcher(b.trim());
        boolean validA = ma != null && ma.matches();
        boolean validB = mb != null && mb.matches();
        if (!validA || !validB) {
            return Boolean.compare(validA, validB);
        }

        int result = compareNumbers(ma.group(1).split("\\."), mb.group(1).split("\\."));
        if (result != 0) {
            return result;
        }
        return comparePreRelease(ma.group(2), mb.group(2));
    }

    private static int compareNumbers(String[] a, String[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int result = compareNumeric(i < a.length ? a[i] : "0", i < b.length ? b[i] : "0");
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    /** Compares digit strings of any length without overflowing. */
    private static int compareNumeric(String a, String b) {
        a = a.replaceFirst("^0+(?=.)", "");
        b = b.replaceFirst("^0+(?=.)", "");
        return a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b);
    }

    /** SemVer precedence: no pre-release > pre-release; identifiers compared one by one. */
    private static int comparePreRelease(String a, String b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : 1) : -1;
        }
        String[] ia = a.split("\\.");
        String[] ib = b.split("\\.");
        for (int i = 0; i < Math.min(ia.length, ib.length); i++) {
            boolean numA = ia[i].matches("\\d+");
            boolean numB = ib[i].matches("\\d+");
            int result;
            if (numA && numB) {
                result = compareNumeric(ia[i], ib[i]);
            } else if (numA || numB) {
                result = numA ? -1 : 1; // numeric identifiers sort before alphanumeric ones
            } else {
                result = ia[i].compareTo(ib[i]);
            }
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(ia.length, ib.length);
    }

    @JsonProperty("tag_name")
    public String getTagName() {
        return tagName;
    }

    @JsonProperty("tag_name")
    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    @Override
    public String toString() {
        return "VersionEntry [tag_name=" + tagName + "]";
    }


    public static VersionEntry getLastVersionFromGithub() {
        try {
            ProxySearch proxySearch = ProxySearch.getDefaultProxySearch();
            ProxySelector myProxySelector = proxySearch.getProxySelector();
            ProxySelector.setDefault(myProxySelector);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return objectMapper.readValue(new URL(API_UPDATE_URL).openStream(),
                    new TypeReference<VersionEntry>() {
                    });
        } catch (Exception e) {
            return null;
        }
    }

}
