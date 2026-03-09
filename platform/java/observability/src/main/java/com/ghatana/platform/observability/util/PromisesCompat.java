package com.ghatana.platform.observability.util;

import io.activej.promise.Promise;

/**
 * Compatibility helpers for ActiveJ Promise APIs in legacy or constrained contexts.
 * <p>
 * PromisesCompat provides bridge methods for scenarios where:
 * <ul>
 *   <li>Promise.ofBlocking(Executor, ...) is unavailable (older ActiveJ versions)</li>
 *   <li>Operation is already blocking at call site (no async benefit from executor)</li>
 *   <li>Simple synchronous wrapping is sufficient</li>
 * </ul>
 * </p>
 * 
 * <h2>⚠️ WARNING - Use Sparingly</h2>
 * <b>PREFER</b>: Promise.ofBlocking(executor, callable) for true async execution
 * <br>
 * <b>USE THIS ONLY WHEN</b>:
 * <ul>
 *   <li>ActiveJ version lacks ofBlocking() support</li>
 *   <li>Operation already executed synchronously (just wrapping result)</li>
 *   <li>Testing/mocking scenarios</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Compatibility bridge for Promise APIs in legacy or constrained contexts
 * @doc.layer core
 * @doc.pattern Compatibility, Adapter
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // ❌ WRONG - Blocks Eventloop thread!
 * public Promise<String> getData() {
 *     // This BLOCKS current thread (Eventloop thread)
 *     return PromisesCompat.runBlocking(() -> {
 *         return jedis.get("key");  // Blocking I/O on Eventloop!
 *     });
 * }
 *
 * // ✅ CORRECT - Use Promise.ofBlocking with executor
 * public Promise<String> getData() {
 *     // This runs in separate blocking thread pool
 *     return Promise.ofBlocking(blockingExecutor(), () -> {
 *         return jedis.get("key");  // Blocking I/O in worker thread
 *     });
 * }
 *
 * // ✅ OK - Wrapping already-computed result
 * public Promise<String> getCachedValue() {
 *     // Value already in memory, no I/O
 *     String cached = cache.get("key");
 *     return PromisesCompat.runBlocking(() -> cached);
 * }
 *
 * // ✅ OK - Testing/mocking
 * @Test
 * void testPromiseChain() {
 *     Promise<String> mockData = PromisesCompat.runBlocking(() -> "test-data");
 *     // Test promise chain without actual I/O
 * }
 * }</pre>
 * 
 * <h2>API</h2>
 * <ul>
 *   <li><b>runBlocking(Callable<T>)</b>: Execute callable synchronously, wrap result in Promise</li>
 *   <li>Returns Promise.of(result) on success</li>
 *   <li>Returns Promise.ofException(e) on failure</li>
 * </ul>
 * 
 * <h2>Execution Model</h2>
 * <pre>{@code
 * Thread timeline:
 * [Current Thread] → runBlocking() → callable.call() → return Promise
 *                                    ↑
 *                                    BLOCKS HERE (synchronous execution)
 * }</pre>
 * 
 * <b>Contrast with Promise.ofBlocking()</b>:
 * <pre>{@code
 * Thread timeline:
 * [Eventloop Thread] → ofBlocking() → submit to executor → return Promise
 * [Worker Thread]    →                 callable.call()   → complete Promise
 *                                       ↑
 *                                       BLOCKS WORKER, NOT EVENTLOOP
 * }</pre>
 * 
 * <h2>When to Use</h2>
 * <b>✅ Acceptable Use Cases</b>:
 * <ul>
 *   <li>Wrapping already-computed values (no I/O)</li>
 *   <li>Testing/mocking without real I/O</li>
 *   <li>Legacy code migration (temporary bridge)</li>
 *   <li>ActiveJ version compatibility (missing ofBlocking())</li>
 * </ul>
 * 
 * <b>❌ DO NOT USE FOR</b>:
 * <ul>
 *   <li>Database queries → Use Promise.ofBlocking(executor, ...)</li>
 *   <li>Redis/cache I/O → Use Promise.ofBlocking(executor, ...)</li>
 *   <li>HTTP requests → Use Promise.ofBlocking(executor, ...)</li>
 *   <li>File I/O → Use Promise.ofBlocking(executor, ...)</li>
 *   <li>Any blocking operation → Use Promise.ofBlocking(executor, ...)</li>
 * </ul>
 * 
 * <h2>Migration Path</h2>
 * If using runBlocking() for actual I/O, migrate to proper async:
 * 
 * <pre>{@code
 * // BEFORE (blocks Eventloop)
 * return PromisesCompat.runBlocking(() -> jedis.get("key"));
 *
 * // AFTER (async execution)
 * return Promise.ofBlocking(blockingExecutor(), () -> jedis.get("key"));
 * }</pre>
 * 
 * <h2>Performance Impact</h2>
 * <ul>
 *   <li><b>No async benefit</b>: Blocks calling thread</li>
 *   <li><b>Exception handling overhead</b>: try-catch on every call</li>
 *   <li><b>Eventloop starvation risk</b>: If called from Eventloop thread</li>
 * </ul>
 * 
 * <h2>Alternative: Promise.of()</h2>
 * For already-computed values, consider direct Promise.of():
 * 
 * <pre>{@code
 * // Using PromisesCompat
 * return PromisesCompat.runBlocking(() -> "value");
 *
 * // Simpler alternative
 * return Promise.of("value");
 * }</pre>
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable (legacy support)
 * @completeness 90
 * @testing Unit
 * @thread_safety Thread-safe (synchronous execution)
 * @performance Synchronous, no async benefit
 * @see io.activej.promise.Promise#ofBlocking
 * @see BlockingExecutors
 */
public final class PromisesCompat {
    private PromisesCompat() {}

    public static <T> Promise<T> runBlocking(java.util.concurrent.Callable<T> supplier) {
        try {
            return Promise.of(supplier.call());
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
}
