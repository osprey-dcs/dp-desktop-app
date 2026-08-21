package com.ospreydcs.dp.gui.testutil;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        } catch (IllegalStateException startupRejected) {
            // Platform.startup() throws IllegalStateException both when the toolkit is already
            // running (e.g., started outside this helper by a future TestFX dependency) and when
            // startup genuinely failed.  Probe with runLater, which executes only on a live
            // toolkit and itself throws when the toolkit never initialized — so a real startup
            // failure surfaces here once, with its cause, instead of poisoning every later
            // FX-thread call with opaque timeouts.
            try {
                Platform.runLater(ready::countDown);
            } catch (IllegalStateException toolkitNotRunning) {
                final IllegalStateException failure = new IllegalStateException(
                        "Platform.startup() was rejected but the JavaFX toolkit is not running",
                        startupRejected);
                failure.addSuppressed(toolkitNotRunning);
                throw failure;
            }
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
     * calling (test) thread, so JUnit reports it as the test's own failure.  On timeout the
     * queued action is cancelled so it cannot run late against discarded state or push its delay
     * onto actions queued behind it.
     */
    public static <T> T callOnFxThread(Callable<T> action) throws Exception {
        ensureStarted();
        if (Platform.isFxApplicationThread()) {
            // Run directly: queueing via runLater and blocking here would deadlock the FX
            // thread on itself.
            return action.call();
        }
        final FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        try {
            return task.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            task.cancel(true);
            throw new IllegalStateException(
                    "FX-thread action did not complete within " + TIMEOUT_SECONDS + " seconds");
        } catch (ExecutionException wrapped) {
            final Throwable failure = wrapped.getCause();
            if (failure instanceof Exception exception) {
                throw exception;
            }
            // Errors (AssertionError from JUnit assertions, most importantly) pass through as-is.
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("FX-thread action threw", failure);
        }
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
