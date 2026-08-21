package com.ospreydcs.dp.gui.testutil;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts the JavaFX toolkit for tests that load FXML or instantiate controls, and runs test
 * bodies on the FX application thread.
 *
 * The platform is started at most once per JVM: surefire reuses a single fork across test
 * classes, and Platform.startup() throws IllegalStateException on a second call.  Implicit
 * exit is disabled so the toolkit does not shut itself down between test classes just because
 * no window is showing — none ever shows, since these tests never call show(), which is also
 * what keeps local runs windowless.  On CI (no display) the whole build runs under xvfb-run;
 * see .github/workflows/ci.yml.
 */
public final class FxToolkitSupport {

    private static final long TIMEOUT_SECONDS = 30;

    private static boolean started = false;

    private FxToolkitSupport() {
    }

    /**
     * Starts the JavaFX platform if this JVM has not started it yet.
     */
    public static synchronized void ensureStarted() throws InterruptedException {
        if (started) {
            return;
        }
        final CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyRunning) {
            // Started outside this helper (e.g., by a future TestFX dependency); treat as ready.
            ready.countDown();
        }
        if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "JavaFX platform did not start within " + TIMEOUT_SECONDS + " seconds");
        }
        Platform.setImplicitExit(false);
        started = true;
    }

    /**
     * Runs the action on the FX application thread and returns its result.  Anything the action
     * throws — including assertion failures from JUnit assertions inside it — is rethrown on the
     * calling (test) thread, so JUnit reports it as the test's own failure.
     */
    public static <T> T callOnFxThread(Callable<T> action) throws Exception {
        ensureStarted();
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable throwable) {
                thrown.set(throwable);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "FX-thread action did not complete within " + TIMEOUT_SECONDS + " seconds");
        }
        final Throwable failure = thrown.get();
        if (failure != null) {
            if (failure instanceof Exception exception) {
                throw exception;
            }
            // Errors (AssertionError from JUnit assertions, most importantly) pass through as-is.
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("FX-thread action threw", failure);
        }
        return result.get();
    }

    /**
     * Runs the action on the FX application thread, rethrowing anything it throws.
     */
    public static void runOnFxThread(FxAction action) throws Exception {
        callOnFxThread(() -> {
            action.run();
            return null;
        });
    }

    @FunctionalInterface
    public interface FxAction {
        void run() throws Exception;
    }
}
