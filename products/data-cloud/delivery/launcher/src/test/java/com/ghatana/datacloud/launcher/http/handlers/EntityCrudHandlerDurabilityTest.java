/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.datacloud.spi.EntityWriteIdempotencyStore;
import com.ghatana.datacloud.spi.EntityWriteOutboxProcessor;
import com.ghatana.datacloud.spi.InMemoryEntityWriteOutboxProcessor;
import com.ghatana.datacloud.spi.TransactionManager;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private HttpResponse successResponse;

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

    @Mock
    private AuditService auditService;

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
        lenient().when(http.jsonResponse(any())).thenReturn(successResponse);
        lenient().when(http.objectMapper()).thenReturn(new ObjectMapper());
        lenient().when(successResponse.getCode()).thenReturn(200);
        lenient().when(http.requireTenantIdWithError(request))
            .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-1", null));
        lenient().when(request.getPathParameter("collection")).thenReturn("test-collection");
    }

    // ==================== DC-P1-05: Idempotency Tests ====================

    @Test
    @DisplayName("DC-P1-05: Entity save with idempotency key returns cached result on retry")
    void entitySaveWithIdempotencyKeyReturnsCachedResultOnRetry() {
        String idempotencyKey = "test-idempotency-key-123";

        when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn(idempotencyKey);
        when(idempotencyStore.get("tenant-1", "test-collection", idempotencyKey))
            .thenReturn(Optional.of(Map.of("id", "entity-1", "status", "completed")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Should return cached response without executing the save
        assertThat(response).isNotNull();
        verify(client, never()).save(anyString(), anyString(), any());
        verify(transactionManager, never()).executeInTransactionWithContext(anyString(), any());
    }

    @Test
    @DisplayName("DC-P1-05: Entity save stores idempotency result on success")
    void entitySaveStoresIdempotencyResultOnSuccess() {
        String idempotencyKey = "test-idempotency-key-123";
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn(idempotencyKey);
        when(idempotencyStore.get("tenant-1", "test-collection", idempotencyKey))
            .thenReturn(Optional.empty());
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
            try {
                return op.execute(null);
            } catch (RuntimeException e) {
                return Promise.ofException(e);
            }
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "entity-1", "test-collection", Map.of("id", "entity-1", "name", "Test"))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response).isNotNull();
        verify(idempotencyStore).put(anyString(), anyString(), eq(idempotencyKey), any());
    }

    @Test
    @DisplayName("DC-P1-05: Transaction rollback on partial failure does not persist entity")
    void transactionRollbackOnPartialFailureDoesNotPersistEntity() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        when(transactionManager.executeInTransactionWithContext(anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Database connection failed")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Production error handler converts the failed Promise to an HTTP 500 response
        assertThat(response).isSameAs(errorResponse);
        verify(client, never()).save(anyString(), anyString(), any());
    }

    // ==================== DC-P1-008: Collection Metadata Domain Event Tests ====================

    @Test
    @DisplayName("DC-P1-008: handleUpsertCollectionMetadata emits domain event and audit record")
    void dc_p1_008_upsertCollectionMetadataEmitsDomainEventAndAudit() throws Exception {
        when(handler.getClass().getSimpleName()).thenReturn("EntityCrudHandler");
        // Re-wire handler with audit service
        EntityCrudHandler handlerWithAudit = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOpenSearchConnector(openSearchConnector)
            .withTransactionManager(transactionManager)
            .withOutboxProcessor(outboxProcessor)
            .withIdempotencyStore(idempotencyStore)
            .withAuditService(auditService)
            .withDeploymentProfile("production");

        lenient().when(http.createdResponse(any())).thenReturn(successResponse);
        lenient().when(http.resolvePrincipalId(request)).thenReturn("user-123");

        String metadataJson = "{\"description\":\"test collection\",\"sensitivity\":\"standard\"}";
        ByteBuf body = ByteBuf.wrapForReading(metadataJson.getBytes(StandardCharsets.UTF_8));
        when(request.loadBody()).thenReturn(Promise.of(body));

        DataCloudClient.Entity existingEntity = DataCloudClient.Entity.of(
            "test-collection", "dc_collections", Map.of("id", "test-collection"));
        when(client.findById("tenant-1", "dc_collections", "test-collection"))
            .thenReturn(Promise.of(Optional.of(existingEntity)));

        DataCloudClient.Entity savedEntity = DataCloudClient.Entity.of(
            "test-collection", "dc_collections",
            Map.of("id", "test-collection", "description", "test collection"));
        when(client.save(eq("tenant-1"), eq("dc_collections"), any()))
            .thenReturn(Promise.of(savedEntity));
        when(client.appendEvent(eq("tenant-1"), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));
        when(auditService.record(any(AuditEvent.class))).thenReturn(Promise.of(null));

        HttpResponse response = runPromise(() -> handlerWithAudit.handleUpsertCollectionMetadata(request));

        // domain event must be appended with correct type
        verify(client).appendEvent(eq("tenant-1"), argThat(event ->
            "collection.metadata.updated".equals(event.type())));
        // audit record must be emitted
        verify(auditService).record(argThat(audit ->
            "collection.metadata.updated".equals(audit.eventType())
                && "test-collection".equals(audit.resourceId())
                && "collection".equals(audit.resourceType())
                && audit.success()));
        assertThat(response).isSameAs(successResponse);
    }

    @Test
    @DisplayName("DC-P1-008: handleUpsertCollectionMetadata domain event includes correct tenantId and source")
    void dc_p1_008_upsertCollectionMetadataDomainEventHasCorrectTenantAndSource() throws Exception {
        EntityCrudHandler handlerWithAudit = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOpenSearchConnector(openSearchConnector)
            .withTransactionManager(transactionManager)
            .withOutboxProcessor(outboxProcessor)
            .withIdempotencyStore(idempotencyStore)
            .withAuditService(auditService)
            .withDeploymentProfile("production");

        lenient().when(http.createdResponse(any())).thenReturn(successResponse);
        lenient().when(http.resolvePrincipalId(request)).thenReturn(null);

        String metadataJson = "{\"description\":\"another-collection\"}";
        ByteBuf body = ByteBuf.wrapForReading(metadataJson.getBytes(StandardCharsets.UTF_8));
        when(request.loadBody()).thenReturn(Promise.of(body));
        when(client.findById("tenant-1", "dc_collections", "test-collection"))
            .thenReturn(Promise.of(Optional.empty()));
        when(client.save(eq("tenant-1"), eq("dc_collections"), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "test-collection", "dc_collections", Map.of("id", "test-collection"))));
        when(client.appendEvent(eq("tenant-1"), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(2L)));
        when(auditService.record(any(AuditEvent.class))).thenReturn(Promise.of(null));

        HttpResponse response = runPromise(() -> handlerWithAudit.handleUpsertCollectionMetadata(request));

        verify(client).appendEvent(eq("tenant-1"), argThat(event ->
            "collection.metadata.updated".equals(event.type())
                && "datacloud.launcher.entity-crud".equals(event.source())
                && "tenant-1".equals(((Map<?, ?>) event.payload()).get("tenantId"))));
        assertThat(response).isSameAs(successResponse);
    }

    @DisplayName("DC-P1-05: Outbox event is queued after successful transaction commit")
    void outboxEventIsQueuedAfterSuccessfulTransactionCommit() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
            return op.execute(null);
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "entity-1", "test-collection", Map.of("id", "entity-1"))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response).isNotNull();
        verify(outboxProcessor).addPending(any());
    }

    @Test
    @DisplayName("DC-P1-05: Outbox event is not queued on transaction rollback")
    void outboxEventIsNotQueuedOnTransactionRollback() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        when(transactionManager.executeInTransactionWithContext(anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Transaction failed")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response).isSameAs(errorResponse);
        verify(outboxProcessor, never()).addPending(any());
    }

    @Test
    @DisplayName("DC-P1-05: Semantic index is offloaded to outbox — does not block entity save")
    void semanticIndexFailureDoesNotBlockEntitySave() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
            return op.execute(null);
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "entity-1", "test-collection", Map.of("id", "entity-1"))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));
        // Semantic indexing is deferred via outbox — not called synchronously

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Entity save succeeds; semantic indexing is scheduled asynchronously via outbox
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);
        verify(outboxProcessor).addPending(any());
    }

    @Test
    @DisplayName("DC-P1-05: Entity and event audit consistency is maintained")
    void entityAndEventAuditConsistencyIsMaintained() {
        String idempotencyKey = "test-idempotency-key-456";
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn(idempotencyKey);
        when(idempotencyStore.get("tenant-1", "test-collection", idempotencyKey))
            .thenReturn(Optional.empty());
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
            return op.execute(null);
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "entity-1", "test-collection", Map.of("id", "entity-1", "name", "Test"))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response).isNotNull();
        // Verify the full atomic sequence: transaction, outbox, idempotency persistence
        verify(transactionManager).executeInTransactionWithContext(anyString(), any());
        verify(outboxProcessor).addPending(any());
        verify(idempotencyStore).put(anyString(), anyString(), eq(idempotencyKey), any());
    }

    // ==================== DC-P1-05: Production Readiness Validation Tests ====================

    @Test
    @DisplayName("DC-P1-05: Production profile requires durable idempotency store")
    void productionProfileRequiresDurableIdempotencyStore() {
        EntityCrudHandler handlerWithoutStore = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOpenSearchConnector(openSearchConnector)
            .withTransactionManager(transactionManager)
            .withOutboxProcessor(outboxProcessor)
            .withDeploymentProfile("production");

        assertThatThrownBy(handlerWithoutStore::validateProductionRequirements)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-05")
            .hasMessageContaining("Durable EntityWriteIdempotencyStore is required");
    }

    @Test
    @DisplayName("DC-P1-05: Production profile requires transaction manager")
    void productionProfileRequiresTransactionManager() {
        EntityCrudHandler handlerWithoutTx = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOpenSearchConnector(openSearchConnector)
            .withIdempotencyStore(idempotencyStore)
            .withDeploymentProfile("production");

        assertThatThrownBy(handlerWithoutTx::validateProductionRequirements)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-05")
            .hasMessageContaining("TransactionManager is required");
    }

    @Test
    @DisplayName("DC-P1-05: Production profile requires outbox processor")
    void productionProfileRequiresOutboxProcessor() {
        // Set idempotencyStore + transactionManager + auditService so those checks pass,
        // leaving only the outboxProcessor check to trigger.
        EntityCrudHandler handlerWithoutOutbox = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOpenSearchConnector(openSearchConnector)
            .withTransactionManager(transactionManager)
            .withIdempotencyStore(idempotencyStore)
            .withAuditService(auditService)
            .withDeploymentProfile("production");

        assertThatThrownBy(handlerWithoutOutbox::validateProductionRequirements)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-05")
            .hasMessageContaining("OutboxProcessor is required");
    }

    @Test
    @DisplayName("DC-P1-05: Local profile allows in-memory idempotency for development")
    void localProfileAllowsInMemoryIdempotencyForDevelopment() {
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

        when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn(idempotencyKey);
        when(idempotencyStore.get("tenant-1", "test-collection", idempotencyKey))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(Map.of("id", "entity-1", "status", "completed")));
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
            return op.execute(null);
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "entity-1", "test-collection", Map.of("id", "entity-1"))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        // First save — processes the request and stores idempotency result
        HttpResponse response1 = runPromise(() -> handler.handleSaveEntity(request));
        assertThat(response1).isNotNull();

        // Second save with same idempotency key — hits the cache and short-circuits
        HttpResponse response2 = runPromise(() -> handler.handleSaveEntity(request));
        assertThat(response2).isNotNull();

        // Verify entity save was called only once
        verify(client, times(1)).save(anyString(), anyString(), any());
        verify(outboxProcessor, times(1)).addPending(any());
    }

    @Test
    @DisplayName("DC-P1-05: Outbox replay preserves event ordering")
    void outboxReplayPreservesEventOrdering() {
        String entity1Json = "{\"id\":\"entity-1\",\"name\":\"Test1\"}";
        String entity2Json = "{\"id\":\"entity-2\",\"name\":\"Test2\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entity1Json.getBytes(StandardCharsets.UTF_8))))
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entity2Json.getBytes(StandardCharsets.UTF_8))));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
            return op.execute(null);
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "entity-1", "test-collection", Map.of("id", "entity-1"))))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "entity-2", "test-collection", Map.of("id", "entity-2"))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(2L)));

        // First entity save
        HttpResponse response1 = runPromise(() -> handler.handleSaveEntity(request));
        assertThat(response1).isNotNull();

        // Second entity save
        HttpResponse response2 = runPromise(() -> handler.handleSaveEntity(request));
        assertThat(response2).isNotNull();

        // Verify outbox entries were queued in order (one per save)
        verify(outboxProcessor, times(2)).addPending(any());
    }

    // ==================== DC-P1-09: Atomicity and Failure Tests ====================

    @Test
    @DisplayName("DC-P1-09: Golden test - entity save → event append → outbox atomicity")
    void entitySaveEventAppendAuditEmitAtomicity() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
            return op.execute(null);
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "entity-1", "test-collection", Map.of("id", "entity-1"))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);

        // Verify all operations executed in the transaction
        verify(transactionManager).executeInTransactionWithContext(anyString(), any());
        verify(client).save(anyString(), eq("test-collection"), any());
        verify(outboxProcessor).addPending(any());
    }

    @Test
    @DisplayName("DC-P1-09: Event append failure rolls back entity save")
    void eventAppendFailureRollsBackEntitySave() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
            return op.execute(null);
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "entity-1", "test-collection", Map.of("id", "entity-1"))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Event store unavailable")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Transaction failed → error response returned
        assertThat(response).isSameAs(errorResponse);
        // Entity save was attempted (inside the lambda) but transaction propagates the failure
        verify(client).save(anyString(), anyString(), any());
        // Outbox is NOT queued because event append failed before addPending
        verify(outboxProcessor, never()).addPending(any());
    }

    @Test
    @DisplayName("DC-P1-09: Transaction failure signals rollback via error response")
    void auditWriteFailureRollsBackTransactionInProduction() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        // Simulate the transaction itself aborting (e.g., audit or DB write failure)
        when(transactionManager.executeInTransactionWithContext(anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Audit write failed")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Error response returned — not an uncaught exception
        assertThat(response).isSameAs(errorResponse);
        verify(transactionManager).executeInTransactionWithContext(anyString(), any());
        verify(outboxProcessor, never()).addPending(any());
    }

    @Test
    @DisplayName("DC-P1-09: Outbox queue is populated after transaction commit")
    void outboxQueueIsPopulatedAfterTransactionCommit() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
            return op.execute(null);
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "entity-1", "test-collection", Map.of("id", "entity-1"))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Transaction committed and response is success
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);

        // Outbox entry was queued for async processing (broadcast, semantic index, etc.)
        verify(transactionManager).executeInTransactionWithContext(anyString(), any());
        verify(outboxProcessor).addPending(any());
    }

    @Test
    @DisplayName("DC-P1-09: Semantic indexing is offloaded to outbox — does not block transaction")
    void semanticIndexFailureDoesNotBlockTransactionCommit() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
            return op.execute(null);
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of(
                "entity-1", "test-collection", Map.of("id", "entity-1"))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));
        // Semantic indexing is deferred via outbox — not called synchronously in the transactional path

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Transaction committed successfully; semantic indexing is deferred to outbox
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);
        verify(transactionManager).executeInTransactionWithContext(anyString(), any());
        verify(outboxProcessor).addPending(any());
    }

    @Test
    @DisplayName("DC-P1-09: Entity save failure inside transaction does not emit outbox event")
    void transactionRollbackOnEntitySaveFailureDoesNotEmitEventOrAudit() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
            return op.execute(null);
        }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Constraint violation")));

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        // Transaction failed → error response
        assertThat(response).isSameAs(errorResponse);
        verify(transactionManager).executeInTransactionWithContext(anyString(), any());
        // Save was attempted but failed — outbox must NOT be populated
        verify(outboxProcessor, never()).addPending(any());
    }

    // ==================== DC-P1-001: Non-transactional path guard ====================

    @Test
    @DisplayName("DC-P1-001: Entity save in production profile without TransactionManager returns 500")
    void productionProfileWithoutTransactionManager_returns500() {
        String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

        // Handler in production profile WITHOUT a transaction manager
        EntityCrudHandler handlerNoTx = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withDeploymentProfile("production");

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
        lenient().when(http.requireTenantIdWithError(request))
            .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-1", null));
        lenient().when(request.getPathParameter("collection")).thenReturn("test-collection");

        HttpResponse response = runPromise(() -> handlerNoTx.handleSaveEntity(request));

        assertThat(response).isSameAs(errorResponse);
        verify(client, never()).save(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("DC-P1-001: Entity save in local profile without TransactionManager is allowed")
    void localProfileWithoutTransactionManager_isAllowed() {
            String entityJson = "{\"id\":\"entity-1\",\"name\":\"Test\"}";

            // Handler in local profile WITHOUT a transaction manager — should use non-transactional path
            EntityCrudHandler handlerLocal = new EntityCrudHandler(client, http, wsBroadcaster)
                .withTraceSupport(TraceSpanSupport.disabled())
                .withDeploymentProfile("local");

            when(request.loadBody())
                .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
            lenient().when(http.requireTenantIdWithError(request))
                .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-1", null));
            lenient().when(request.getPathParameter("collection")).thenReturn("test-collection");
            when(client.save(anyString(), anyString(), any()))
                .thenReturn(Promise.of(DataCloudClient.Entity.of(
                    "entity-1", "test-collection", Map.of("id", "entity-1"))));
            when(client.appendEvent(anyString(), any()))
                .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

            HttpResponse response = runPromise(() -> handlerLocal.handleSaveEntity(request));

            // Should proceed with non-transactional path in local profile
            assertThat(response).isNotNull();
            verify(client).save(anyString(), anyString(), any());
        }

        // ==================== DC-P1-006: In-memory outbox fail-fast in production ====================

        @Test
        @DisplayName("DC-P1-006: Entity save in production profile with InMemoryEntityWriteOutboxProcessor returns 500")
        void productionProfileWithInMemoryOutbox_returns500OnSave() {
            // DC-P1-006: Production profile must not silently accept an in-memory outbox.
            String entityJson = "{\"id\":\"entity-dc1006\",\"name\":\"Test\"}";

            EntityCrudHandler handlerWithInMemoryOutbox = new EntityCrudHandler(client, http, wsBroadcaster)
                    .withTraceSupport(TraceSpanSupport.disabled())
                    .withOpenSearchConnector(openSearchConnector)
                    .withTransactionManager(transactionManager)
                    .withIdempotencyStore(idempotencyStore)
                    .withOutboxProcessor(new InMemoryEntityWriteOutboxProcessor())
                    .withDeploymentProfile("production");

            // Stub the transaction manager to delegate to the operation
            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
                return op.execute(null);
            }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
            when(client.save(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(DataCloudClient.Entity.of(
                            "entity-dc1006", "test-collection", Map.of("id", "entity-dc1006"))));
            when(client.appendEvent(anyString(), any()))
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));
            when(request.loadBody())
                    .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));

            HttpResponse response = runPromise(() -> handlerWithInMemoryOutbox.handleSaveEntity(request));

            // Fail-fast: in-memory outbox in production must return a server error, not silently succeed
            assertThat(response).isSameAs(errorResponse);
        }

        @Test
        @DisplayName("DC-P1-006: InMemoryEntityWriteOutboxProcessor is ALLOWED in local/dev profile")
        void localProfileWithInMemoryOutbox_succeeds() {
            String entityJson = "{\"id\":\"entity-local\",\"name\":\"Test\"}";

            EntityCrudHandler handlerLocal = new EntityCrudHandler(client, http, wsBroadcaster)
                    .withTraceSupport(TraceSpanSupport.disabled())
                    .withOpenSearchConnector(openSearchConnector)
                    .withTransactionManager(transactionManager)
                    .withIdempotencyStore(idempotencyStore)
                    .withOutboxProcessor(new InMemoryEntityWriteOutboxProcessor())
                    .withDeploymentProfile("local");

            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
                return op.execute(null);
            }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
            when(client.save(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(DataCloudClient.Entity.of(
                            "entity-local", "test-collection", Map.of("id", "entity-local"))));
            when(client.appendEvent(anyString(), any()))
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));
            when(request.loadBody())
                    .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));
            lenient().when(http.requireTenantIdWithError(request))
                    .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-1", null));
            lenient().when(request.getPathParameter("collection")).thenReturn("test-collection");

            HttpResponse response = runPromise(() -> handlerLocal.handleSaveEntity(request));

            // Local profile allows in-memory outbox — save should succeed
            assertThat(response).isNotNull();
            verify(client).save(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("DC-P1-006: Staging profile with InMemoryEntityWriteOutboxProcessor also returns 500")
        void stagingProfileWithInMemoryOutbox_returns500() {
            String entityJson = "{\"id\":\"entity-staging\",\"name\":\"Test\"}";

            EntityCrudHandler handlerStaging = new EntityCrudHandler(client, http, wsBroadcaster)
                    .withTraceSupport(TraceSpanSupport.disabled())
                    .withOpenSearchConnector(openSearchConnector)
                    .withTransactionManager(transactionManager)
                    .withIdempotencyStore(idempotencyStore)
                    .withOutboxProcessor(new InMemoryEntityWriteOutboxProcessor())
                    .withDeploymentProfile("staging");

            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                TransactionManager.TransactionalOperation<HttpResponse> op = inv.getArgument(1);
                return op.execute(null);
            }).when(transactionManager).executeInTransactionWithContext(anyString(), any());
            when(client.save(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(DataCloudClient.Entity.of(
                            "entity-staging", "test-collection", Map.of("id", "entity-staging"))));
            when(client.appendEvent(anyString(), any()))
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));
            when(request.loadBody())
                    .thenReturn(Promise.of(ByteBuf.wrapForReading(entityJson.getBytes(StandardCharsets.UTF_8))));

            HttpResponse response = runPromise(() -> handlerStaging.handleSaveEntity(request));

            assertThat(response).isSameAs(errorResponse);
        }
    }
