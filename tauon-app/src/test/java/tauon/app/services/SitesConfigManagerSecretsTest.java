package tauon.app.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import tauon.app.services.SitesConfigManager.PasswordEntry;
import tauon.app.settings.HopEntry;
import tauon.app.settings.SessionFolder;
import tauon.app.settings.SiteInfo;
import tauon.app.ui.dialogs.sessions.SavedSessionTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class SitesConfigManagerSecretsTest {

    private static SiteInfo siteWithHops(String... hopPasswords) {
        SiteInfo site = new SiteInfo();
        site.setId("site-1");
        site.setHost("target");
        for (int i = 0; i < hopPasswords.length; i++) {
            site.getJumpHosts().add(new HopEntry("hop-" + i, "host" + i, 22, "user", hopPasswords[i], null));
        }
        return site;
    }

    private static SessionFolder folderWith(SiteInfo site) {
        SessionFolder folder = new SessionFolder();
        folder.setId("root");
        folder.getItems().add(site);
        return folder;
    }

    /** Writes the sessions file as {@code save} does and reads it back as {@code getSessionTree} does. */
    private static SessionFolder roundTrip(SessionFolder folder) throws Exception {
        SavedSessionTree tree = new SavedSessionTree();
        tree.setFolder(folder);
        String json = new ObjectMapper().writeValueAsString(tree);
        return SitesConfigManager.readSitesTreeFromContent(json).getFolder();
    }

    private static Map<String, String> hopPasswordsById(SiteInfo site) {
        Map<String, String> m = new HashMap<>();
        for (HopEntry h : site.getJumpHosts()) {
            m.put(h.getId(), h.getPassword());
        }
        return m;
    }

    @Test
    public void eachJumpHostGetsItsOwnPassword() throws Exception {
        SiteInfo site = siteWithHops("p0", null, "p2");
        Map<String, PasswordEntry> map = new HashMap<>();
        SitesConfigManager.setSecrets(site, map);

        SessionFolder loaded = roundTrip(folderWith(site));
        SitesConfigManager.populatePasswordsInto(loaded, map);

        List<HopEntry> hops = loaded.getItems().get(0).getJumpHosts();
        assertEquals("p0", hops.get(0).getPassword());
        assertNull(hops.get(1).getPassword());
        assertEquals("p2", hops.get(2).getPassword());
    }

    @Test
    public void reorderedJumpHostsKeepTheirPasswords() throws Exception {
        SiteInfo site = siteWithHops("p0", "p1", "p2");
        Map<String, String> before = hopPasswordsById(site);

        // As JumpHostPanel's up/down buttons do: the HopEntry objects move
        List<HopEntry> reordered = new ArrayList<>(site.getJumpHosts());
        Collections.reverse(reordered);
        site.setJumpHosts(reordered);

        Map<String, PasswordEntry> map = new HashMap<>();
        SitesConfigManager.setSecrets(site, map);
        SessionFolder loaded = roundTrip(folderWith(site));
        SitesConfigManager.populatePasswordsInto(loaded, map);

        SiteInfo loadedSite = loaded.getItems().get(0);
        assertEquals("hop-2", loadedSite.getJumpHosts().get(0).getId());
        assertEquals(before, hopPasswordsById(loadedSite));
    }

    @Test
    public void entryWithoutHopPasswordsStillLoadsTheOtherSecrets() {
        SiteInfo site = siteWithHops("p0");
        PasswordEntry entry = new PasswordEntry();
        entry.infoPassword = "pw".toCharArray();
        entry.proxyPassword = "proxy".toCharArray();
        Map<String, PasswordEntry> map = new HashMap<>();
        map.put("site-1", entry);

        SitesConfigManager.populatePasswordsInto(folderWith(siteWithHops()), map); // no jump hosts
        SessionFolder folder = folderWith(site);
        site.getJumpHosts().get(0).setPassword(null);
        SitesConfigManager.populatePasswordsInto(folder, map);

        assertEquals("pw", site.getPassword());
        assertEquals("proxy", site.getProxyPassword());
        assertNull(site.getJumpHosts().get(0).getPassword());
    }

    @Test
    public void proxyPasswordIsNotWrittenToTheSessionsFile() throws Exception {
        SiteInfo site = siteWithHops();
        site.setProxyPassword("proxy-secret");
        SavedSessionTree tree = new SavedSessionTree();
        tree.setFolder(folderWith(site));

        String json = new ObjectMapper().writeValueAsString(tree);
        assertFalse(json.contains("proxy-secret"));
        assertFalse(json.contains("proxyPassword"));

        Map<String, PasswordEntry> map = new HashMap<>();
        SitesConfigManager.setSecrets(site, map);
        assertArrayEquals("proxy-secret".toCharArray(), map.get("site-1").proxyPassword);

        SessionFolder loaded = SitesConfigManager.readSitesTreeFromContent(json).getFolder();
        assertFalse(SitesConfigManager.hasPlainTextSecrets(loaded));
        SitesConfigManager.populatePasswordsInto(loaded, map);
        assertEquals("proxy-secret", loaded.getItems().get(0).getProxyPassword());
    }

    @Test
    public void plainTextProxyPasswordInAnOldSessionsFileIsReadAndDetected() throws Exception {
        String oldJson = "{\"folder\":{\"id\":\"root\",\"folders\":[{\"id\":\"sub\",\"items\":["
                + "{\"id\":\"site-1\",\"host\":\"target\",\"proxyPassword\":\"old-secret\"}]}]}}";
        SessionFolder loaded = SitesConfigManager.readSitesTreeFromContent(oldJson).getFolder();
        assertTrue(SitesConfigManager.hasPlainTextSecrets(loaded));

        // Its passwords file has no proxy password yet: the plain text value is kept and saved encrypted
        Map<String, PasswordEntry> stored = new HashMap<>();
        stored.put("site-1", new PasswordEntry());
        SitesConfigManager.populatePasswordsInto(loaded, stored);
        SiteInfo site = loaded.getFolders().get(0).getItems().get(0);
        assertEquals("old-secret", site.getProxyPassword());

        Map<String, PasswordEntry> map = new HashMap<>();
        SitesConfigManager.setSecrets(site, map);
        assertArrayEquals("old-secret".toCharArray(), map.get("site-1").proxyPassword);
    }

    @Test
    public void plainTextProxyPasswordWinsOverTheStoredOne() throws Exception {
        // Only an older version writes it in plain text, so it is the most recent value
        String json = "{\"folder\":{\"id\":\"root\",\"items\":[{\"id\":\"site-1\",\"proxyPassword\":\"newer\"}]}}";
        SessionFolder loaded = SitesConfigManager.readSitesTreeFromContent(json).getFolder();
        PasswordEntry entry = new PasswordEntry();
        entry.proxyPassword = "older".toCharArray();
        Map<String, PasswordEntry> map = new HashMap<>();
        map.put("site-1", entry);

        SitesConfigManager.populatePasswordsInto(loaded, map);
        assertEquals("newer", loaded.getItems().get(0).getProxyPassword());
    }
}
