package com.ghatana.virtualorg.framework.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized thread pool for blocking I/O operations in the virtual-org
 * framework.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a shared ExecutorService for executing blocking operations (file
 * I/O, command execution, HTTP calls) without blocking ActiveJ's Eventloop.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * import static com.ghatana.virtualorg.framework.util.BlockingExecutors.blockingExecutor;
 *
 * public Promise<String> executeCommand(String command) {
 *     return Promise.ofBlocking(blockingExecutor(), () -> {
 *         // Blocking process execution
 *         Process process = Runtime.getRuntime().exec(command);
 *         return readOutput(process);
 *     });
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralized thread pool for blocking I/O operations
 * @doc.layer product
 * @doc.pattern Thread Pool
 */
public final class BlockingExecutors {

    private static final int POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());

    private static final ExecutorService BLOCKING_EXECUTOR = Executors.newFixedThreadPool(
            POOL_SIZE,
            new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "virtual-org-blocking-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
    );

    private BlockingExecutors() {
        // Utility class
    }

    /**
     * Gets the shared blocking executor for I/O operations.
     *
     * @return The blocking executor service
     */
    public static ExecutorService blockingExecutor() {
        return BLOCKING_EXECUTOR;
    }

    /**
     * Gets the thread pool size.
     *
     * @return Number of threads in the pool
     */
    public static int getPoolSize() {
        return POOL_SIZE;
    }
}
