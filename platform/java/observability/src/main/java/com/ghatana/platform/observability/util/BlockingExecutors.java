package com.ghatana.platform.observability.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized thread pool for blocking I/O operations in the observability module.
 * <p>
 * BlockingExecutors provides a shared ExecutorService for executing blocking operations
 * (database queries, Redis calls, HTTP requests) without blocking ActiveJ's Eventloop.
 * This prevents Eventloop starvation while maintaining controlled concurrency.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Fixed Thread Pool</b>: Size based on CPU cores (min 4 threads)</li>
 *   <li><b>Daemon Threads</b>: Won't prevent JVM shutdown</li>
 *   <li><b>Named Threads</b>: "observability-blocking-N" for debugging</li>
 *   <li><b>Shared Pool</b>: Single instance for all observability blocking operations</li>
 *   <li><b>Auto-Sized</b>: max(4, availableProcessors())</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Centralized thread pool for blocking I/O operations with CPU-aware sizing
 * @doc.layer core
 * @doc.pattern Thread Pool, Executor Factory
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // In RedisSessionManager, RedisCache, etc.
 * import static com.ghatana.observability.util.BlockingExecutors.blockingExecutor;
 *
 * public Promise<SessionState> getSession(String sessionId) {
 *     return Promise.ofBlocking(blockingExecutor(), () -> {
 *         // Blocking Redis call
 *         try (Jedis jedis = jedisPool.getResource()) {
 *             String json = jedis.get("session:" + sessionId);
 *             return deserializeSession(json);
 *         }
 *     });
 * }
 *
 * // Database query
 * public Promise<List<User>> findUsers() {
 *     return Promise.ofBlocking(blockingExecutor(), () -> {
 *         // Blocking JDBC query
 *         return userRepository.findAll();
 *     });
 * }
 *
 * // HTTP call
 * public Promise<String> fetchData() {
 *     return Promise.ofBlocking(blockingExecutor(), () -> {
 *         // Blocking HTTP request
 *         return httpClient.get("https://api.example.com/data");
 *     });
 * }
 * }</pre>
 * 
 * <h2>Why Separate Executor?</h2>
 * ActiveJ's Eventloop is single-threaded and must never block. Blocking operations
 * (I/O, database, network) must run in separate thread pool:
 * 
 * <pre>{@code
 * ❌ WRONG - Blocks Eventloop (starvation!)
 * public Promise<String> getData() {
 *     String data = jedis.get("key");  // BLOCKS Eventloop thread!
 *     return Promise.of(data);
 * }
 *
 * ✅ CORRECT - Runs in blocking pool
 * public Promise<String> getData() {
 *     return Promise.ofBlocking(blockingExecutor(), () -> {
 *         return jedis.get("key");  // Runs in separate thread
 *     });
 * }
 * }</pre>
 * 
 * <h2>Thread Pool Sizing</h2>
 * <ul>
 *   <li><b>Pool Size</b>: max(4, CPU cores)</li>
 *   <li><b>4-core machine</b>: 4 threads</li>
 *   <li><b>8-core machine</b>: 8 threads</li>
 *   <li><b>16-core machine</b>: 16 threads</li>
 * </ul>
 * 
 * <b>Why CPU-based?</b> Balances concurrency (I/O-bound) with resource usage.
 * For mostly I/O operations, can handle more concurrent tasks than CPU cores.
 * 
 * <h2>Thread Naming</h2>
 * Threads named "observability-blocking-N" (N = 1, 2, 3, ...) for:
 * <ul>
 *   <li>Easy identification in thread dumps</li>
 *   <li>Debugging blocked operations</li>
 *   <li>Performance profiling</li>
 * </ul>
 * 
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li><b>Creation</b>: Static initialization (JVM startup)</li>
 *   <li><b>Shutdown</b>: Daemon threads, auto-terminated on JVM exit</li>
 *   <li><b>No manual shutdown</b>: Application-scoped lifecycle</li>
 * </ul>
 * 
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>Task Submission</b>: O(1) via ThreadPoolExecutor queue</li>
 *   <li><b>Thread Overhead</b>: ~1MB per thread (stack size)</li>
 *   <li><b>Context Switch</b>: ~1-10μs per switch</li>
 *   <li><b>Queue</b>: Unbounded (LinkedBlockingQueue)</li>
 * </ul>
 * 
 * <h2>When to Use</h2>
 * Use blockingExecutor() for:
 * <ul>
 *   <li>Database queries (JDBC, JPA)</li>
 *   <li>Redis/cache operations (Jedis)</li>
 *   <li>HTTP client calls (synchronous APIs)</li>
 *   <li>File I/O</li>
 *   <li>Any blocking third-party library</li>
 * </ul>
 * 
 * <b>Do NOT use for</b>:
 * <ul>
 *   <li>CPU-intensive calculations (use ForkJoinPool)</li>
 *   <li>Already-async operations (Promise.of())</li>
 *   <li>Eventloop operations (run in Eventloop thread)</li>
 * </ul>
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit
 * @thread_safety Thread-safe (thread pool)
 * @performance Fixed pool, unbounded queue
 * @see io.activej.promise.Promise#ofBlocking
 */
public final class BlockingExecutors {

    private static final int CORE_POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());

    private static final ExecutorService BLOCKING_EXECUTOR = Executors.newFixedThreadPool(
        CORE_POOL_SIZE,
        new ThreadFactory() {
            private final AtomicInteger threadIndex = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "observability-blocking-" + threadIndex.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        }
    );

    private BlockingExecutors() {
    }

    public static ExecutorService blockingExecutor() {
        return BLOCKING_EXECUTOR;
    }
}
