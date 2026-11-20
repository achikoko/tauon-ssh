package tauon.app.util.misc;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Helpers to run code on the Swing Event Dispatch Thread, directly if already on it.
 * (Previously lived in the tauon-jediterm fork as com.intellij.util.ui.UIUtil.)
 */
public final class UIUtil {

    private UIUtil() {
    }

    public static void invokeAndWaitIfNeeded(@NotNull Runnable runnable) throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeAndWait(runnable);
        }
    }

    public static <R> R invokeAndWaitIfNeeded(@NotNull Supplier<R> supplier) throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            return supplier.get();
        } else {
            AtomicReference<R> ret = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> ret.set(supplier.get()));
            return ret.get();
        }
    }

    public static <T, R> R invokeAndWaitIfNeeded(@NotNull Function<T, R> function, T param) throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            return function.apply(param);
        } else {
            AtomicReference<R> ret = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> ret.set(function.apply(param)));
            return ret.get();
        }
    }

    public static void invokeLaterIfNeeded(@NotNull Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
