package tauon.app.util.misc;

import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ShellUtilsTest {

    /** Names that broke the old {@code "..."} quoting, plus the usual suspects. */
    static final List<String> HOSTILE = List.of(
            "a\" ~ \"b", "$(touch pwned)", "`id`", "it's", "a b", "back\\slash", "$HOME", "*", "-rf", "~", "!x", "a;b|c&d"
    );

    /** Runs {@code command} with {@code sh -c} and returns its stdout. */
    static String sh(String command) throws Exception {
        Process p = new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(out);
        }
        p.waitFor();
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void quotedValuesReachTheCommandUnchanged() throws Exception {
        Assume.assumeFalse(PlatformUtils.IS_WINDOWS);
        for (String value : HOSTILE) {
            assertEquals(value, sh("printf %s " + ShellUtils.quote(value)));
        }
    }

    @Test
    public void quoteAllKeepsOneWordPerValue() throws Exception {
        Assume.assumeFalse(PlatformUtils.IS_WINDOWS);
        assertEquals(HOSTILE.size() + "\n", sh("set -- " + ShellUtils.quoteAll(HOSTILE) + "; echo $#"));
    }

    @Test
    public void shRunsTheWholeScriptAsOneArgument() throws Exception {
        Assume.assumeFalse(PlatformUtils.IS_WINDOWS);
        // As SSHCommandRunner does under sudo: "sudo ... sh -c '<script>'" runs every command of the script
        String script = "printf %s " + ShellUtils.quote("it's") + "; printf %s " + ShellUtils.quote("$(x)");
        assertEquals("it's$(x)", sh(ShellUtils.sh(script)));
    }

    @Test
    public void quoteFormat() {
        assertEquals("'a b'", ShellUtils.quote("a b"));
        assertEquals("'it'\\''s'", ShellUtils.quote("it's"));
        assertEquals("''", ShellUtils.quote(""));
    }
}
