package tauon.app.util.misc;

import java.util.Collection;
import java.util.stream.Collectors;

/** Builds remote shell command lines from untrusted values (file names, paths, user input). */
public final class ShellUtils {

    private ShellUtils() {
    }

    /**
     * Quotes {@code value} as one POSIX shell word: wrapped in single quotes, each {@code '} written as
     * {@code '\''}. Nothing inside is expanded ({@code $}, backticks, {@code \}, {@code "}, globs, {@code ~}).
     */
    public static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /** @return each value {@link #quote quoted}, separated by spaces */
    public static String quoteAll(Collection<String> values) {
        return values.stream().map(ShellUtils::quote).collect(Collectors.joining(" "));
    }

    /**
     * Runs {@code script} with {@code sh}, so it behaves the same whatever the login shell is (fish, csh...) and,
     * under sudo, every command of it runs as root.
     */
    public static String sh(String script) {
        return "sh -c " + quote(script);
    }
}
