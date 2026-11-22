package tauon.app.vnc;

import tauon.app.util.misc.PlatformUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds the TurboVNC viewer on this computer: the path set in the settings, else the standard install locations.
 * <p>
 * {@code vncviewer} on the PATH is not used: it is often another VNC viewer (TigerVNC, ...), which doesn't understand
 * TurboVNC's options such as {@code -autopass}.
 */
public final class TurboVncLocator {

    /** Where to get it, for the "not installed" message. */
    public static final String DOWNLOAD_URL = "https://turbovnc.org/";

    private final String configuredPath;
    private final List<Path> candidates;

    /**
     * @param configuredPath the viewer set in the settings; null or blank to look in the standard locations
     */
    public TurboVncLocator(String configuredPath) {
        this(configuredPath, standardLocations());
    }

    TurboVncLocator(String configuredPath, List<Path> candidates) {
        this.configuredPath = configuredPath;
        this.candidates = candidates;
    }

    /** @return the viewer to run, if found */
    public Optional<Path> locate() {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path p = Paths.get(configuredPath.trim());
            return isRunnable(p) ? Optional.of(p) : Optional.empty();
        }
        return candidates.stream().filter(TurboVncLocator::isRunnable).findFirst();
    }

    /** @return the locations a TurboVNC installer uses on this OS, most likely first */
    public static List<Path> standardLocations() {
        List<Path> list = new ArrayList<>();
        if (PlatformUtils.IS_WINDOWS) {
            for (String env : new String[]{"ProgramFiles", "ProgramW6432", "ProgramFiles(x86)"}) {
                String dir = System.getenv(env);
                if (dir != null) {
                    // TurboVNC 3.x ships the Java viewer behind a .bat; 2.x had a native vncviewer.exe
                    list.add(Paths.get(dir, "TurboVNC", "vncviewer.bat"));
                    list.add(Paths.get(dir, "TurboVNC", "vncviewer.exe"));
                }
            }
            list.add(Paths.get("C:\\Program Files\\TurboVNC\\vncviewer.bat"));
            list.add(Paths.get("C:\\Program Files\\TurboVNC\\vncviewer.exe"));
        } else {
            // Linux packages and the macOS installer both put the launcher here
            list.add(Paths.get("/opt/TurboVNC/bin/vncviewer"));
        }
        return list;
    }

    private static boolean isRunnable(Path p) {
        return Files.isRegularFile(p) && (PlatformUtils.IS_WINDOWS || Files.isExecutable(p));
    }
}
