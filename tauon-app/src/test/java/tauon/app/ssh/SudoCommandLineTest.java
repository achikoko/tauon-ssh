package tauon.app.ssh;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import tauon.app.util.misc.PlatformUtils;
import tauon.app.util.misc.ShellUtils;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Runs the full command line {@link SSHCommandRunner} sends for a sudo command, with a fake {@code sudo} on the
 * {@code PATH} that drops sudo's options and runs the rest, as the remote login shell would.
 */
public class SudoCommandLineTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File bin;
    private File dir;

    @Before
    public void setUp() throws Exception {
        Assume.assumeFalse(PlatformUtils.IS_WINDOWS);
        bin = tmp.newFolder("bin");
        dir = tmp.newFolder("files");
        File sudo = new File(bin, "sudo");
        // sudo -S [-E] -p PROMPT command...  ->  command...
        Files.writeString(sudo.toPath(), "#!/bin/sh\n"
                + "while [ $# -gt 0 ]; do case \"$1\" in -S|-E) shift;; -p) shift 2;; *) break;; esac; done\n"
                + "exec \"$@\"\n");
        assertTrue(sudo.setExecutable(true));
    }

    private int runAsRemoteShell(String commandLine) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", commandLine).inheritIO();
        pb.environment().put("PATH", bin.getAbsolutePath() + File.pathSeparator + System.getenv("PATH"));
        pb.environment().put("odd", "EXPANDED"); // would show up in the name if $odd were expanded
        return pb.start().waitFor();
    }

    @Test
    public void sudoRenameKeepsDollarSigns() throws Exception {
        File old = new File(dir, "old");
        assertTrue(old.createNewFile());
        String newName = new File(dir, "ola $odd").getAbsolutePath();

        // As SshFileOperations.renameWithPrivilege builds it
        String command = "mv " + ShellUtils.quote(old.getAbsolutePath()) + " " + ShellUtils.quote(newName);
        assertEquals(0, runAsRemoteShell(SSHCommandRunner.sudoCommandLine("a1b2-c3", command, false)));

        assertTrue(new File(newName).exists());
        assertArrayEquals(new String[]{"ola $odd"}, dir.list());
    }

    @Test
    public void everyCommandOfAChainedLineRunsUnderSudo() throws Exception {
        // With "sudo a; b" only "a" ran as root: the whole line must be one sudo argument
        String marker = new File(dir, "ran").getAbsolutePath();
        String command = "true; touch " + ShellUtils.quote(marker);
        String line = SSHCommandRunner.sudoCommandLine("p", command, true);
        assertTrue(line, line.startsWith("sudo -S -E -p 'p' sh -c '"));
        assertEquals(0, runAsRemoteShell(line));
        assertTrue(new File(marker).exists());
    }
}
