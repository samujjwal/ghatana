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

@ExtendWith(MockitoExtension.class) 
@MockitoSettings(strictness = Strictness.LENIENT) 
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
    static final UUID COLLECTION_ID = UUID.randomUUID(); 

    @BeforeEach
    void setUp() { 
        doNothing().when(metrics).incrementCounter(anyString(), any(String[].class)); 
        doNothing().when(metrics).recordTimer(anyString(), anyLong(), any(String[].class)); 
        doNothing().when(auditLogger).logDataModification(any(), any(), any(), any(), anyBoolean()); 
        when(entityRepository.count(anyString(), anyString())).thenReturn(Promise.of(0L)); 
        connector = new PostgresJsonbConnector(entityRepository, metrics, auditLogger); 
    }

    // ── constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_null_repository_throwsNPE() { 
        assertThatNullPointerException() 
                .isThrownBy(() -> new PostgresJsonbConnector(null, metrics, auditLogger)); 
    }

    @Test
    void constructor_null_metrics_throwsNPE() { 
        assertThatNullPointerException() 
                .isThrownBy(() -> new PostgresJsonbConnector(entityRepository, null, auditLogger)); 
    }

    @Test
    void constructor_null_auditLogger_throwsNPE() { 
        assertThatNullPointerException() 
                .isThrownBy(() -> new PostgresJsonbConnector(entityRepository, metrics, null)); 
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_savesEntityAndReturnsResult() { 
        Entity input = entity(null); 
        Entity saved = entity(UUID.randomUUID()); 
        when(entityRepository.save(eq(TENANT), any(Entity.class))) 
                .thenReturn(Promise.of(saved)); 

        Entity result = resolve(connector.create(input)); 

        assertThat(result).isEqualTo(saved); 
        verify(entityRepository).save(eq(TENANT), any(Entity.class)); 
    }

    @Test
    void create_generatesId_whenNull() { 
        Entity input = entity(null); 
        when(entityRepository.save(eq(TENANT), any(Entity.class))) 
                .thenAnswer(inv -> Promise.of(inv.getArgument(1))); 

        Entity result = resolve(connector.create(input)); 

        assertThat(result.getId()).isNotNull(); 
    }

    @Test
    void create_null_throwsNPE() { 
        assertThatNullPointerException().isThrownBy(() -> connector.create(null)); 
    }

    @Test
    void create_logsAuditOnSuccess() { 
        Entity input = entity(null); 
        Entity saved = entity(UUID.randomUUID()); 
        when(entityRepository.save(eq(TENANT), any(Entity.class))) 
                .thenReturn(Promise.of(saved)); 

        resolve(connector.create(input)); 

        verify(auditLogger).logDataModification(eq(TENANT), eq("CREATE"), eq(COLLECTION), any(), eq(true));
    }

    // ── read ─────────────────────────────────────────────────────────────────

    @Test
    void read_returnsEntity_whenFound() { 
        UUID id = UUID.randomUUID(); 
        Entity found = entity(id); 
        // M7: connector passes collectionId.toString() — use anyString() not eq(COLLECTION) 
        when(entityRepository.findById(eq(TENANT), anyString(), eq(id))) 
                .thenReturn(Promise.of(Optional.of(found))); 

        Optional<Entity> result = resolve(connector.read(COLLECTION_ID, TENANT, id)); 

        assertThat(result).isPresent(); 
        assertThat(result.get().getId()).isEqualTo(id); 
    }

    @Test
    void read_returnsEmpty_whenNotFound() { 
        UUID id = UUID.randomUUID(); 
        // M7: connector passes collectionId.toString() — use anyString() not eq(COLLECTION) 
        when(entityRepository.findById(eq(TENANT), anyString(), eq(id))) 
                .thenReturn(Promise.of(Optional.empty())); 

        Optional<Entity> result = resolve(connector.read(COLLECTION_ID, TENANT, id)); 

        assertThat(result).isEmpty(); 
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_savesAndReturnsUpdatedEntity() { 
        Entity input = entity(UUID.randomUUID()); 
        // M7: update() calls only save(), not findById() 
        when(entityRepository.save(eq(TENANT), any(Entity.class))) 
                .thenReturn(Promise.of(input)); 

        Entity result = resolve(connector.update(input)); 

        assertThat(result.getId()).isEqualTo(input.getId()); 
        verify(entityRepository).save(eq(TENANT), any(Entity.class)); 
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_invokesRepository() { 
        UUID id = UUID.randomUUID(); 
        // M7: connector passes collectionId.toString() — use anyString() not eq(COLLECTION) 
        when(entityRepository.delete(eq(TENANT), anyString(), eq(id))) 
                .thenReturn(Promise.of(null)); 

        assertThatCode(() -> resolve(connector.delete(COLLECTION_ID, TENANT, id))) 
                .doesNotThrowAnyException(); 

        verify(entityRepository).delete(eq(TENANT), anyString(), eq(id)); 
    }

    // ── query ────────────────────────────────────────────────────────────────

    @Test
    void query_delegatesToRepository() { 
        List<Entity> entities = List.of(entity(UUID.randomUUID()), entity(UUID.randomUUID())); 
        // M7: connector calls findByQuery (not findAll) and count (not countByFilter) 
        when(entityRepository.findByQuery(eq(TENANT), anyString(), any())) 
                .thenReturn(Promise.of(entities)); 
        when(entityRepository.count(eq(TENANT), anyString())) 
                .thenReturn(Promise.of(2L)); 

        QuerySpec spec = QuerySpec.builder().limit(10).offset(0).build(); 
        StorageConnector.QueryResult result = resolve(connector.query(COLLECTION_ID, TENANT, spec)); 

        assertThat(result.entities()).hasSize(2); 
        assertThat(result.total()).isEqualTo(2); 
    }

    // ── count ────────────────────────────────────────────────────────────────

    @Test
    void count_delegatesToRepository() { 
        // M7: connector calls count() not countByFilter(); collectionName = collectionId.toString() 
        when(entityRepository.count(eq(TENANT), anyString())) 
                .thenReturn(Promise.of(5L)); 

        long count = resolve(connector.count(COLLECTION_ID, TENANT, null)); 

        assertThat(count).isEqualTo(5L); 
    }

    // ── metadata ─────────────────────────────────────────────────────────────

    @Test
    void getMetadata_returnsRelationalBackendType() { 
        assertThat(connector.getMetadata().backendType().name()).isEqualTo("RELATIONAL");
    }

    @Test
    void healthCheck_completesWithoutError() { 
        assertThatCode(() -> resolve(connector.healthCheck())).doesNotThrowAnyException(); 
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Entity entity(UUID id) { 
        Entity e = Entity.builder() 
                .tenantId(TENANT) 
                .collectionName(COLLECTION) 
                .data(Map.of("name", "test-" + UUID.randomUUID())) 
                .build(); 
        if (id != null) e.setId(id); 
        e.setCreatedAt(Instant.now()); 
        e.setUpdatedAt(Instant.now()); 
        return e;
    }

    private <T> T resolve(Promise<T> promise) { 
        return runPromise(() -> promise); 
    }
}
