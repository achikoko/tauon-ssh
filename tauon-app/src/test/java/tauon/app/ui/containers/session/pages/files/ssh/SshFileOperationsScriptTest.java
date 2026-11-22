package tauon.app.ui.containers.session.pages.files.ssh;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import tauon.app.ssh.filesystem.FileInfo;
import tauon.app.ssh.filesystem.FileType;
import tauon.app.util.misc.PlatformUtils;
import tauon.app.util.misc.ShellUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Runs the move/copy scripts with the local {@code sh} against a temporary folder. */
public class SshFileOperationsScriptTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File src;
    private File dst;

    @Before
    public void setUp() throws Exception {
        Assume.assumeFalse(PlatformUtils.IS_WINDOWS);
        src = tmp.newFolder("src");
        dst = tmp.newFolder("dst");
    }

    private FileInfo file(String name) throws Exception {
        File f = new File(src, name);
        Files.writeString(f.toPath(), name);
        return new FileInfo(name, f.getAbsolutePath(), f.length(), FileType.FILE, 0, 0, "local", "", 0, "", false);
    }

    private static int run(String script) throws Exception {
        // As SshFileOperations does: instance.exec(ShellUtils.sh(script))
        return new ProcessBuilder("sh", "-c", ShellUtils.sh(script)).inheritIO().start().waitFor();
    }

    @Test
    public void hostileNamesAreMovedAndNothingElseHappens() throws Exception {
        List<FileInfo> files = new ArrayList<>();
        for (String name : List.of("a\" ~ \"b", "$(touch pwned)", "`touch pwned2`", "it's")) {
            files.add(file(name));
        }

        String script = SshFileOperations.transferScript(files, "mv", "mv -T", dst.getAbsolutePath(),
                List.of(), List.of(), false);
        assertEquals(0, run(script));

        for (FileInfo f : files) {
            assertTrue(f.getName(), new File(dst, f.getName()).isFile());
        }
        assertEquals(0, src.list().length);
        assertFalse(new File("pwned").exists());
        assertFalse(new File(dst, "pwned").exists());
        assertFalse(new File("pwned2").exists());
    }

    @Test
    public void aFailureInTheMiddleIsReportedAndTheRestStillRuns() throws Exception {
        FileInfo first = file("first");
        FileInfo missing = new FileInfo("missing", new File(src, "missing").getAbsolutePath(), 0, FileType.FILE,
                0, 0, "local", "", 0, "", false);
        FileInfo last = file("last");

        String script = SshFileOperations.transferScript(List.of(first, missing, last), "cp -rf", "cp -Tf",
                dst.getAbsolutePath(), List.of(), List.of(), false);

        assertNotEquals("the missing file's failure must not be hidden by the last success", 0, run(script));
        assertTrue(new File(dst, "first").isFile());
        assertTrue(new File(dst, "last").isFile());
    }

    @Test
    public void autoRenameGivesDuplicatesAUniqueName() throws Exception {
        FileInfo f = file("same");
        Files.writeString(new File(dst, "same").toPath(), "existing");
        FileInfo existing = new FileInfo("same", new File(dst, "same").getAbsolutePath(), 8, FileType.FILE,
                0, 0, "local", "", 0, "", false);

        String script = SshFileOperations.transferScript(List.of(f), "cp -rf", "cp -Tf", dst.getAbsolutePath(),
                List.of(existing), List.of(f), true);
        assertEquals(0, run(script));
        assertEquals("existing", Files.readString(new File(dst, "same").toPath()));
        assertEquals("same", Files.readString(new File(dst, "Copy of same").toPath()));
    }
}
