package com.ghatana.datacloud.infrastructure.cache;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CachingEntityRepository}.
 *
 * <p>Uses Mockito to stub the delegate {@link EntityRepository} so that
 * tests are fully isolated from any database. Extends {@link EventloopTestBase}
 * so that {@code runPromise()} correctly drives ActiveJ Promises.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CachingEntityRepository Tests")
class CachingEntityRepositoryTest extends EventloopTestBase {

    private static final String TENANT = "tenant-1";
    private static final String COLLECTION = "orders";

    @Mock
    private EntityRepository delegate;

    private SimpleMeterRegistry meterRegistry;
    private CachingEntityRepository cache;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // Very short TTL (200 ms) so we can test expiry behaviour without sleeping long
        cache = new CachingEntityRepository(delegate, meterRegistry,
                Duration.ofMillis(200), 100L);
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("null delegate throws NullPointerException")
        void nullDelegateFails() {
            assertThatThrownBy(() ->
                    new CachingEntityRepository(null, meterRegistry))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("zero TTL throws IllegalArgumentException")
        void zeroTtlFails() {
            assertThatThrownBy(() ->
                    new CachingEntityRepository(delegate, meterRegistry,
                            Duration.ZERO, 10L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("negative TTL throws IllegalArgumentException")
        void negativeTtlFails() {
            assertThatThrownBy(() ->
                    new CachingEntityRepository(delegate, meterRegistry,
                            Duration.ofSeconds(-1), 10L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("maxSize zero throws IllegalArgumentException")
        void zeroMaxSizeFails() {
            assertThatThrownBy(() ->
                    new CachingEntityRepository(delegate, meterRegistry,
                            Duration.ofSeconds(5), 0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null MeterRegistry is accepted (no metrics)")
        void nullMeterRegistryIsAccepted() {
            // should not throw
            var c = new CachingEntityRepository(delegate, null);
            assertThat(c.estimatedSize()).isZero();
        }
    }

    // =========================================================================
    // findById — cache-aside logic
    // =========================================================================

    @Nested
    @DisplayName("findById — cache-aside behaviour")
    class FindByIdTests {

        @Test
        @DisplayName("first call is a miss → delegates to repository")
        void firstCallMissesAndDelegates() {
            UUID id = UUID.randomUUID();
            Entity entity = stubEntity(id);
            when(delegate.findById(TENANT, COLLECTION, id))
                    .thenReturn(Promise.of(Optional.of(entity)));

            Optional<Entity> result = runPromise(() ->
                    cache.findById(TENANT, COLLECTION, id));

            assertThat(result).contains(entity);
            verify(delegate, times(1)).findById(TENANT, COLLECTION, id);
        }

        @Test
        @DisplayName("second call with same key is a hit → delegate not called again")
        void secondCallHitsCache() {
            UUID id = UUID.randomUUID();
            Entity entity = stubEntity(id);
            when(delegate.findById(TENANT, COLLECTION, id))
                    .thenReturn(Promise.of(Optional.of(entity)));

            // First call — populates cache
            runPromise(() -> cache.findById(TENANT, COLLECTION, id));
            // Second call — should hit cache
            Optional<Entity> secondResult = runPromise(() ->
                    cache.findById(TENANT, COLLECTION, id));

            assertThat(secondResult).contains(entity);
            // delegate was called exactly once despite two cache.findById calls
            verify(delegate, times(1)).findById(TENANT, COLLECTION, id);
        }

        @Test
        @DisplayName("empty optional is also cached to prevent repeated DB misses")
        void emptyOptionalIsCached() {
            UUID id = UUID.randomUUID();
            when(delegate.findById(TENANT, COLLECTION, id))
                    .thenReturn(Promise.of(Optional.empty()));

            runPromise(() -> cache.findById(TENANT, COLLECTION, id));
            Optional<Entity> second = runPromise(() -> cache.findById(TENANT, COLLECTION, id));

            assertThat(second).isEmpty();
            verify(delegate, times(1)).findById(TENANT, COLLECTION, id);
        }

        @Test
        @DisplayName("different entity IDs produce independent cache entries")
        void differentIdsAreIndependent() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            Entity e1 = stubEntity(id1);
            Entity e2 = stubEntity(id2);
            when(delegate.findById(TENANT, COLLECTION, id1)).thenReturn(Promise.of(Optional.of(e1)));
            when(delegate.findById(TENANT, COLLECTION, id2)).thenReturn(Promise.of(Optional.of(e2)));

            Optional<Entity> r1 = runPromise(() -> cache.findById(TENANT, COLLECTION, id1));
            Optional<Entity> r2 = runPromise(() -> cache.findById(TENANT, COLLECTION, id2));
            Optional<Entity> r1Again = runPromise(() -> cache.findById(TENANT, COLLECTION, id1));

            assertThat(r1).contains(e1);
            assertThat(r2).contains(e2);
            assertThat(r1Again).contains(e1);
            verify(delegate, times(1)).findById(TENANT, COLLECTION, id1);
            verify(delegate, times(1)).findById(TENANT, COLLECTION, id2);
        }

        @Test
        @DisplayName("different tenants produce independent cache entries (multi-tenancy)")
        void differentTenantsAreIndependent() {
            UUID id = UUID.randomUUID();
            Entity e1 = stubEntity(id);
            Entity e2 = stubEntity(id);
            when(delegate.findById("tenant-A", COLLECTION, id)).thenReturn(Promise.of(Optional.of(e1)));
            when(delegate.findById("tenant-B", COLLECTION, id)).thenReturn(Promise.of(Optional.of(e2)));

            Optional<Entity> rA = runPromise(() -> cache.findById("tenant-A", COLLECTION, id));
            Optional<Entity> rB = runPromise(() -> cache.findById("tenant-B", COLLECTION, id));

            assertThat(rA).contains(e1);
            assertThat(rB).contains(e2);
            // Both tenants miss independently
            verify(delegate, times(1)).findById("tenant-A", COLLECTION, id);
            verify(delegate, times(1)).findById("tenant-B", COLLECTION, id);
        }

        @Test
        @DisplayName("TTL expiry causes re-fetch from delegate")
        void ttlExpiryRecallsDelegate() throws InterruptedException {
            UUID id = UUID.randomUUID();
            Entity entity = stubEntity(id);
            when(delegate.findById(TENANT, COLLECTION, id))
                    .thenReturn(Promise.of(Optional.of(entity)));

            // Populate cache
            runPromise(() -> cache.findById(TENANT, COLLECTION, id));
            // Wait for TTL (200 ms) to expire
            Thread.sleep(350);
            // After expiry, delegate must be called again
            runPromise(() -> cache.findById(TENANT, COLLECTION, id));

            verify(delegate, times(2)).findById(TENANT, COLLECTION, id);
        }
    }

    // =========================================================================
    // save — delegation + invalidation
    // =========================================================================

    @Nested
    @DisplayName("save — delegate + invalidate")
    class SaveTests {

        @Test
        @DisplayName("save delegates to backing repository")
        void saveDelegates() {
            UUID id = UUID.randomUUID();
            Entity entity = stubEntity(id);
            when(delegate.save(TENANT, entity)).thenReturn(Promise.of(entity));

            Entity saved = runPromise(() -> cache.save(TENANT, entity));

            assertThat(saved).isSameAs(entity);
            verify(delegate).save(TENANT, entity);
        }

        @Test
        @DisplayName("save invalidates the cached findById entry for the entity")
        void saveInvalidatesCacheEntry() {
            UUID id = UUID.randomUUID();
            Entity v1 = stubEntity(id);
            Entity v2 = stubEntity(id);

            when(delegate.findById(TENANT, COLLECTION, id))
                    .thenReturn(Promise.of(Optional.of(v1)))
                    .thenReturn(Promise.of(Optional.of(v2)));
            when(delegate.save(TENANT, v2)).thenReturn(Promise.of(v2));

            // Populate cache with v1
            runPromise(() -> cache.findById(TENANT, COLLECTION, id));
            // Save v2 — should invalidate the v1 entry
            runPromise(() -> cache.save(TENANT, v2));
            // Next read should miss and get v2 from delegate
            Optional<Entity> afterSave = runPromise(() -> cache.findById(TENANT, COLLECTION, id));

            assertThat(afterSave).contains(v2);
            // delegate.findById was called twice (before and after save)
            verify(delegate, times(2)).findById(TENANT, COLLECTION, id);
        }
    }

    // =========================================================================
    // delete — delegation + invalidation
    // =========================================================================

    @Nested
    @DisplayName("delete — delegate + invalidate")
    class DeleteTests {

        @Test
        @DisplayName("delete delegates to backing repository")
        void deleteDelegates() {
            UUID id = UUID.randomUUID();
            when(delegate.delete(TENANT, COLLECTION, id)).thenReturn(Promise.of(null));

            runPromise(() -> cache.delete(TENANT, COLLECTION, id));

            verify(delegate).delete(TENANT, COLLECTION, id);
        }

        @Test
        @DisplayName("delete invalidates the cached findById entry")
        void deleteInvalidatesCacheEntry() {
            UUID id = UUID.randomUUID();
            Entity entity = stubEntity(id);

            when(delegate.findById(TENANT, COLLECTION, id))
                    .thenReturn(Promise.of(Optional.of(entity)))
                    .thenReturn(Promise.of(Optional.empty()));
            when(delegate.delete(TENANT, COLLECTION, id)).thenReturn(Promise.of(null));

            // Warm the cache
            runPromise(() -> cache.findById(TENANT, COLLECTION, id));
            // Delete
            runPromise(() -> cache.delete(TENANT, COLLECTION, id));
            // Should miss cache and get empty from delegate
            Optional<Entity> afterDelete = runPromise(() -> cache.findById(TENANT, COLLECTION, id));

            assertThat(afterDelete).isEmpty();
            verify(delegate, times(2)).findById(TENANT, COLLECTION, id);
        }
    }

    // =========================================================================
    // Pass-through operations (collection-scoped)
    // =========================================================================

    @Nested
    @DisplayName("Collection-scoped ops pass through without caching")
    class PassThroughTests {

        @Test
        @DisplayName("findAll always delegates")
        void findAllAlwaysDelegates() {
            when(delegate.findAll(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(List.of()));

            runPromise(() -> cache.findAll(TENANT, COLLECTION, Map.of(), null, 0, 10));
            runPromise(() -> cache.findAll(TENANT, COLLECTION, Map.of(), null, 0, 10));

            verify(delegate, times(2)).findAll(any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("exists always delegates")
        void existsAlwaysDelegates() {
            UUID id = UUID.randomUUID();
            when(delegate.exists(TENANT, COLLECTION, id)).thenReturn(Promise.of(true));

            runPromise(() -> cache.exists(TENANT, COLLECTION, id));
            runPromise(() -> cache.exists(TENANT, COLLECTION, id));

            verify(delegate, times(2)).exists(TENANT, COLLECTION, id);
        }

        @Test
        @DisplayName("count always delegates")
        void countAlwaysDelegates() {
            when(delegate.count(TENANT, COLLECTION)).thenReturn(Promise.of(42L));

            runPromise(() -> cache.count(TENANT, COLLECTION));
            runPromise(() -> cache.count(TENANT, COLLECTION));

            verify(delegate, times(2)).count(TENANT, COLLECTION);
        }

        @Test
        @DisplayName("findByQuery always delegates")
        void findByQueryAlwaysDelegates() {
            when(delegate.findByQuery(any(), any(), any())).thenReturn(Promise.of(List.of()));

            runPromise(() -> cache.findByQuery(TENANT, COLLECTION, "spec"));
            runPromise(() -> cache.findByQuery(TENANT, COLLECTION, "spec"));

            verify(delegate, times(2)).findByQuery(any(), any(), any());
        }
    }

    // =========================================================================
    // Cache management utilities
    // =========================================================================

    @Nested
    @DisplayName("Cache management")
    class CacheManagementTests {

        @Test
        @DisplayName("invalidateCollection evicts all entries for that tenant+collection")
        void invalidateCollectionEvictsCorrectEntries() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();

            when(delegate.findById(eq(TENANT), eq(COLLECTION), any()))
                    .thenReturn(Promise.of(Optional.of(stubEntity(id1))))
                    .thenReturn(Promise.of(Optional.of(stubEntity(id2))));
            when(delegate.findById(eq(TENANT), eq("other"), any()))
                    .thenReturn(Promise.of(Optional.of(stubEntity(id3))));

            // Warm 2 entries for COLLECTION, 1 for "other"
            runPromise(() -> cache.findById(TENANT, COLLECTION, id1));
            runPromise(() -> cache.findById(TENANT, COLLECTION, id2));
            runPromise(() -> cache.findById(TENANT, "other", id3));

            // Invalidate only COLLECTION
            cache.invalidateCollection(TENANT, COLLECTION);

            // Re-fetch id1 and id2 — should miss (delegate called again)
            when(delegate.findById(TENANT, COLLECTION, id1)).thenReturn(Promise.of(Optional.empty()));
            when(delegate.findById(TENANT, COLLECTION, id2)).thenReturn(Promise.of(Optional.empty()));
            runPromise(() -> cache.findById(TENANT, COLLECTION, id1));
            runPromise(() -> cache.findById(TENANT, COLLECTION, id2));

            // id3 in "other" collection should still be cached — delegate not called again
            runPromise(() -> cache.findById(TENANT, "other", id3));

            verify(delegate, times(2)).findById(TENANT, COLLECTION, id1);
            verify(delegate, times(2)).findById(TENANT, COLLECTION, id2);
            verify(delegate, times(1)).findById(TENANT, "other", id3);
        }

        @Test
        @DisplayName("invalidateAll clears every entry")
        void invalidateAllClearsEverything() {
            UUID id = UUID.randomUUID();
            Entity entity = stubEntity(id);
            when(delegate.findById(TENANT, COLLECTION, id))
                    .thenReturn(Promise.of(Optional.of(entity)));

            runPromise(() -> cache.findById(TENANT, COLLECTION, id));
            assertThat(cache.estimatedSize()).isGreaterThan(0);

            cache.invalidateAll();
            assertThat(cache.estimatedSize()).isZero();
        }

        @Test
        @DisplayName("estimatedSize reflects populated cache entries")
        void estimatedSizeReflectsCacheState() {
            UUID id = UUID.randomUUID();
            when(delegate.findById(any(), any(), any())).thenReturn(Promise.of(Optional.empty()));

            runPromise(() -> cache.findById(TENANT, COLLECTION, id));

            assertThat(cache.estimatedSize()).isGreaterThan(0);
        }
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    @Nested
    @DisplayName("Metrics counters")
    class MetricsTests {

        @Test
        @DisplayName("cache miss increments miss counter")
        void missIncrementsCounter() {
            UUID id = UUID.randomUUID();
            when(delegate.findById(any(), any(), any())).thenReturn(Promise.of(Optional.empty()));

            runPromise(() -> cache.findById(TENANT, COLLECTION, id));

            double misses = meterRegistry.counter("data_cloud.l1_cache.misses",
                    "operation", "findById").count();
            assertThat(misses).isEqualTo(1.0);
        }

        @Test
        @DisplayName("cache hit increments hit counter")
        void hitIncrementsCounter() {
            UUID id = UUID.randomUUID();
            when(delegate.findById(any(), any(), any())).thenReturn(Promise.of(Optional.empty()));

            // First call: miss
            runPromise(() -> cache.findById(TENANT, COLLECTION, id));
            // Second call: hit
            runPromise(() -> cache.findById(TENANT, COLLECTION, id));

            double hits = meterRegistry.counter("data_cloud.l1_cache.hits",
                    "operation", "findById").count();
            double misses = meterRegistry.counter("data_cloud.l1_cache.misses",
                    "operation", "findById").count();
            assertThat(hits).isEqualTo(1.0);
            assertThat(misses).isEqualTo(1.0);
        }

        @Test
        @DisplayName("no NullPointerException when meterRegistry is null")
        void noNpeWithNullMetrics() {
            var c = new CachingEntityRepository(delegate, null,
                    Duration.ofSeconds(5), 10L);
            when(delegate.findById(any(), any(), any())).thenReturn(Promise.of(Optional.empty()));

            Optional<Entity> result = runPromise(() -> c.findById(TENANT, COLLECTION, UUID.randomUUID()));
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Builds a minimal stub {@link Entity} with the given id. */
    private static Entity stubEntity(UUID id) {
        Entity entity = new Entity();
        entity.setId(id);
        entity.setTenantId(TENANT);
        entity.setCollectionName(COLLECTION);
        entity.setActive(true);
        return entity;
    }
}
