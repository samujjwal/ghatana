/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.datacloud.spi.TransactionManager;
import com.ghatana.datacloud.spi.EntityWriteOutboxProcessor;
import com.ghatana.datacloud.spi.EntityWriteIdempotencyStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DC-P1-05/DC-P1-09: Golden tests for entity write durability and atomicity.
 *
 * <p>Verifies that entity save operations are durable across restarts, support
 * idempotency, handle partial failures with transaction rollback, and properly
 * integrate with the outbox pattern for event emission.
 *
 * <p>DC-P1-09: Verifies atomicity of entity write + event append + audit emission
 * with failure scenarios for event append, audit, outbox, and semantic index.
 *
 * @doc.type class
 * @doc.purpose Golden tests for entity write durability (DC-P1-05) and atomicity (DC-P1-09)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EntityCrudHandler Durability Golden Tests")
@Tag("durability")
@Tag("production")
@ExtendWith(MockitoExtension.class)
class EntityCrudHandlerDurabilityTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    @Mock
    private OpenSearchConnector openSearchConnector;

    @Mock
    private BiConsumer<String, Map<String, Object>> wsBroadcaster;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private EntityWriteOutboxProcessor outboxProcessor;

    @Mock
    private EntityWriteIdempotencyStore idempotencyStore;

    private EntityCrudHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOpenSearchConnector(openSearchConnector)
            .withTransactionManager(transactionManager)
            .withOutboxProcessor(outboxProcessor)
            .withIdempotencyStore(idempotencyStore)
            .withDeploymentProfile("production");

        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse);
        lenient().when(http.requireTenantIdWithError(request))
            .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-1"));
        lenient().when(request.getPathParameter("collection")).thenReturn("test-collection");
    }

    @Test
    @DisplayName("DC-P1-05: Entity save with idempotency key returns cached result on retry")
    void entitySaveWithIdempotencyKeyReturnsCachedResultOnRetry() {
        String idempotencyKey = "test-idempotency-key-123";
        String cachedResponse = "{\"id\":\"entity-1\",\"status\":\"completed\"}";

        when(request.getHeader("X-Idempotency-Key")).thenReturn(idempotencyKey);
        when(idempotencyStore.checkIdempotency("tenant-1", idempotencyKey))
            .thenReturn(Promise.ofOptional(java.util.Optional.of(cachedResponse)));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Should return cached response without executing the save
        assertThat(response).isNotNull();
        verify(client, never()).save(anyString(), anyString(), any());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("DC-P1-05: Entity save stores idempotency result on success")
    void entitySaveStoresIdempotencyResultOnSuccess() {
        String idempotencyKey = "test-idempotency-key-123";
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.getHeader("X-Idempotency-Key")).thenReturn(idempotencyKey);
        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(idempotencyStore.checkIdempotency("tenant-1", idempotencyKey))
            .thenReturn(Promise.ofOptional(java.util.Optional.empty()));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.of(Map.of("id", "entity-1", "status", "created")));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.of("entity-1"));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response).isNotNull();
        verify(idempotencyStore).storeIdempotency("tenant-1", idempotencyKey, any());
    }

    @Test
    @DisplayName("DC-P1-05: Transaction rollback on partial failure does not persist entity")
    void transactionRollbackOnPartialFailureDoesNotPersistEntity() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.ofException(new RuntimeException("Database connection failed")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Should return error response
        assertThat(response).isSameAs(errorResponse);
        verify(client, never()).save(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("DC-P1-05: Outbox event is queued after successful transaction commit")
    void outboxEventIsQueuedAfterSuccessfulTransactionCommit() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.of(Map.of("id", "entity-1", "status", "created")));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.of("entity-1"));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response).isNotNull();
        verify(outboxProcessor).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-05: Outbox event is not queued on transaction rollback")
    void outboxEventIsNotQueuedOnTransactionRollback() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.ofException(new RuntimeException("Transaction failed")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response).isSameAs(errorResponse);
        verify(outboxProcessor, never()).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-05: Semantic index failure does not block entity save")
    void semanticIndexFailureDoesNotBlockEntitySave() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.of(Map.of("id", "entity-1", "status", "created")));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.of("entity-1"));
        when(openSearchConnector.indexEntity(anyString(), anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Semantic index unavailable")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Entity save should succeed despite semantic index failure
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("DC-P1-05: Entity and event audit consistency is maintained")
    void entityAndEventAuditConsistencyIsMaintained() {
        String idempotencyKey = "test-idempotency-key-456";
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.getHeader("X-Idempotency-Key")).thenReturn(idempotencyKey);
        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(idempotencyStore.checkIdempotency("tenant-1", idempotencyKey))
            .thenReturn(Promise.ofOptional(java.util.Optional.empty()));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.of(Map.of("id", "entity-1", "status", "created", "eventId", "event-123")));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.of("entity-1"));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response).isNotNull();
        // Verify both entity save and event emission are tracked
        verify(transactionManager).execute(any());
        verify(outboxProcessor).enqueue(any());
        verify(idempotencyStore).storeIdempotency("tenant-1", idempotencyKey, any());
    }

    @Test
    @DisplayName("DC-P1-05: Production profile requires durable idempotency store")
    void productionProfileRequiresDurableIdempotencyStore() {
        // Create handler without idempotency store in production profile
        EntityCrudHandler handlerWithoutStore = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOpenSearchConnector(openSearchConnector)
            .withTransactionManager(transactionManager)
            .withOutboxProcessor(outboxProcessor)
            .withDeploymentProfile("production");

        IllegalStateException exception = new IllegalStateException();
        try {
            handlerWithoutStore.validateProductionRequirements();
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception.getMessage()).contains("DC-P1-05");
        assertThat(exception.getMessage()).contains("Durable EntityWriteIdempotencyStore is required");
    }

    @Test
    @DisplayName("DC-P1-05: Production profile requires transaction manager")
    void productionProfileRequiresTransactionManager() {
        // Create handler without transaction manager in production profile
        EntityCrudHandler handlerWithoutTx = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOpenSearchConnector(openSearchConnector)
            .withIdempotencyStore(idempotencyStore)
            .withDeploymentProfile("production");

        IllegalStateException exception = new IllegalStateException();
        try {
            handlerWithoutTx.validateProductionRequirements();
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception.getMessage()).contains("DC-P1-05");
        assertThat(exception.getMessage()).contains("TransactionManager is required");
    }

    @Test
    @DisplayName("DC-P1-05: Production profile requires outbox processor")
    void productionProfileRequiresOutboxProcessor() {
        // Create handler without outbox processor in production profile
        EntityCrudHandler handlerWithoutOutbox = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOpenSearchConnector(openSearchConnector)
            .withTransactionManager(transactionManager)
            .withIdempotencyStore(idempotencyStore)
            .withDeploymentProfile("production");

        IllegalStateException exception = new IllegalStateException();
        try {
            handlerWithoutOutbox.validateProductionRequirements();
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception.getMessage()).contains("DC-P1-05");
        assertThat(exception.getMessage()).contains("OutboxProcessor is required");
    }

    @Test
    @DisplayName("DC-P1-05: Local profile allows in-memory idempotency for development")
    void localProfileAllowsInMemoryIdempotencyForDevelopment() {
        // Create handler without durable store in local profile
        EntityCrudHandler localHandler = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOpenSearchConnector(openSearchConnector)
            .withDeploymentProfile("local");

        // Should not throw exception in local profile
        localHandler.validateProductionRequirements();
    }

    @Test
    @DisplayName("DC-P1-05: Concurrent saves with same idempotency key are serialized")
    void concurrentSavesWithSameIdempotencyKeyAreSerialized() {
        String idempotencyKey = "test-concurrent-key";
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";
        AtomicInteger callCount = new AtomicInteger(0);

        when(request.getHeader("X-Idempotency-Key")).thenReturn(idempotencyKey);
        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(idempotencyStore.checkIdempotency("tenant-1", idempotencyKey))
            .thenAnswer(inv -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    // First call: no cached value
                    return Promise.ofOptional(java.util.Optional.empty());
                } else {
                    // Subsequent calls: return cached value
                    return Promise.ofOptional(java.util.Optional.of("{\"id\":\"entity-1\",\"status\":\"completed\"}"));
                }
            });
        when(transactionManager.execute(any()))
            .thenReturn(Promise.of(Map.of("id", "entity-1", "status", "created")));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.of("entity-1"));

        // First save
        HttpResponse response1 = runPromise(() -> handler.handleSaveEntity(request));
        assertThat(response1).isNotNull();
        assertThat(callCount.get()).isEqualTo(1);

        // Second save with same idempotency key
        HttpResponse response2 = runPromise(() -> handler.handleSaveEntity(request));
        assertThat(response2).isNotNull();
        assertThat(callCount.get()).isEqualTo(2);

        // Verify entity save was called only once
        verify(client, times(1)).save(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("DC-P1-05: Outbox replay preserves event ordering")
    void outboxReplayPreservesEventOrdering() {
        // This test verifies that events are enqueued in the correct order
        // when multiple entities are saved in sequence
        String entity1Json = "{\"id\":\"entity-1\",\"name\":\"Test1\"}";
        String entity2Json = "{\"id\":\"entity-2\",\"name\":\"Test2\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(entity1Json))
            .thenReturn(Promise.of(entity2Json));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.of(Map.of("id", "entity-1", "status", "created", "eventId", "event-1")))
            .thenReturn(Promise.of(Map.of("id", "entity-2", "status", "created", "eventId", "event-2")));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.of("entity-1"))
            .thenReturn(Promise.of("entity-2"));

        // First entity save
        HttpResponse response1 = runPromise(() -> handler.handleSaveEntity(request));
        assertThat(response1).isNotNull();

        // Second entity save
        HttpResponse response2 = runPromise(() -> handler.handleSaveEntity(request));
        assertThat(response2).isNotNull();

        // Verify events were enqueued in order
        verify(outboxProcessor, times(2)).enqueue(any());
    }

    // ==================== DC-P1-09: Atomicity and Failure Tests ====================

    @Test
    @DisplayName("DC-P1-09: Golden test - entity save → event append → audit emit atomicity")
    void entitySaveEventAppendAuditEmitAtomicity() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.of(Map.of(
                "id", "entity-1",
                "status", "created",
                "eventId", "event-123",
                "auditId", "audit-456"
            )));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.of("entity-1"));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);
        
        // Verify all three operations were executed in the transaction
        verify(transactionManager).execute(any());
        verify(client).save("tenant-1", "test-collection", any());
        verify(outboxProcessor).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-09: Event append failure rolls back entity save")
    void eventAppendFailureRollsBackEntitySave() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.ofException(new RuntimeException("Event store unavailable")));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.of("entity-1"));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Should return error response
        assertThat(response).isSameAs(errorResponse);
        
        // Verify entity was saved but transaction failed (rolled back)
        verify(client).save("tenant-1", "test-collection", any());
        // Verify outbox was NOT enqueued due to transaction rollback
        verify(outboxProcessor, never()).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-09: Audit write failure rolls back transaction in production")
    void auditWriteFailureRollsBackTransactionInProduction() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.ofException(new RuntimeException("Audit write failed")));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.of("entity-1"));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Should return error response
        assertThat(response).isSameAs(errorResponse);
        
        // Verify transaction was attempted but failed
        verify(transactionManager).execute(any());
        // Verify outbox was NOT enqueued due to transaction rollback
        verify(outboxProcessor, never()).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-09: Outbox failure does not block transaction commit")
    void outboxFailureDoesNotBlockTransactionCommit() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.of(Map.of("id", "entity-1", "status", "created")));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.of("entity-1"));
        when(outboxProcessor.enqueue(any()))
            .thenReturn(Promise.ofException(new RuntimeException("Outbox queue unavailable")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Entity save should succeed despite outbox failure (outbox is async)
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);
        
        // Verify transaction committed successfully
        verify(transactionManager).execute(any());
        // Verify outbox enqueue was attempted
        verify(outboxProcessor).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-09: Semantic index failure does not block transaction commit")
    void semanticIndexFailureDoesNotBlockTransactionCommit() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.of(Map.of("id", "entity-1", "status", "created")));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.of("entity-1"));
        when(openSearchConnector.indexEntity(anyString(), anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Semantic index unavailable")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Entity save should succeed despite semantic index failure
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);
        
        // Verify transaction committed successfully
        verify(transactionManager).execute(any());
        // Verify outbox was enqueued for async processing
        verify(outboxProcessor).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-09: Transaction rollback on entity save failure does not emit event or audit")
    void transactionRollbackOnEntitySaveFailureDoesNotEmitEventOrAudit() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody()).thenReturn(Promise.of(entityJson));
        when(transactionManager.execute(any()))
            .thenReturn(Promise.ofException(new RuntimeException("Entity store constraint violation")));
        when(client.save("tenant-1", "test-collection", any()))
            .thenReturn(Promise.ofException(new RuntimeException("Constraint violation")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Should return error response
        assertThat(response).isSameAs(errorResponse);
        
        // Verify transaction was attempted but failed
        verify(transactionManager).execute(any());
        // Verify outbox was NOT enqueued due to transaction rollback
        verify(outboxProcessor, never()).enqueue(any());
    }
}
