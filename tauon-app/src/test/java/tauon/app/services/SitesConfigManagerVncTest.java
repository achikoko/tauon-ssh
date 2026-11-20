package tauon.app.services;

import org.junit.Test;
import tauon.app.services.SitesConfigManager.PasswordEntry;
import tauon.app.settings.SiteInfo;
import tauon.app.settings.VncConnection;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class SitesConfigManagerVncTest {

    @Test
    public void savingPutsVncPasswordsInTheSitesEntryByConnectionId() {
        SiteInfo site = new SiteInfo();
        site.setId("site-1");
        VncConnection withPassword = new VncConnection();
        withPassword.setPassword("pw");
        VncConnection withoutPassword = new VncConnection();
        site.getVncConnections().add(withPassword);
        site.getVncConnections().add(withoutPassword);

        Map<String, PasswordEntry> map = new HashMap<>();
        SitesConfigManager.setVncPasswords(site, map);
        Map<String, char[]> vnc = map.get("site-1").vncPasswords;
        assertArrayEquals("pw".toCharArray(), vnc.get(withPassword.getId()));
        assertFalse("no entry for a connection without password", vnc.containsKey(withoutPassword.getId()));

        // A password cleared (or a connection deleted) is gone from the map on the next save
        withPassword.setPassword(null);
        SitesConfigManager.setVncPasswords(site, map);
        assertTrue(map.get("site-1").vncPasswords.isEmpty());
    }
}
