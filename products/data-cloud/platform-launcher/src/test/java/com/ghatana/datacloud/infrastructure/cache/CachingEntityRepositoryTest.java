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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CachingEntityRepository}.
 *
 * <p>Uses Mockito to stub the delegate {@link EntityRepository} so that
 * tests are fully isolated from any database. Extends {@link EventloopTestBase}
 * so that {@code runPromise()} correctly drives ActiveJ Promises. // GH-90000
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("CachingEntityRepository Tests")
class CachingEntityRepositoryTest extends EventloopTestBase {

    private static final String TENANT = "tenant-1";
    private static final String COLLECTION = "orders";

    @Mock
    private EntityRepository delegate;

    private SimpleMeterRegistry meterRegistry;
    private CachingEntityRepository cache;

    @BeforeEach
    void setUp() { // GH-90000
        meterRegistry = new SimpleMeterRegistry(); // GH-90000
        // Very short TTL (200 ms) so we can test expiry behaviour without sleeping long // GH-90000
        cache = new CachingEntityRepository(delegate, meterRegistry, // GH-90000
                Duration.ofMillis(200), 100L); // GH-90000
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("null delegate throws NullPointerException")
        void nullDelegateFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    new CachingEntityRepository(null, meterRegistry)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("zero TTL throws IllegalArgumentException")
        void zeroTtlFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    new CachingEntityRepository(delegate, meterRegistry, // GH-90000
                            Duration.ZERO, 10L))
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("negative TTL throws IllegalArgumentException")
        void negativeTtlFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    new CachingEntityRepository(delegate, meterRegistry, // GH-90000
                            Duration.ofSeconds(-1), 10L)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("maxSize zero throws IllegalArgumentException")
        void zeroMaxSizeFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    new CachingEntityRepository(delegate, meterRegistry, // GH-90000
                            Duration.ofSeconds(5), 0L)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("null MeterRegistry is accepted (no metrics)")
        void nullMeterRegistryIsAccepted() { // GH-90000
            // should not throw
            var c = new CachingEntityRepository(delegate, null); // GH-90000
            assertThat(c.estimatedSize()).isZero(); // GH-90000
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
        void firstCallMissesAndDelegates() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity entity = stubEntity(id); // GH-90000
            when(delegate.findById(TENANT, COLLECTION, id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

            Optional<Entity> result = runPromise(() -> // GH-90000
                    cache.findById(TENANT, COLLECTION, id)); // GH-90000

            assertThat(result).contains(entity); // GH-90000
            verify(delegate, times(1)).findById(TENANT, COLLECTION, id); // GH-90000
        }

        @Test
        @DisplayName("second call with same key is a hit → delegate not called again")
        void secondCallHitsCache() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity entity = stubEntity(id); // GH-90000
            when(delegate.findById(TENANT, COLLECTION, id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

            // First call — populates cache
            runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000
            // Second call — should hit cache
            Optional<Entity> secondResult = runPromise(() -> // GH-90000
                    cache.findById(TENANT, COLLECTION, id)); // GH-90000

            assertThat(secondResult).contains(entity); // GH-90000
            // delegate was called exactly once despite two cache.findById calls
            verify(delegate, times(1)).findById(TENANT, COLLECTION, id); // GH-90000
        }

        @Test
        @DisplayName("empty optional is also cached to prevent repeated DB misses")
        void emptyOptionalIsCached() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            when(delegate.findById(TENANT, COLLECTION, id)) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000
            Optional<Entity> second = runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000

            assertThat(second).isEmpty(); // GH-90000
            verify(delegate, times(1)).findById(TENANT, COLLECTION, id); // GH-90000
        }

        @Test
        @DisplayName("different entity IDs produce independent cache entries")
        void differentIdsAreIndependent() { // GH-90000
            UUID id1 = UUID.randomUUID(); // GH-90000
            UUID id2 = UUID.randomUUID(); // GH-90000
            Entity e1 = stubEntity(id1); // GH-90000
            Entity e2 = stubEntity(id2); // GH-90000
            when(delegate.findById(TENANT, COLLECTION, id1)).thenReturn(Promise.of(Optional.of(e1))); // GH-90000
            when(delegate.findById(TENANT, COLLECTION, id2)).thenReturn(Promise.of(Optional.of(e2))); // GH-90000

            Optional<Entity> r1 = runPromise(() -> cache.findById(TENANT, COLLECTION, id1)); // GH-90000
            Optional<Entity> r2 = runPromise(() -> cache.findById(TENANT, COLLECTION, id2)); // GH-90000
            Optional<Entity> r1Again = runPromise(() -> cache.findById(TENANT, COLLECTION, id1)); // GH-90000

            assertThat(r1).contains(e1); // GH-90000
            assertThat(r2).contains(e2); // GH-90000
            assertThat(r1Again).contains(e1); // GH-90000
            verify(delegate, times(1)).findById(TENANT, COLLECTION, id1); // GH-90000
            verify(delegate, times(1)).findById(TENANT, COLLECTION, id2); // GH-90000
        }

        @Test
        @DisplayName("different tenants produce independent cache entries (multi-tenancy)")
        void differentTenantsAreIndependent() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity e1 = stubEntity(id); // GH-90000
            Entity e2 = stubEntity(id); // GH-90000
            when(delegate.findById("tenant-A", COLLECTION, id)).thenReturn(Promise.of(Optional.of(e1))); // GH-90000
            when(delegate.findById("tenant-B", COLLECTION, id)).thenReturn(Promise.of(Optional.of(e2))); // GH-90000

            Optional<Entity> rA = runPromise(() -> cache.findById("tenant-A", COLLECTION, id)); // GH-90000
            Optional<Entity> rB = runPromise(() -> cache.findById("tenant-B", COLLECTION, id)); // GH-90000

            assertThat(rA).contains(e1); // GH-90000
            assertThat(rB).contains(e2); // GH-90000
            // Both tenants miss independently
            verify(delegate, times(1)).findById("tenant-A", COLLECTION, id); // GH-90000
            verify(delegate, times(1)).findById("tenant-B", COLLECTION, id); // GH-90000
        }

        @Test
        @DisplayName("TTL expiry causes re-fetch from delegate")
        void ttlExpiryRecallsDelegate() throws InterruptedException { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity entity = stubEntity(id); // GH-90000
            when(delegate.findById(TENANT, COLLECTION, id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

            // Populate cache
            runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000
            // Wait for TTL (200 ms) to expire // GH-90000
            Thread.sleep(350); // GH-90000
            // After expiry, delegate must be called again
            runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000

            verify(delegate, times(2)).findById(TENANT, COLLECTION, id); // GH-90000
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
        void saveDelegates() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity entity = stubEntity(id); // GH-90000
            when(delegate.save(TENANT, entity)).thenReturn(Promise.of(entity)); // GH-90000

            Entity saved = runPromise(() -> cache.save(TENANT, entity)); // GH-90000

            assertThat(saved).isSameAs(entity); // GH-90000
            verify(delegate).save(TENANT, entity); // GH-90000
        }

        @Test
        @DisplayName("save invalidates the cached findById entry for the entity")
        void saveInvalidatesCacheEntry() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity v1 = stubEntity(id); // GH-90000
            Entity v2 = stubEntity(id); // GH-90000

            when(delegate.findById(TENANT, COLLECTION, id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(v1))) // GH-90000
                    .thenReturn(Promise.of(Optional.of(v2))); // GH-90000
            when(delegate.save(TENANT, v2)).thenReturn(Promise.of(v2)); // GH-90000

            // Populate cache with v1
            runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000
            // Save v2 — should invalidate the v1 entry
            runPromise(() -> cache.save(TENANT, v2)); // GH-90000
            // Next read should miss and get v2 from delegate
            Optional<Entity> afterSave = runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000

            assertThat(afterSave).contains(v2); // GH-90000
            // delegate.findById was called twice (before and after save) // GH-90000
            verify(delegate, times(2)).findById(TENANT, COLLECTION, id); // GH-90000
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
        void deleteDelegates() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            when(delegate.delete(TENANT, COLLECTION, id)).thenReturn(Promise.of(null)); // GH-90000

            runPromise(() -> cache.delete(TENANT, COLLECTION, id)); // GH-90000

            verify(delegate).delete(TENANT, COLLECTION, id); // GH-90000
        }

        @Test
        @DisplayName("delete invalidates the cached findById entry")
        void deleteInvalidatesCacheEntry() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity entity = stubEntity(id); // GH-90000

            when(delegate.findById(TENANT, COLLECTION, id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entity))) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000
            when(delegate.delete(TENANT, COLLECTION, id)).thenReturn(Promise.of(null)); // GH-90000

            // Warm the cache
            runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000
            // Delete
            runPromise(() -> cache.delete(TENANT, COLLECTION, id)); // GH-90000
            // Should miss cache and get empty from delegate
            Optional<Entity> afterDelete = runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000

            assertThat(afterDelete).isEmpty(); // GH-90000
            verify(delegate, times(2)).findById(TENANT, COLLECTION, id); // GH-90000
        }
    }

    // =========================================================================
    // Pass-through operations (collection-scoped) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("Collection-scoped ops pass through without caching")
    class PassThroughTests {

        @Test
        @DisplayName("findAll always delegates")
        void findAllAlwaysDelegates() { // GH-90000
            when(delegate.findAll(any(), any(), any(), any(), anyInt(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            runPromise(() -> cache.findAll(TENANT, COLLECTION, Map.of(), null, 0, 10)); // GH-90000
            runPromise(() -> cache.findAll(TENANT, COLLECTION, Map.of(), null, 0, 10)); // GH-90000

            verify(delegate, times(2)).findAll(any(), any(), any(), any(), anyInt(), anyInt()); // GH-90000
        }

        @Test
        @DisplayName("exists always delegates")
        void existsAlwaysDelegates() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            when(delegate.exists(TENANT, COLLECTION, id)).thenReturn(Promise.of(true)); // GH-90000

            runPromise(() -> cache.exists(TENANT, COLLECTION, id)); // GH-90000
            runPromise(() -> cache.exists(TENANT, COLLECTION, id)); // GH-90000

            verify(delegate, times(2)).exists(TENANT, COLLECTION, id); // GH-90000
        }

        @Test
        @DisplayName("count always delegates")
        void countAlwaysDelegates() { // GH-90000
            when(delegate.count(TENANT, COLLECTION)).thenReturn(Promise.of(42L)); // GH-90000

            runPromise(() -> cache.count(TENANT, COLLECTION)); // GH-90000
            runPromise(() -> cache.count(TENANT, COLLECTION)); // GH-90000

            verify(delegate, times(2)).count(TENANT, COLLECTION); // GH-90000
        }

        @Test
        @DisplayName("findByQuery always delegates")
        void findByQueryAlwaysDelegates() { // GH-90000
            when(delegate.findByQuery(any(), any(), any())).thenReturn(Promise.of(List.of())); // GH-90000

            runPromise(() -> cache.findByQuery(TENANT, COLLECTION, "spec")); // GH-90000
            runPromise(() -> cache.findByQuery(TENANT, COLLECTION, "spec")); // GH-90000

            verify(delegate, times(2)).findByQuery(any(), any(), any()); // GH-90000
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
        void invalidateCollectionEvictsCorrectEntries() { // GH-90000
            UUID id1 = UUID.randomUUID(); // GH-90000
            UUID id2 = UUID.randomUUID(); // GH-90000
            UUID id3 = UUID.randomUUID(); // GH-90000

            when(delegate.findById(TENANT, COLLECTION, id1)) // GH-90000
                .thenReturn(Promise.of(Optional.of(stubEntity(id1)))) // GH-90000
                .thenReturn(Promise.of(Optional.empty())); // GH-90000
            when(delegate.findById(TENANT, COLLECTION, id2)) // GH-90000
                .thenReturn(Promise.of(Optional.of(stubEntity(id2)))) // GH-90000
                .thenReturn(Promise.of(Optional.empty())); // GH-90000
            when(delegate.findById(TENANT, "other", id3)) // GH-90000
                .thenReturn(Promise.of(Optional.of(stubEntity(id3)))); // GH-90000

            // Warm 2 entries for COLLECTION, 1 for "other"
            runPromise(() -> cache.findById(TENANT, COLLECTION, id1)); // GH-90000
            runPromise(() -> cache.findById(TENANT, COLLECTION, id2)); // GH-90000
            runPromise(() -> cache.findById(TENANT, "other", id3)); // GH-90000

            clearInvocations(delegate); // GH-90000

            // Invalidate only COLLECTION
            cache.invalidateCollection(TENANT, COLLECTION); // GH-90000

            // Re-fetch id1 and id2 — should miss after invalidation.
            runPromise(() -> cache.findById(TENANT, COLLECTION, id1)); // GH-90000
            runPromise(() -> cache.findById(TENANT, COLLECTION, id2)); // GH-90000

            // id3 in "other" collection should still be cached — delegate not called again
            runPromise(() -> cache.findById(TENANT, "other", id3)); // GH-90000

            verify(delegate, times(1)).findById(TENANT, COLLECTION, id1); // GH-90000
            verify(delegate, times(1)).findById(TENANT, COLLECTION, id2); // GH-90000
            verify(delegate, never()).findById(TENANT, "other", id3); // GH-90000
        }

        @Test
        @DisplayName("invalidateAll clears every entry")
        void invalidateAllClearsEverything() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity entity = stubEntity(id); // GH-90000
            when(delegate.findById(TENANT, COLLECTION, id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

            runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000
            assertThat(cache.estimatedSize()).isGreaterThan(0); // GH-90000

            cache.invalidateAll(); // GH-90000
            assertThat(cache.estimatedSize()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("estimatedSize reflects populated cache entries")
        void estimatedSizeReflectsCacheState() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            when(delegate.findById(any(), any(), any())).thenReturn(Promise.of(Optional.empty())); // GH-90000

            runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000

            assertThat(cache.estimatedSize()).isGreaterThan(0); // GH-90000
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
        void missIncrementsCounter() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            when(delegate.findById(any(), any(), any())).thenReturn(Promise.of(Optional.empty())); // GH-90000

            runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000

            double misses = meterRegistry.counter("data_cloud.l1_cache.misses", // GH-90000
                    "operation", "findById").count(); // GH-90000
            assertThat(misses).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("cache hit increments hit counter")
        void hitIncrementsCounter() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            when(delegate.findById(any(), any(), any())).thenReturn(Promise.of(Optional.empty())); // GH-90000

            // First call: miss
            runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000
            // Second call: hit
            runPromise(() -> cache.findById(TENANT, COLLECTION, id)); // GH-90000

            double hits = meterRegistry.counter("data_cloud.l1_cache.hits", // GH-90000
                    "operation", "findById").count(); // GH-90000
            double misses = meterRegistry.counter("data_cloud.l1_cache.misses", // GH-90000
                    "operation", "findById").count(); // GH-90000
            assertThat(hits).isEqualTo(1.0); // GH-90000
            assertThat(misses).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("no NullPointerException when meterRegistry is null")
        void noNpeWithNullMetrics() { // GH-90000
            var c = new CachingEntityRepository(delegate, null, // GH-90000
                    Duration.ofSeconds(5), 10L); // GH-90000
            when(delegate.findById(any(), any(), any())).thenReturn(Promise.of(Optional.empty())); // GH-90000

            Optional<Entity> result = runPromise(() -> c.findById(TENANT, COLLECTION, UUID.randomUUID())); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Builds a minimal stub {@link Entity} with the given id. */
    private static Entity stubEntity(UUID id) { // GH-90000
        Entity entity = new Entity(); // GH-90000
        entity.setId(id); // GH-90000
        entity.setTenantId(TENANT); // GH-90000
        entity.setCollectionName(COLLECTION); // GH-90000
        entity.setActive(true); // GH-90000
        return entity;
    }
}
