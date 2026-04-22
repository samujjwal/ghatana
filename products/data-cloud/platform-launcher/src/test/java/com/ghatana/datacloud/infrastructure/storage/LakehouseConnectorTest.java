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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class) // GH-90000
class LakehouseConnectorTest extends EventloopTestBase {

    @Mock
    MetricsCollector metrics;

    LakehouseConnector connector;

    static final String TENANT = "tenant-test";
    static final String COLLECTION = "events";
    static final UUID COLLECTION_ID = UUID.randomUUID(); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        lenient().doNothing().when(metrics).incrementCounter(anyString(), any(String[].class)); // GH-90000
        lenient().doNothing().when(metrics).recordTimer(anyString(), anyLong(), any(String[].class)); // GH-90000
        connector = new LakehouseConnector(metrics); // GH-90000
    }

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    void create_assignsId_whenAbsent() { // GH-90000
        Entity e = entityWithoutId(); // GH-90000
        Entity saved = resolve(connector.create(e)); // GH-90000
        assertThat(saved.getId()).isNotNull(); // GH-90000
        assertThat(saved.getTenantId()).isEqualTo(TENANT); // GH-90000
    }

    @Test
    void create_preservesId_whenPresent() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        Entity e = entityWithId(id); // GH-90000
        Entity saved = resolve(connector.create(e)); // GH-90000
        assertThat(saved.getId()).isEqualTo(id); // GH-90000
    }

    @Test
    void create_null_throwsNPE() { // GH-90000
        assertThatNullPointerException().isThrownBy(() -> connector.create(null)); // GH-90000
    }

    // ── read ─────────────────────────────────────────────────────────────────

    @Test
    void read_returnsEntity_afterCreate() { // GH-90000
        Entity saved = resolve(connector.create(entityWithoutId())); // GH-90000
        Optional<Entity> found = resolve(connector.read(COLLECTION_ID, TENANT, saved.getId())); // GH-90000
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().getId()).isEqualTo(saved.getId()); // GH-90000
    }

    @Test
    void read_returnsEmpty_forUnknownId() { // GH-90000
        resolve(connector.create(entityWithoutId())); // GH-90000
        Optional<Entity> found = resolve(connector.read(COLLECTION_ID, TENANT, UUID.randomUUID())); // GH-90000
        assertThat(found).isEmpty(); // GH-90000
    }

    @Test
    void read_isolatesTenants() { // GH-90000
        Entity saved = resolve(connector.create(entityWithoutId())); // GH-90000
        Optional<Entity> found = resolve(connector.read(COLLECTION_ID, "other-tenant", saved.getId())); // GH-90000
        assertThat(found).isEmpty(); // GH-90000
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_replacesEntity() { // GH-90000
        Entity original = resolve(connector.create(entityWithoutId())); // GH-90000
        Entity updated = Entity.builder() // GH-90000
                .id(original.getId()) // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .data(Map.of("key", "updated-value")) // GH-90000
                .build(); // GH-90000
        Entity result = resolve(connector.update(updated)); // GH-90000
        assertThat(result.getData().get("key [GH-90000]")).isEqualTo("updated-value [GH-90000]");
    }

    @Test
    void update_fails_forMissingEntity() { // GH-90000
        Entity ghost = entityWithId(UUID.randomUUID()); // GH-90000
        Promise<Entity> p = connector.update(ghost); // GH-90000
        assertThatThrownBy(() -> resolve(p)) // GH-90000
                .isInstanceOf(Exception.class); // GH-90000
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_removesEntity() { // GH-90000
        Entity saved = resolve(connector.create(entityWithoutId())); // GH-90000
        resolve(connector.delete(COLLECTION_ID, TENANT, saved.getId())); // GH-90000
        Optional<Entity> found = resolve(connector.read(COLLECTION_ID, TENANT, saved.getId())); // GH-90000
        assertThat(found).isEmpty(); // GH-90000
    }

    @Test
    void delete_isIdempotent_forMissingEntity() { // GH-90000
        assertThatCode(() -> resolve(connector.delete(COLLECTION_ID, TENANT, UUID.randomUUID()))) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ── query ────────────────────────────────────────────────────────────────

    @Test
    void query_returnsPaginatedResults() { // GH-90000
        for (int i = 0; i < 5; i++) resolve(connector.create(entityWithoutId())); // GH-90000
        QuerySpec spec = QuerySpec.builder().limit(3).offset(0).build(); // GH-90000
        StorageConnector.QueryResult result = resolve(connector.query(COLLECTION_ID, TENANT, spec)); // GH-90000
        assertThat(result.entities()).hasSize(3); // GH-90000
        assertThat(result.total()).isEqualTo(5); // GH-90000
    }

    @Test
    void query_respectsOffset() { // GH-90000
        for (int i = 0; i < 4; i++) resolve(connector.create(entityWithoutId())); // GH-90000
        QuerySpec spec = QuerySpec.builder().limit(10).offset(2).build(); // GH-90000
        StorageConnector.QueryResult result = resolve(connector.query(COLLECTION_ID, TENANT, spec)); // GH-90000
        assertThat(result.entities()).hasSize(2); // GH-90000
    }

    @Test
    void query_appliesFilterExpression() { // GH-90000
        resolve(connector.create(entity(Map.of("status", "active", "amount", 150)))); // GH-90000
        resolve(connector.create(entity(Map.of("status", "inactive", "amount", 150)))); // GH-90000
        resolve(connector.create(entity(Map.of("status", "active", "amount", 80)))); // GH-90000

        QuerySpec spec = QuerySpec.builder() // GH-90000
            .filter("status = 'active' AND amount >= 100 [GH-90000]")
            .build(); // GH-90000

        StorageConnector.QueryResult result = resolve(connector.query(COLLECTION_ID, TENANT, spec)); // GH-90000

        assertThat(result.entities()).hasSize(1); // GH-90000
        assertThat(result.entities().getFirst().getData()).containsEntry("status", "active"); // GH-90000
    }

    @Test
    void query_appliesSortFieldsBeforePagination() { // GH-90000
        resolve(connector.create(entity(Map.of("score", 5, "name", "gamma")))); // GH-90000
        resolve(connector.create(entity(Map.of("score", 20, "name", "alpha")))); // GH-90000
        resolve(connector.create(entity(Map.of("score", 10, "name", "beta")))); // GH-90000

        QuerySpec spec = QuerySpec.builder() // GH-90000
            .sort("score", QuerySpec.SortDirection.DESC) // GH-90000
            .limit(2) // GH-90000
            .build(); // GH-90000

        StorageConnector.QueryResult result = resolve(connector.query(COLLECTION_ID, TENANT, spec)); // GH-90000

        assertThat(result.entities()) // GH-90000
            .extracting(entity -> entity.getData().get("score [GH-90000]"))
            .containsExactly(20, 10); // GH-90000
    }

    // ── count ────────────────────────────────────────────────────────────────

    @Test
    void count_reflectsCreatedEntities() { // GH-90000
        resolve(connector.create(entityWithoutId())); // GH-90000
        resolve(connector.create(entityWithoutId())); // GH-90000
        long count = resolve(connector.count(COLLECTION_ID, TENANT, null)); // GH-90000
        assertThat(count).isEqualTo(2); // GH-90000
    }

    @Test
    void count_appliesFilterExpression() { // GH-90000
        resolve(connector.create(entity(Map.of("status", "active")))); // GH-90000
        resolve(connector.create(entity(Map.of("status", "inactive")))); // GH-90000

        long count = resolve(connector.count(COLLECTION_ID, TENANT, "status = 'active'")); // GH-90000

        assertThat(count).isEqualTo(1); // GH-90000
    }

    // ── bulkCreate ───────────────────────────────────────────────────────────

    @Test
    void bulkCreate_storesAllEntities() { // GH-90000
        List<Entity> batch = List.of(entityWithoutId(), entityWithoutId(), entityWithoutId()); // GH-90000
        List<Entity> created = resolve(connector.bulkCreate(COLLECTION_ID, TENANT, batch)); // GH-90000
        assertThat(created).hasSize(3); // GH-90000
        long count = resolve(connector.count(COLLECTION_ID, TENANT, null)); // GH-90000
        assertThat(count).isEqualTo(3); // GH-90000
    }

    // ── bulkDelete ───────────────────────────────────────────────────────────

    @Test
    void bulkDelete_removesSpecifiedEntities() { // GH-90000
        Entity a = resolve(connector.create(entityWithoutId())); // GH-90000
        Entity b = resolve(connector.create(entityWithoutId())); // GH-90000
        resolve(connector.create(entityWithoutId())); // GH-90000
        long deleted = resolve(connector.bulkDelete(COLLECTION_ID, TENANT, List.of(a.getId(), b.getId()))); // GH-90000
        assertThat(deleted).isEqualTo(2); // GH-90000
        assertThat(resolve(connector.count(COLLECTION_ID, TENANT, null))).isEqualTo(1); // GH-90000
    }

    // ── truncate ─────────────────────────────────────────────────────────────

    @Test
    void truncate_removesAllEntities() { // GH-90000
        resolve(connector.create(entityWithoutId())); // GH-90000
        resolve(connector.create(entityWithoutId())); // GH-90000
        long deleted = resolve(connector.truncate(COLLECTION_ID, TENANT)); // GH-90000
        assertThat(deleted).isEqualTo(2); // GH-90000
        assertThat(resolve(connector.count(COLLECTION_ID, TENANT, null))).isZero(); // GH-90000
    }

    // ── concurrency ──────────────────────────────────────────────────────────

    @Test
    void concurrentCreates_doNotCorruptState() throws InterruptedException { // GH-90000
        int threads = 10;
        int perThread = 100;
        ExecutorService exec = Executors.newFixedThreadPool(threads); // GH-90000
        CountDownLatch latch = new CountDownLatch(threads); // GH-90000
        AtomicInteger errors = new AtomicInteger(0); // GH-90000

        for (int t = 0; t < threads; t++) { // GH-90000
            exec.submit(() -> { // GH-90000
                try {
                    for (int i = 0; i < perThread; i++) { // GH-90000
                        resolve(connector.create(entityWithoutId())); // GH-90000
                    }
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
        long count = resolve(connector.count(COLLECTION_ID, TENANT, null)); // GH-90000
        assertThat(count).isEqualTo((long) threads * perThread); // GH-90000
    }

    // ── metadata ─────────────────────────────────────────────────────────────

    @Test
    void getMetadata_returnsLakehouseType() { // GH-90000
        assertThat(connector.getMetadata().backendType().name()).isEqualTo("LAKEHOUSE [GH-90000]");
    }

    @Test
    void healthCheck_succeeds() { // GH-90000
        assertThatCode(() -> resolve(connector.healthCheck())).doesNotThrowAnyException(); // GH-90000
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Entity entityWithoutId() { // GH-90000
        return Entity.builder() // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .data(Map.of("field", UUID.randomUUID().toString())) // GH-90000
                .build(); // GH-90000
    }

    private Entity entity(Map<String, Object> data) { // GH-90000
        return Entity.builder() // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .data(data) // GH-90000
                .build(); // GH-90000
    }

    private Entity entityWithId(UUID id) { // GH-90000
        return Entity.builder() // GH-90000
                .id(id) // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .data(Map.of("field", "value")) // GH-90000
                .build(); // GH-90000
    }

    private <T> T resolve(Promise<T> promise) { // GH-90000
        return runPromise(() -> promise); // GH-90000
    }
}
