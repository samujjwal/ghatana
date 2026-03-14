package com.ghatana.datacloud.testing;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.platform.testing.activej.EventloopTestUtil;
import io.activej.promise.Promises;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test isolation helper that wraps a {@link DataCloudClient} and automatically
 * deletes all entities saved during a test when {@link #close()} is called.
 *
 * <p>Manages its own ActiveJ eventloop ({@link EventloopTestUtil.EventloopRunner}),
 * so tests need not extend {@code EventloopTestBase} to use it. All public
 * methods are synchronous from the caller's perspective.
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * class MyServiceTest {
 *     private DataCloudTestContext ctx;
 *
 *     @BeforeEach
 *     void setUp() { ctx = DataCloudTestContext.create(); }
 *
 *     @AfterEach
 *     void tearDown() { ctx.close(); }   // delete all tracked entities
 *
 *     @Test
 *     void shouldPersistEntity() {
 *         Entity e = ctx.save("tenant-1", "orders", Map.of("id", "o1", "amount", 42));
 *         assertThat(e.id()).isEqualTo("o1");
 *
 *         Optional<Entity> found = ctx.findById("tenant-1", "orders", "o1");
 *         assertThat(found).isPresent();
 *     }
 * }
 * }</pre>
 *
 * <h2>Custom eventloop timeout</h2>
 * Use {@link #create(Duration)} to override the default 10-second promise timeout.
 *
 * @doc.type class
 * @doc.purpose Test isolation helper that auto-cleans saved entities on close()
 * @doc.layer product
 * @doc.pattern Test Support, AutoCloseable
 * @since 1.0.0
 */
public final class DataCloudTestContext implements AutoCloseable {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final EventloopTestUtil.EventloopRunner runner;
    private final DataCloudClient client;

    /**
     * Tracks: tenantId → collection → entityIds created during this context.
     * ConcurrentHashMap + synchronizedSet for safe multi-threaded operation appending
     * from any thread, but deletion always happens single-threadedly via the runner.
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Set<String>>> tracked =
            new ConcurrentHashMap<>();

    private DataCloudTestContext(Duration timeout) {
        this.runner = EventloopTestUtil.newRunnerBuilder()
                .timeout(timeout)
                .threadName("DataCloudTestContext-eventloop")
                .build();
        this.runner.start();
        this.client = DataCloud.forTesting();
    }

    // ==================== Factory Methods ====================

    /**
     * Create a test context backed by a fresh in-memory DataCloud client with
     * the default promise-resolution timeout of 10 seconds.
     *
     * @return new {@code DataCloudTestContext}
     */
    public static DataCloudTestContext create() {
        return new DataCloudTestContext(DEFAULT_TIMEOUT);
    }

    /**
     * Create a test context with a custom promise-resolution timeout.
     * Use this when tests involve many entities or slow CI machines.
     *
     * @param timeout maximum time to wait for any single async operation
     * @return new {@code DataCloudTestContext}
     */
    public static DataCloudTestContext create(Duration timeout) {
        return new DataCloudTestContext(timeout);
    }

    // ==================== Entity Operations (synchronous wrappers) ====================

    /**
     * Save an entity and track it for cleanup on {@link #close()}.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     * @param data       entity data (must contain an {@code "id"} key for predictable IDs,
     *                   or an auto-generated ID will be assigned and tracked)
     * @return saved entity
     */
    public Entity save(String tenantId, String collection, Map<String, Object> data) {
        Entity saved = runner.runPromise(() -> client.save(tenantId, collection, data));
        track(tenantId, collection, saved.id());
        return saved;
    }

    /**
     * Find an entity by ID. Does not affect the tracked-entity set.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     * @param id         entity ID
     * @return entity if present
     */
    public Optional<Entity> findById(String tenantId, String collection, String id) {
        return runner.runPromise(() -> client.findById(tenantId, collection, id));
    }

    /**
     * Query entities. Does not affect the tracked-entity set.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     * @param query      query specification
     * @return matching entities
     */
    public List<Entity> query(String tenantId, String collection, Query query) {
        return runner.runPromise(() -> client.query(tenantId, collection, query));
    }

    /**
     * Delete a specific entity and remove it from the tracking set.
     * If the entity was never tracked (e.g. pre-existing data), this is a no-op
     * for the tracking set but the delete is still issued.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     * @param id         entity ID
     */
    public void delete(String tenantId, String collection, String id) {
        runner.runPromise(() -> client.delete(tenantId, collection, id));
        untrack(tenantId, collection, id);
    }

    // ==================== Tracking Control ====================

    /**
     * Explicitly add an entity ID to the cleanup tracking set without saving data.
     * Useful when tests create entities via alternative code paths that don't go
     * through this context's {@link #save} method.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     * @param id         entity ID to track
     */
    public void track(String tenantId, String collection, String id) {
        tracked
            .computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>())
            .computeIfAbsent(collection, c -> ConcurrentHashMap.newKeySet())
            .add(id);
    }

    /**
     * Remove a specific entity from the tracking set so it will NOT be deleted
     * on {@link #close()}. Has no effect if the ID was never tracked.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     * @param id         entity ID to stop tracking
     */
    public void untrack(String tenantId, String collection, String id) {
        ConcurrentHashMap<String, Set<String>> tenantMap = tracked.get(tenantId);
        if (tenantMap != null) {
            Set<String> ids = tenantMap.get(collection);
            if (ids != null) {
                ids.remove(id);
            }
        }
    }

    /**
     * Returns an unmodifiable snapshot of the entity IDs currently tracked for
     * a given tenant + collection. Useful for assertions.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     * @return unmodifiable set of tracked entity IDs (empty if none)
     */
    public Set<String> trackedIds(String tenantId, String collection) {
        ConcurrentHashMap<String, Set<String>> tenantMap = tracked.get(tenantId);
        if (tenantMap == null) {
            return Set.of();
        }
        Set<String> ids = tenantMap.get(collection);
        return ids == null ? Set.of() : Collections.unmodifiableSet(ids);
    }

    // ==================== Direct Client Access ====================

    /**
     * Expose the underlying {@link DataCloudClient} for operations not covered
     * by this context's convenience methods. Entities created through the raw
     * client are NOT automatically tracked — call {@link #track} manually if
     * cleanup is required.
     *
     * @return the underlying client
     */
    public DataCloudClient client() {
        return client;
    }

    // ==================== Lifecycle ====================

    /**
     * Delete all tracked entities (in parallel per tenant/collection), then shut
     * down the eventloop runner and close the underlying client.
     *
     * <p>Idempotent: safe to call more than once (subsequent calls are no-ops).
     * All best-effort deletes are issued; individual failures are accumulated and
     * reported as a single {@link RuntimeException} after all deletes complete.
     */
    @Override
    public void close() {
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        tracked.forEach((tenantId, collectionMap) ->
            collectionMap.forEach((collection, ids) -> {
                if (ids.isEmpty()) {
                    return;
                }
                List<String> snapshot = new ArrayList<>(ids);
                snapshot.forEach(id ->
                    runner.runPromise(() ->
                        client.delete(tenantId, collection, id)
                              .whenException(failures::add)
                    )
                );
            })
        );

        tracked.clear();
        client.close();
        runner.close();

        if (!failures.isEmpty()) {
            RuntimeException root = new RuntimeException(
                "DataCloudTestContext.close() encountered " + failures.size() + " deletion failure(s)");
            failures.forEach(root::addSuppressed);
            throw root;
        }
    }
}
