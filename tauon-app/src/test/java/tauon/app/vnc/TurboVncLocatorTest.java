package tauon.app.vnc;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class TurboVncLocatorTest {

    private static Path executable(Path dir, String name) throws IOException {
        Path p = Files.createFile(dir.resolve(name));
        assertTrue(p.toFile().setExecutable(true));
        return p;
    }

    @Test
    public void firstInstalledStandardLocationWins() throws IOException {
        Path dir = Files.createTempDirectory("turbovnc");
        Path missing = dir.resolve("nope");
        Path found = executable(dir, "vncviewer");
        assertEquals(Optional.of(found), new TurboVncLocator(null, List.of(missing, found)).locate());
        assertEquals(Optional.of(found), new TurboVncLocator("  ", List.of(missing, found)).locate());
    }

    @Test
    public void configuredPathOverridesTheStandardLocations() throws IOException {
        Path dir = Files.createTempDirectory("turbovnc");
        Path standard = executable(dir, "standard");
        Path custom = executable(dir, "custom");
        assertEquals(Optional.of(custom), new TurboVncLocator(custom.toString(), List.of(standard)).locate());
    }

    @Test
    public void aWrongConfiguredPathIsNotFoundEvenIfAStandardOneExists() throws IOException {
        Path dir = Files.createTempDirectory("turbovnc");
        Path standard = executable(dir, "standard");
        assertEquals(Optional.empty(), new TurboVncLocator(dir.resolve("typo").toString(), List.of(standard)).locate());
    }

    @Test
    public void nothingInstalled() {
        assertEquals(Optional.empty(), new TurboVncLocator(null, List.of(Path.of("/nonexistent/vncviewer"))).locate());
    }
}
