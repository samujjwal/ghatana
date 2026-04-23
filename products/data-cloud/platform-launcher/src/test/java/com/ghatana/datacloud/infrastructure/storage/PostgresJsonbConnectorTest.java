package com.ghatana.datacloud.infrastructure.storage;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.datacloud.infrastructure.audit.DataCloudAuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // GH-90000
@MockitoSettings(strictness = Strictness.LENIENT) // GH-90000
class PostgresJsonbConnectorTest extends EventloopTestBase {

    @Mock
    EntityRepository entityRepository;

    @Mock
    MetricsCollector metrics;

    @Mock
    DataCloudAuditLogger auditLogger;

    PostgresJsonbConnector connector;

    static final String TENANT = "tenant-pg";
    static final String COLLECTION = "products";
    static final UUID COLLECTION_ID = UUID.randomUUID(); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        doNothing().when(metrics).incrementCounter(anyString(), any(String[].class)); // GH-90000
        doNothing().when(metrics).recordTimer(anyString(), anyLong(), any(String[].class)); // GH-90000
        doNothing().when(auditLogger).logDataModification(any(), any(), any(), any(), anyBoolean()); // GH-90000
        when(entityRepository.count(anyString(), anyString())).thenReturn(Promise.of(0L)); // GH-90000
        connector = new PostgresJsonbConnector(entityRepository, metrics, auditLogger); // GH-90000
    }

    // ── constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_null_repository_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new PostgresJsonbConnector(null, metrics, auditLogger)); // GH-90000
    }

    @Test
    void constructor_null_metrics_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new PostgresJsonbConnector(entityRepository, null, auditLogger)); // GH-90000
    }

    @Test
    void constructor_null_auditLogger_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new PostgresJsonbConnector(entityRepository, metrics, null)); // GH-90000
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_savesEntityAndReturnsResult() { // GH-90000
        Entity input = entity(null); // GH-90000
        Entity saved = entity(UUID.randomUUID()); // GH-90000
        when(entityRepository.save(eq(TENANT), any(Entity.class))) // GH-90000
                .thenReturn(Promise.of(saved)); // GH-90000

        Entity result = resolve(connector.create(input)); // GH-90000

        assertThat(result).isEqualTo(saved); // GH-90000
        verify(entityRepository).save(eq(TENANT), any(Entity.class)); // GH-90000
    }

    @Test
    void create_generatesId_whenNull() { // GH-90000
        Entity input = entity(null); // GH-90000
        when(entityRepository.save(eq(TENANT), any(Entity.class))) // GH-90000
                .thenAnswer(inv -> Promise.of(inv.getArgument(1))); // GH-90000

        Entity result = resolve(connector.create(input)); // GH-90000

        assertThat(result.getId()).isNotNull(); // GH-90000
    }

    @Test
    void create_null_throwsNPE() { // GH-90000
        assertThatNullPointerException().isThrownBy(() -> connector.create(null)); // GH-90000
    }

    @Test
    void create_logsAuditOnSuccess() { // GH-90000
        Entity input = entity(null); // GH-90000
        Entity saved = entity(UUID.randomUUID()); // GH-90000
        when(entityRepository.save(eq(TENANT), any(Entity.class))) // GH-90000
                .thenReturn(Promise.of(saved)); // GH-90000

        resolve(connector.create(input)); // GH-90000

        verify(auditLogger).logDataModification(eq(TENANT), eq("CREATE"), eq(COLLECTION), any(), eq(true));
    }

    // ── read ─────────────────────────────────────────────────────────────────

    @Test
    void read_returnsEntity_whenFound() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        Entity found = entity(id); // GH-90000
        // M7: connector passes collectionId.toString() — use anyString() not eq(COLLECTION) // GH-90000
        when(entityRepository.findById(eq(TENANT), anyString(), eq(id))) // GH-90000
                .thenReturn(Promise.of(Optional.of(found))); // GH-90000

        Optional<Entity> result = resolve(connector.read(COLLECTION_ID, TENANT, id)); // GH-90000

        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getId()).isEqualTo(id); // GH-90000
    }

    @Test
    void read_returnsEmpty_whenNotFound() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        // M7: connector passes collectionId.toString() — use anyString() not eq(COLLECTION) // GH-90000
        when(entityRepository.findById(eq(TENANT), anyString(), eq(id))) // GH-90000
                .thenReturn(Promise.of(Optional.empty())); // GH-90000

        Optional<Entity> result = resolve(connector.read(COLLECTION_ID, TENANT, id)); // GH-90000

        assertThat(result).isEmpty(); // GH-90000
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_savesAndReturnsUpdatedEntity() { // GH-90000
        Entity input = entity(UUID.randomUUID()); // GH-90000
        // M7: update() calls only save(), not findById() // GH-90000
        when(entityRepository.save(eq(TENANT), any(Entity.class))) // GH-90000
                .thenReturn(Promise.of(input)); // GH-90000

        Entity result = resolve(connector.update(input)); // GH-90000

        assertThat(result.getId()).isEqualTo(input.getId()); // GH-90000
        verify(entityRepository).save(eq(TENANT), any(Entity.class)); // GH-90000
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_invokesRepository() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        // M7: connector passes collectionId.toString() — use anyString() not eq(COLLECTION) // GH-90000
        when(entityRepository.delete(eq(TENANT), anyString(), eq(id))) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000

        assertThatCode(() -> resolve(connector.delete(COLLECTION_ID, TENANT, id))) // GH-90000
                .doesNotThrowAnyException(); // GH-90000

        verify(entityRepository).delete(eq(TENANT), anyString(), eq(id)); // GH-90000
    }

    // ── query ────────────────────────────────────────────────────────────────

    @Test
    void query_delegatesToRepository() { // GH-90000
        List<Entity> entities = List.of(entity(UUID.randomUUID()), entity(UUID.randomUUID())); // GH-90000
        // M7: connector calls findByQuery (not findAll) and count (not countByFilter) // GH-90000
        when(entityRepository.findByQuery(eq(TENANT), anyString(), any())) // GH-90000
                .thenReturn(Promise.of(entities)); // GH-90000
        when(entityRepository.count(eq(TENANT), anyString())) // GH-90000
                .thenReturn(Promise.of(2L)); // GH-90000

        QuerySpec spec = QuerySpec.builder().limit(10).offset(0).build(); // GH-90000
        StorageConnector.QueryResult result = resolve(connector.query(COLLECTION_ID, TENANT, spec)); // GH-90000

        assertThat(result.entities()).hasSize(2); // GH-90000
        assertThat(result.total()).isEqualTo(2); // GH-90000
    }

    // ── count ────────────────────────────────────────────────────────────────

    @Test
    void count_delegatesToRepository() { // GH-90000
        // M7: connector calls count() not countByFilter(); collectionName = collectionId.toString() // GH-90000
        when(entityRepository.count(eq(TENANT), anyString())) // GH-90000
                .thenReturn(Promise.of(5L)); // GH-90000

        long count = resolve(connector.count(COLLECTION_ID, TENANT, null)); // GH-90000

        assertThat(count).isEqualTo(5L); // GH-90000
    }

    // ── metadata ─────────────────────────────────────────────────────────────

    @Test
    void getMetadata_returnsRelationalBackendType() { // GH-90000
        assertThat(connector.getMetadata().backendType().name()).isEqualTo("RELATIONAL");
    }

    @Test
    void healthCheck_completesWithoutError() { // GH-90000
        assertThatCode(() -> resolve(connector.healthCheck())).doesNotThrowAnyException(); // GH-90000
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Entity entity(UUID id) { // GH-90000
        Entity e = Entity.builder() // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .data(Map.of("name", "test-" + UUID.randomUUID())) // GH-90000
                .build(); // GH-90000
        if (id != null) e.setId(id); // GH-90000
        e.setCreatedAt(Instant.now()); // GH-90000
        e.setUpdatedAt(Instant.now()); // GH-90000
        return e;
    }

    private <T> T resolve(Promise<T> promise) { // GH-90000
        return runPromise(() -> promise); // GH-90000
    }
}
