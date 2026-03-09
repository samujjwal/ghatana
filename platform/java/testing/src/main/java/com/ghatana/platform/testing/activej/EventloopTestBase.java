package com.ghatana.platform.testing.activej;

import com.ghatana.platform.testing.activej.EventloopTestUtil;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;

/**
 * JUnit 5 base class that manages a dedicated ActiveJ Eventloop per test.
 * Subclasses get direct access to {@link #runner}, {@link #eventloop()}, and
 * helpers like {@link #runBlocking(Runnable)} and
 * {@link #runPromise(Callable)}.
 *
 * Override hooks to customize timeout, watchdog cadence, thread name, and
 * listener.
 *
 * @doc.type class
 * @doc.purpose Base class for tests using managed ActiveJ Eventloop with
 * runPromise() helpers
 * @doc.layer core
 * @doc.pattern Base Class, Test Support
 */
public abstract class EventloopTestBase {

    protected EventloopTestUtil.EventloopRunner runner;

    // ---- Customization hooks (override as needed) -----------------------------
    protected Duration eventloopTimeout() {
        return Duration.ofSeconds(10);
    }

    protected Duration watchdogEvery() {
        return Duration.ofSeconds(2);
    }

    protected String threadName() {
        return this.getClass().getSimpleName() + "-eventloop";
    }

    protected EventloopTestUtil.EventloopListener listener() {
        return new EventloopTestUtil.Slf4jListener();
    }

    /**
     * Controls whether the eventloop should break when a fatal error occurs.
     * Override to return false for tests that expect exceptions in Promise.ofBlocking()
     * and need retry logic to complete.
     *
     * @return true to break eventloop on fatal errors (default), false to continue
     */
    protected boolean breakOnFatalError() {
        return true; // Default: maintain current behavior
    }

    @BeforeEach
    protected void setUpEventloop() {
        runner = EventloopTestUtil.newRunnerBuilder()
                .timeout(eventloopTimeout())
                .watchdogEvery(watchdogEvery())
                .threadName(threadName())
                .listener(listener())
                .breakOnFatalError(breakOnFatalError())
                .build();
        runner.start();
    }

    @AfterEach
    protected void tearDownEventloop() {
        if (runner != null) {
            runner.close();
            runner = null;
        }
    }

    // ---- Convenience accessors -----------------------------------------------
    protected Eventloop eventloop() {
        return runner.eventloop();
    }

    protected EventloopTestUtil.Inspector inspector() {
        return runner.inspector();
    }

    protected void runBlocking(Runnable r) {
        runner.runBlocking(r);
    }

    protected <T> T runBlocking(java.util.concurrent.Callable<T> c) {
        return runner.runBlocking(c);
    }

    protected <T> T runPromise(java.util.concurrent.Callable<Promise<T>> c) {
        return runner.runPromise(c);
    }

    /**
     * Clears any fatal error captured by the eventloop during Promise
     * operations. Use this in tests that EXPECT exceptions from Promise
     * operations. Without clearing, the fatal error would be re-thrown in
     * @AfterEach.
     *
     * @return The fatal error that was cleared, or null if none
     */
    protected Throwable clearFatalError() {
        return runner.clearFatalError();
    }

    /**
     * Creates a promise that completes after the specified delay in
     * milliseconds. Useful for testing asynchronous operations with timing.
     *
     * @param delayMillis Delay in milliseconds
     * @return Promise that completes after the delay
     */
    protected Promise<Void> eventloopDelayMillis(long delayMillis) {
        return Promise.ofCallback(cb -> eventloop().delay(delayMillis, () -> cb.set(null)));
    }
}
