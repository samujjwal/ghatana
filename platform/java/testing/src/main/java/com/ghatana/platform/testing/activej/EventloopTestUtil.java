package com.ghatana.platform.testing.activej;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test utility for running work on a dedicated ActiveJ Eventloop with: -
 * deterministic startup/shutdown - timeouts & watchdog - fatal error surface
 * (no silent hangs) - per-test logging hooks
 *
 * @doc.type class
 * @doc.purpose Core utility for managing EventloopRunner with timeout and
 * watchdog protection
 * @doc.layer core
 * @doc.pattern Utility, Test Support - basic execution stats via Inspector
 *
 * ActiveJ 6.x compatible.
 */
public final class EventloopTestUtil {

    private EventloopTestUtil() {
    }

    // region ===== Listener API =====
    public interface EventloopListener {

        default void onStart(String threadName) {
        }

        default void onProgress() {
        }

        default void onStuck(long elapsedMs) {
        }

        default void onFatal(Throwable error) {
        }

        default void onStop(String threadName, Inspector snapshot) {
        }
    }

    /**
     * Default SLF4J logger-backed listener.
     */
    public static final class Slf4jListener implements EventloopListener {

        private static final Logger log = LoggerFactory.getLogger(Slf4jListener.class);

        @Override
        public void onStart(String threadName) {
            log.info("Eventloop started on {}", threadName);
        }

        @Override
        public void onProgress() {
            /* noisy if logged; keep quiet */ }

        @Override
        public void onStuck(long elapsedMs) {
            log.warn("Eventloop appears stuck (no progress {} ms)", elapsedMs);
        }

        @Override
        public void onFatal(Throwable error) {
            log.error("Fatal error on eventloop", error);
        }

        @Override
        public void onStop(String threadName, Inspector snapshot) {
            log.info("Eventloop stopping on {}. {}", threadName, snapshot);
        }
    }
    // endregion

    // region ===== Inspector API =====
    /**
     * Minimal execution stats for debugging flaky tests.
     */
    public static final class Inspector {

        private final AtomicLong posts = new AtomicLong();
        private final AtomicLong promises = new AtomicLong();
        private final AtomicLong runsOk = new AtomicLong();
        private final AtomicLong runsErr = new AtomicLong();
        private final AtomicLong totalRunNanos = new AtomicLong();
        private final AtomicReference<Throwable> lastError = new AtomicReference<>();

        private long startNanos;
        private final String name;

        Inspector(String name) {
            this.name = name;
            this.startNanos = System.nanoTime();
        }

        void markPost() {
            posts.incrementAndGet();
        }

        void markPromise() {
            promises.incrementAndGet();
        }

        <T> T timedRun(Callable<T> c) throws Exception {
            long t0 = System.nanoTime();
            try {
                T r = c.call();
                runsOk.incrementAndGet();
                return r;
            } catch (Throwable e) {
                runsErr.incrementAndGet();
                lastError.set(e);
                if (e instanceof Exception) {
                    throw (Exception) e;
                }
                if (e instanceof Error) {
                    throw (Error) e;
                }
                throw new RuntimeException(e);
            } finally {
                totalRunNanos.addAndGet(System.nanoTime() - t0);
            }
        }

        void resetTimer() {
            startNanos = System.nanoTime();
        }

        public String pretty() {
            return toString();
        }

        @Override
        public String toString() {
            long secs = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos);
            return "Inspector[" + name + "]{"
                    + "posts=" + posts.get()
                    + ", promises=" + promises.get()
                    + ", runsOk=" + runsOk.get()
                    + ", runsErr=" + runsErr.get()
                    + ", totalRunMillis=" + TimeUnit.NANOSECONDS.toMillis(totalRunNanos.get())
                    + (lastError.get() != null ? ", lastError=" + lastError.get().getClass().getSimpleName() + ": " + lastError.get().getMessage() : "")
                    + ", observedForSec=" + secs
                    + '}';
        }
    }
    // endregion

    // region ===== Builder =====
    public static Builder newRunnerBuilder() {
        return new Builder();
    }

    public static final class Builder {

        private Duration timeout = Duration.ofSeconds(10);
        private Duration watchdogEvery = Duration.ofSeconds(2);
        private String threadName = "test-eventloop";
        private EventloopListener listener = new Slf4jListener();
        private boolean breakOnFatalError = true; // Default to current behavior

        public Builder timeout(Duration t) {
            this.timeout = Objects.requireNonNull(t);
            return this;
        }

        public Builder watchdogEvery(Duration d) {
            this.watchdogEvery = Objects.requireNonNull(d);
            return this;
        }

        public Builder threadName(String n) {
            this.threadName = Objects.requireNonNull(n);
            return this;
        }

        public Builder listener(EventloopListener l) {
            this.listener = Objects.requireNonNull(l);
            return this;
        }

        /**
         * Controls whether the eventloop should break when a fatal error occurs.
         * Set to false for tests that expect exceptions in Promise.ofBlocking()
         * and need the eventloop to continue running for retry logic.
         *
         * @param breakOnFatalError if true (default), eventloop breaks on fatal errors;
         *                          if false, fatal errors are logged but eventloop continues
         * @return this builder
         */
        public Builder breakOnFatalError(boolean breakOnFatalError) {
            this.breakOnFatalError = breakOnFatalError;
            return this;
        }

        public EventloopRunner build() {
            return new EventloopRunner(timeout, watchdogEvery, threadName, listener, breakOnFatalError);
        }
    }
    // endregion

    // region ===== Runner =====
    public static final class EventloopRunner implements AutoCloseable {

        private final Duration timeout;
        private final Duration watchdogInterval;
        private final String threadName;
        private final EventloopListener listener;
        private final boolean breakOnFatalError;

        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<Throwable> fatal = new AtomicReference<>();

        private Eventloop eventloop;
        private Thread loopThread;
        private ScheduledExecutorService watchdogExec;
        private volatile long lastProgressNanos;

        private final Inspector inspector;

        private EventloopRunner(Duration timeout, Duration watchdogInterval, String threadName, EventloopListener listener, boolean breakOnFatalError) {
            this.timeout = timeout;
            this.watchdogInterval = watchdogInterval;
            this.threadName = threadName;
            this.listener = listener;
            this.breakOnFatalError = breakOnFatalError;
            this.inspector = new Inspector(threadName);
        }

        /**
         * Clears any fatal error that was captured by the eventloop. Use this
         * in tests that expect exceptions from Promise operations. The fatal
         * error is stored when Promise.ofBlocking throws, and would otherwise
         * be re-thrown in close()/tearDown.
         *
         * @return The fatal error that was cleared, or null if none
         */
        public Throwable clearFatalError() {
            return fatal.getAndSet(null);
        }

        public synchronized void start() {
            if (started.get()) {
                return;
            }
            this.eventloop = Eventloop.builder()
                    .withCurrentThread()
                    .withFatalErrorHandler((e, context) -> {
                        fatal.compareAndSet(null, e);
                        listener.onFatal(e);
                        if (breakOnFatalError) {
                            eventloop.post(eventloop::breakEventloop);
                        }
                    })
                    .build();

            this.lastProgressNanos = System.nanoTime();
            this.eventloop.post(this::tickProgress);

            this.loopThread = new Thread(this.eventloop, threadName);
            this.loopThread.setDaemon(true);
            this.loopThread.setUncaughtExceptionHandler((t, e) -> {
                fatal.compareAndSet(null, e);
                listener.onFatal(e);
            });
            this.loopThread.start();
            listener.onStart(threadName);

            // watchdog
            this.watchdogExec = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread th = new Thread(r, threadName + "-watchdog");
                th.setDaemon(true);
                return th;
            });
            this.watchdogExec.scheduleAtFixedRate(this::checkStuck,
                    watchdogInterval.toMillis(), watchdogInterval.toMillis(), TimeUnit.MILLISECONDS);

            started.set(true);
        }

        private void tickProgress() {
            lastProgressNanos = System.nanoTime();
            listener.onProgress();
            // reschedule a heartbeat
            eventloop.schedule(eventloop.currentTimeMillis() + Math.max(1, watchdogInterval.toMillis() / 2), this::tickProgress);
        }

        private void checkStuck() {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastProgressNanos);
            if (elapsedMs > Math.max(2 * watchdogInterval.toMillis(), 1500)) {
                listener.onStuck(elapsedMs);
                IllegalStateException ise = new IllegalStateException("Eventloop stuck/no progress for " + elapsedMs + " ms\n"
                        + dumpThread() + "\n" + inspector.pretty());
                fatal.compareAndSet(null, ise);
                if (eventloop != null) {
                    eventloop.post(eventloop::breakEventloop);
                }
            }
        }

        private String dumpThread() {
            Thread t = loopThread;
            if (t == null) {
                return "Thread dump: <no thread>";
            }
            StringBuilder sb = new StringBuilder("Thread dump [")
                    .append(t.getName()).append(" state=").append(t.getState()).append("]\n");
            for (StackTraceElement el : t.getStackTrace()) {
                sb.append("  at ").append(el).append('\n');
            }
            return sb.toString();
        }

        private void ensureStarted() {
            if (!started.get()) {
                start();
        
            }}

        // ---- Public helpers -----------------------------------------------------
        public Eventloop eventloop() {
            ensureStarted();
            return eventloop;
        }

        public Inspector inspector() {
            return inspector;
        }

        /**
         * Run a task on the eventloop and wait for completion.
         */
        public void runBlocking(Runnable runnable) {
            runBlocking(Executors.callable(runnable));
        }

        /**
         * Run a Callable on the eventloop and return its result.
         */
        public <T> T runBlocking(Callable<T> callable) {
            ensureStarted();
            inspector.markPost();

            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<T> resultRef = new AtomicReference<>();
            AtomicReference<Throwable> errRef = new AtomicReference<>();

            eventloop.execute(() -> {
                try {
                    T r = inspector.timedRun(callable);
                    resultRef.set(r);
                } catch (Throwable t) {
                    errRef.set(t);
                } finally {
                    done.countDown();
                }
            });

            await(done);
            propagateIfAny(errRef.get());
            propagateIfAny(fatal.get());
            return resultRef.get();
        }

        /**
         * Run a Promise-producing Callable on the eventloop and await the
         * resolved value.
         */
        public <T> T runPromise(Callable<Promise<T>> promiseCallable) {
            ensureStarted();
            inspector.markPromise();

            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<T> resultRef = new AtomicReference<>();
            AtomicReference<Throwable> errRef = new AtomicReference<>();

            eventloop.execute(() -> {
                try {
                    Promise<T> p = inspector.timedRun(promiseCallable);
                    if (p == null) {
                        errRef.set(new NullPointerException("Promise is null"));
                        done.countDown();
                        return;
                    }
                    p.whenComplete((res, err) -> {
                        if (err != null) {
                            errRef.set(err); 
                        }else {
                            resultRef.set(res);
                        }
                        done.countDown();
                    });
                } catch (Throwable t) {
                    errRef.set(t);
                    done.countDown();
                }
            });

            await(done);
            propagateIfAny(errRef.get());
            // Only propagate fatal error if breakOnFatalError is true
            // AND the Promise itself didn't complete successfully.
            // This allows retry logic to work even when intermediate exceptions occur.
            if (breakOnFatalError) {
                propagateIfAny(fatal.get());
            }
            return resultRef.get();
        }

        private void await(CountDownLatch done) {
            try {
                if (!done.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    String msg = "Timed out after " + timeout + " waiting on Eventloop\n"
                            + dumpThread() + "\n" + inspector.pretty();
                    throw new RuntimeException(new TimeoutException(msg));
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while awaiting eventloop task", ie);
            }
        }

        private void propagateIfAny(Throwable t) {
            if (t != null) {
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                }
                if (t instanceof Error) {
                    throw (Error) t;
                }
                throw new RuntimeException(t);
            }
        }

        @Override
        public synchronized void close() {
            if (closed.getAndSet(true)) {
                return;
            }
            try {
                if (eventloop != null) {
                    eventloop.execute(eventloop::breakEventloop);
                }
                if (loopThread != null) {
                    loopThread.join(Math.max(500, timeout.toMillis()));
                    if (loopThread.isAlive()) {
                        loopThread.interrupt();
                        loopThread.join(500);
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                if (watchdogExec != null) {
                    watchdogExec.shutdownNow();
                }
                listener.onStop(threadName, inspector);
            }
            if (fatal.get() != null) {
                propagateIfAny(fatal.get());
            }
        }
    }
    // endregion
}
