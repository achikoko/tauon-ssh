package tauon.app.settings;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import tauon.app.services.SitesConfigManager;
import tauon.app.services.SitesConfigManager.PasswordEntry;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** VNC passwords never go to the sites file: they live in the encrypted password map, keyed by connection id. */
public class VncConnectionStorageTest {

    private static SiteInfo siteWithVnc() {
        SiteInfo site = new SiteInfo();
        site.setId("site-1");
        site.setName("gpu box");
        VncConnection vnc = new VncConnection();
        vnc.setName("desktop");
        vnc.setHost("localhost");
        vnc.setPort(5901);
        vnc.setPassword("vnc-secret");
        site.getVncConnections().add(vnc);
        return site;
    }

    @Test
    public void passwordIsNotInTheSitesJson() throws Exception {
        SiteInfo site = siteWithVnc();
        String json = new ObjectMapper().writeValueAsString(site);
        assertFalse(json, json.contains("vnc-secret"));
        assertTrue(json, json.contains("\"vncConnections\""));
        assertTrue(json, json.contains("5901"));

        ObjectMapper reader = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SiteInfo back = reader.readValue(json, SiteInfo.class);
        VncConnection vnc = back.getVncConnections().get(0);
        assertEquals(site.getVncConnections().get(0).getId(), vnc.getId());
        assertEquals("desktop", vnc.getName());
        assertEquals(5901, vnc.getPort());
        assertNull(vnc.getPassword());
    }

    @Test
    public void passwordRoundTripsThroughThePasswordMap() throws Exception {
        SiteInfo site = siteWithVnc();
        String vncId = site.getVncConnections().get(0).getId();

        PasswordEntry entry = new PasswordEntry();
        entry.hopPasswords = new char[0][];
        entry.vncPasswords = new HashMap<>(Map.of(vncId, "vnc-secret".toCharArray()));
        char[] serialized = new ObjectMapper().writeValueAsString(Map.of("site-1", entry)).toCharArray();

        Map<String, PasswordEntry> map = SitesConfigManager.deserializePasswordMap(serialized);
        site.getVncConnections().get(0).setPassword(null);
        SessionFolder folder = new SessionFolder();
        folder.getItems().add(site);
        SitesConfigManager.populatePasswordsInto(folder, map);
        assertEquals("vnc-secret", site.getVncConnections().get(0).getPassword());
    }

    @Test
    public void passwordFilesFromBeforeVncStillLoad() throws Exception {
        char[] old = "{\"site-1\":{\"infoPassword\":\"cw==\",\"hopPasswords\":[]}}".toCharArray();
        Map<String, PasswordEntry> map = SitesConfigManager.deserializePasswordMap(old);
        assertNull(map.get("site-1").vncPasswords);

        SiteInfo site = siteWithVnc();
        site.getVncConnections().get(0).setPassword(null);
        SessionFolder folder = new SessionFolder();
        folder.getItems().add(site);
        SitesConfigManager.populatePasswordsInto(folder, map);
        assertNull(site.getVncConnections().get(0).getPassword());
    }

    @Test
    public void copyIsDeep() {
        SiteInfo site = siteWithVnc();
        SiteInfo copy = site.copy();
        VncConnection original = site.getVncConnections().get(0);
        VncConnection copied = copy.getVncConnections().get(0);
        assertNotSame(original, copied);
        assertEquals(original.getId(), copied.getId());
        assertEquals("vnc-secret", copied.getPassword());
        copied.setPort(5902);
        assertEquals(5901, original.getPort());
    }

    @Test
    public void displayIsThePortMinus5900() {
        VncConnection vnc = new VncConnection();
        vnc.setPort(5903);
        assertEquals(3, vnc.getDisplay());
        vnc.setPort(22);
        assertEquals(-1, vnc.getDisplay());
    }
}
