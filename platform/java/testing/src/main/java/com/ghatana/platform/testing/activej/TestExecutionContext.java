package com.ghatana.platform.testing.activej;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test execution context that manages all resources and provides a unified cleanup mechanism.
 * This helps prevent hanging tests by ensuring all EventLoops, connections, and resources
 * are properly cleaned up even if tests fail or timeout.
 * 
 * @doc.type class
 * @doc.purpose Resource manager ensuring cleanup of all test resources and EventLoops
 * @doc.layer core
 * @doc.pattern Context Object, Resource Manager
 */
public final class TestExecutionContext implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TestExecutionContext.class);

    private final Map<String, AutoCloseable> resources = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<EventloopTestUtil.EventloopRunner> eventloopRunner = new AtomicReference<>();
    private final String testName;
    private final long startTime;

    public TestExecutionContext(String testName) {
        this.testName = testName;
        this.startTime = System.currentTimeMillis();
        log.debug("Created test execution context for: {}", testName);
    }

    /**
     * Create a new EventLoop runner with sensible defaults for testing.
     * The runner will be automatically closed when this context is closed.
     */
    public EventloopTestUtil.EventloopRunner createEventloopRunner() {
        return createEventloopRunner(Duration.ofSeconds(10), Duration.ofSeconds(2));
    }

    /**
     * Create a new EventLoop runner with custom timeouts.
     * The runner will be automatically closed when this context is closed.
     */
    public EventloopTestUtil.EventloopRunner createEventloopRunner(Duration timeout, Duration watchdogInterval) {
        if (eventloopRunner.get() != null) {
            throw new IllegalStateException("EventLoop runner already created for this context");
        }

        var runner = EventloopTestUtil.newRunnerBuilder()
            .timeout(timeout)
            .watchdogEvery(watchdogInterval)
            .threadName(testName + "-eventloop")
            .listener(new TestEventloopListener())
            .build();

        runner.start();
        eventloopRunner.set(runner);
        log.debug("Created EventLoop runner for test: {}", testName);
        return runner;
    }

    /**
     * Register a resource for automatic cleanup.
     * Resources are closed in reverse order of registration.
     */
    public <T extends AutoCloseable> T registerResource(String name, T resource) {
        if (closed.get()) {
            throw new IllegalStateException("Context already closed");
        }
        resources.put(name, resource);
        log.debug("Registered resource '{}' for test: {}", name, testName);
        return resource;
    }

    /**
     * Get a previously registered resource by name.
     */
    @SuppressWarnings("unchecked")
    public <T extends AutoCloseable> T getResource(String name, Class<T> type) {
        AutoCloseable resource = resources.get(name);
        if (resource == null) {
            return null;
        }
        if (!type.isInstance(resource)) {
            throw new IllegalArgumentException("Resource '" + name + "' is not of expected type " + type);
        }
        return (T) resource;
    }

    /**
     * Check if the context has been closed.
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Get the test execution duration so far.
     */
    public Duration getExecutionDuration() {
        return Duration.ofMillis(System.currentTimeMillis() - startTime);
    }

    @Override
    public void close() {
        if (closed.getAndSet(true)) {
            return; // Already closed
        }

        log.debug("Closing test execution context for: {} (duration: {}ms)", 
                 testName, System.currentTimeMillis() - startTime);

        // Close EventLoop runner first
        var runner = eventloopRunner.get();
        if (runner != null) {
            try {
                runner.close();
                log.debug("Closed EventLoop runner for test: {}", testName);
            } catch (Exception e) {
                log.warn("Error closing EventLoop runner for test: {}", testName, e);
            }
        }

        // Close all registered resources in reverse order
        resources.entrySet().stream()
            .sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey())) // Reverse order
            .forEach(entry -> {
                try {
                    entry.getValue().close();
                    log.debug("Closed resource '{}' for test: {}", entry.getKey(), testName);
                } catch (Exception e) {
                    log.warn("Error closing resource '{}' for test: {}", entry.getKey(), testName, e);
                }
            });

        resources.clear();
        log.debug("Test execution context closed for: {}", testName);
    }

    /**
     * Test-specific EventLoop listener that provides better debugging information.
     */
    private class TestEventloopListener implements EventloopTestUtil.EventloopListener {
        @Override
        public void onStart(String threadName) {
            log.debug("EventLoop started for test: {} on thread: {}", testName, threadName);
        }

        @Override
        public void onStuck(long elapsedMs) {
            log.warn("EventLoop stuck for test: {} (no progress for {} ms). Consider increasing timeout or checking for blocking operations.", 
                    testName, elapsedMs);
        }

        @Override
        public void onFatal(Throwable error) {
            log.error("Fatal error in EventLoop for test: {}", testName, error);
        }

        @Override
        public void onStop(String threadName, EventloopTestUtil.Inspector snapshot) {
            log.debug("EventLoop stopped for test: {} on thread: {}. Stats: {}", 
                     testName, threadName, snapshot.pretty());
        }
    }
}