package com.ghatana.datacloud.infrastructure.storage;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class) // GH-90000
class TimeSeriesConnectorTest extends EventloopTestBase {

    @Mock
    MetricsCollector metrics;

    TimeSeriesConnector connector;

    static final String TENANT = "ts-tenant";
    static final String COLLECTION = "metrics";
    static final UUID COLLECTION_ID = UUID.randomUUID(); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        lenient().doNothing().when(metrics).incrementCounter(anyString(), any(String[].class)); // GH-90000
        lenient().doNothing().when(metrics).recordTimer(anyString(), anyLong(), any(String[].class)); // GH-90000
        connector = new TimeSeriesConnector(metrics); // GH-90000
    }

    // ── constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_null_throwsNPE() { // GH-90000
        assertThatNullPointerException().isThrownBy(() -> new TimeSeriesConnector(null)); // GH-90000
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_generatesId_whenAbsent() { // GH-90000
        Entity saved = resolve(connector.create(event(null))); // GH-90000
        assertThat(saved.getId()).isNotNull(); // GH-90000
    }

    @Test
    void create_preservesExplicitId() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        Entity saved = resolve(connector.create(event(id))); // GH-90000
        assertThat(saved.getId()).isEqualTo(id); // GH-90000
    }

    @Test
    void create_multipleEntities_allStored() { // GH-90000
        for (int i = 0; i < 5; i++) resolve(connector.create(event(null))); // GH-90000
        long count = resolve(connector.count(COLLECTION_ID, TENANT, null)); // GH-90000
        assertThat(count).isEqualTo(5); // GH-90000
    }

    // ── read ─────────────────────────────────────────────────────────────────

    @Test
    void read_findsCreatedEntity() { // GH-90000
        Entity saved = resolve(connector.create(event(null))); // GH-90000
        Optional<Entity> found = resolve(connector.read(COLLECTION_ID, TENANT, saved.getId())); // GH-90000
        assertThat(found).isPresent(); // GH-90000
    }

    @Test
    void read_returnsEmpty_forUnknownId() { // GH-90000
        Optional<Entity> found = resolve(connector.read(COLLECTION_ID, TENANT, UUID.randomUUID())); // GH-90000
        assertThat(found).isEmpty(); // GH-90000
    }

    @Test
    void read_isolatesByTenant() { // GH-90000
        Entity saved = resolve(connector.create(event(null))); // GH-90000
        Optional<Entity> crossTenant = resolve(connector.read(COLLECTION_ID, "other-tenant", saved.getId())); // GH-90000
        assertThat(crossTenant).isEmpty(); // GH-90000
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_replacesData() { // GH-90000
        Entity saved = resolve(connector.create(event(null))); // GH-90000
        Entity updated = Entity.builder() // GH-90000
                .id(saved.getId()) // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .data(Map.of("value", 99.0, "timestamp", Instant.now().toString())) // GH-90000
                .build(); // GH-90000
        Entity result = resolve(connector.update(updated)); // GH-90000
        assertThat(result.getData().get("value [GH-90000]")).isEqualTo(99.0);
    }

    @Test
    void update_failsForMissingEntity() { // GH-90000
        Entity ghost = event(UUID.randomUUID()); // GH-90000
        assertThatThrownBy(() -> resolve(connector.update(ghost))).isInstanceOf(Exception.class); // GH-90000
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_removesEntity() { // GH-90000
        Entity saved = resolve(connector.create(event(null))); // GH-90000
        resolve(connector.delete(COLLECTION_ID, TENANT, saved.getId())); // GH-90000
        Optional<Entity> found = resolve(connector.read(COLLECTION_ID, TENANT, saved.getId())); // GH-90000
        assertThat(found).isEmpty(); // GH-90000
    }

    // ── query with time window ────────────────────────────────────────────────

    @Test
    void query_returnsAllEntities_withoutFilter() { // GH-90000
        for (int i = 0; i < 3; i++) resolve(connector.create(event(null))); // GH-90000
        QuerySpec spec = QuerySpec.builder().limit(10).offset(0).build(); // GH-90000
        StorageConnector.QueryResult result = resolve(connector.query(COLLECTION_ID, TENANT, spec)); // GH-90000
        assertThat(result.entities()).hasSize(3); // GH-90000
    }

    @Test
    void query_paginatesCorrectly() { // GH-90000
        for (int i = 0; i < 6; i++) resolve(connector.create(event(null))); // GH-90000
        QuerySpec spec = QuerySpec.builder().limit(2).offset(4).build(); // GH-90000
        StorageConnector.QueryResult result = resolve(connector.query(COLLECTION_ID, TENANT, spec)); // GH-90000
        assertThat(result.entities()).hasSize(2); // GH-90000
    }

    @Test
    void query_timeWindow_filtersEntities() { // GH-90000
        resolve(connector.create(event(null))); // GH-90000
        Instant futureStart = Instant.now().plusSeconds(3600); // GH-90000
        Instant futureEnd = Instant.now().plusSeconds(7200); // GH-90000
        QuerySpec spec = QuerySpec.builder() // GH-90000
                .timeWindow(futureStart, futureEnd) // GH-90000
                .limit(100).offset(0).build(); // GH-90000
        StorageConnector.QueryResult result = resolve(connector.query(COLLECTION_ID, TENANT, spec)); // GH-90000
        assertThat(result.entities()).isEmpty(); // GH-90000
    }

    // ── scan ─────────────────────────────────────────────────────────────────

    @Test
    void scan_returnsAllEntities_noFilter() { // GH-90000
        for (int i = 0; i < 4; i++) resolve(connector.create(event(null))); // GH-90000
        List<Entity> found = resolve(connector.scan(COLLECTION_ID, TENANT, null, 100, 0)); // GH-90000
        assertThat(found).hasSize(4); // GH-90000
    }

    // ── concurrency ──────────────────────────────────────────────────────────

    @Test
    void concurrentCreates_areThreadSafe() throws InterruptedException { // GH-90000
        int threads = 8;
        int perThread = 50;
        ExecutorService exec = Executors.newFixedThreadPool(threads); // GH-90000
        CountDownLatch latch = new CountDownLatch(threads); // GH-90000
        AtomicInteger errors = new AtomicInteger(0); // GH-90000

        for (int t = 0; t < threads; t++) { // GH-90000
            exec.submit(() -> { // GH-90000
                try {
                    for (int i = 0; i < perThread; i++) resolve(connector.create(event(null))); // GH-90000
                } catch (Exception e) { // GH-90000
                    errors.incrementAndGet(); // GH-90000
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS); // GH-90000
        exec.shutdown(); // GH-90000

        assertThat(errors.get()).isZero(); // GH-90000
        assertThat(resolve(connector.count(COLLECTION_ID, TENANT, null))) // GH-90000
                .isEqualTo((long) threads * perThread); // GH-90000
    }

    // ── metadata ─────────────────────────────────────────────────────────────

    @Test
    void metadata_isTimeSeries() { // GH-90000
        assertThat(connector.getMetadata().supportsTimeSeries()).isTrue(); // GH-90000
    }

    @Test
    void healthCheck_completes() { // GH-90000
        assertThatCode(() -> resolve(connector.healthCheck())).doesNotThrowAnyException(); // GH-90000
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Entity event(UUID id) { // GH-90000
        if (id != null) { // GH-90000
            return Entity.builder() // GH-90000
                    .id(id) // GH-90000
                    .tenantId(TENANT) // GH-90000
                    .collectionName(COLLECTION) // GH-90000
                    .data(Map.of( // GH-90000
                            "timestamp", Instant.now().toString(), // GH-90000
                            "metric", "cpu_usage",
                            "value", Math.random() * 100)) // GH-90000
                    .build(); // GH-90000
        }
        return Entity.builder() // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .data(Map.of( // GH-90000
                        "timestamp", Instant.now().toString(), // GH-90000
                        "metric", "cpu_usage",
                        "value", Math.random() * 100)) // GH-90000
                .build(); // GH-90000
    }

    private <T> T resolve(Promise<T> promise) { // GH-90000
        return runPromise(() -> promise); // GH-90000
    }
}
