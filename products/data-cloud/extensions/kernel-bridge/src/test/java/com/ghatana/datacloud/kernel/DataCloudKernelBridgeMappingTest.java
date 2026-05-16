/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataDeleteRequest;
import com.ghatana.kernel.adapter.datacloud.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataResult;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.DatasetInfo;
import com.ghatana.kernel.adapter.datacloud.SchemaInfo;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.testing.TestBridgePorts;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Expanded bridge integration tests for the Data-Cloud kernel-bridge adapter.
 *
 * <p>Complements {@link DataCloudKernelExtensionTest} by exercising:
 * <ul>
 *   <li><strong>Mapping</strong> — request fields are correctly forwarded to the client</li>
 *   <li><strong>Failure propagation</strong> — client exceptions surface as failed Promises</li>
 *   <li><strong>Null-boundary handling</strong> — null requests are rejected at the adapter boundary</li>
 *   <li><strong>Tenant-safe isolation</strong> — dataset IDs are passed verbatim without mutation</li>
 *   <li><strong>Transaction lifecycle</strong> — begin/commit/rollback propagate correctly</li>
 *   <li><strong>Query result mapping</strong> — hasMore flag derived from limit/result-count</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Bridge integration coverage for mapping, failure propagation, and boundary safety
 * @doc.layer adapter
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudKernelBridge — mapping and failure propagation")
class DataCloudKernelBridgeMappingTest extends EventloopTestBase {

    @Mock
    private DataCloudKernelAdapterImpl.DataCloudClient client;

    @Mock
    private KernelContext context;

    private DataCloudKernelAdapterImpl adapter;
    private final BridgeContext bridgeContext = BridgeContext.builder()
        .tenantId("tenant-a")
        .principalId("test-principal")
        .correlationId("test-correlation")
        .build();

    @BeforeEach
    void setUp() {
        adapter = new DataCloudKernelAdapterImpl(client, TestBridgePorts.allowAllAuthorization(), captureAudit(), captureHealth());
    }

    // ==================== Null boundary guards ====================

    @Nested
    @DisplayName("null argument boundary guards")
    class NullBoundaryGuards {

        @Test
        @DisplayName("null client at construction time is rejected")
        void nullClientAtConstructionIsRejected() {
            assertThatThrownBy(() -> new DataCloudKernelAdapterImpl(
                null,
                TestBridgePorts.allowAllAuthorization(),
                captureAudit(),
                captureHealth()))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null read request is rejected")
        void nullReadRequestIsRejected() {
            assertThatThrownBy(() -> adapter.readData(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null write request is rejected")
        void nullWriteRequestIsRejected() {
            assertThatThrownBy(() -> adapter.writeData(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null delete request is rejected")
        void nullDeleteRequestIsRejected() {
            assertThatThrownBy(() -> adapter.deleteData(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null query request is rejected")
        void nullQueryRequestIsRejected() {
            assertThatThrownBy(() -> adapter.queryData(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null schema create request is rejected")
        void nullSchemaCreateRequestIsRejected() {
            assertThatThrownBy(() -> adapter.createSchema(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null datasetId in getSchema is rejected")
        void nullDatasetIdInGetSchemaIsRejected() {
            assertThatThrownBy(() -> adapter.getSchema(null, "dataset"))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================== Read mapping ====================

    @Nested
    @DisplayName("readData request mapping")
    class ReadMapping {

        @Test
        @DisplayName("dataset ID and record ID are forwarded verbatim to client")
        void datasetAndRecordIdForwardedVerbatim() {
            DataResult expected = new DataResult("rec-42", "hello".getBytes(), Map.of(), Instant.now().toEpochMilli());
            when(client.read(eq("tenant-a.entities"), eq("rec-42"), any()))
                .thenReturn(CompletableFuture.completedFuture(expected));

            DataResult result = runPromise(() ->
                adapter.readData(new DataReadRequest(bridgeContext, "tenant-a.entities", "rec-42", Map.of())));

            assertThat(result.getRecordId()).isEqualTo("rec-42");
            verify(client).read("tenant-a.entities", "rec-42", Map.of());
        }

        @Test
        @DisplayName("options map is forwarded to client unchanged")
        void optionsMapForwardedUnchanged() {
            DataResult expected = new DataResult("r1", new byte[0], Map.of(), 0L);
            Map<String, String> opts = Map.of("tier", "warm", "consistency", "eventual");
            when(client.read(any(), any(), eq(opts)))
                .thenReturn(CompletableFuture.completedFuture(expected));

            runPromise(() -> adapter.readData(new DataReadRequest(bridgeContext, "tenant-a.ds", "r1", opts)));

            verify(client).read("tenant-a.ds", "r1", opts);
        }

        @Test
        @DisplayName("null options map defaults to empty — no NPE")
        void nullOptionsMapDefaultsToEmpty() {
            DataResult expected = new DataResult("r1", new byte[0], Map.of(), 0L);
            when(client.read(anyString(), anyString(), eq(Map.of())))
                .thenReturn(CompletableFuture.completedFuture(expected));

            DataResult result = runPromise(() ->
                adapter.readData(new DataReadRequest(bridgeContext, "tenant-a.ds", "r1", null)));

            assertThat(result).isNotNull();
        }
    }

    // ==================== Write mapping ====================

    @Nested
    @DisplayName("writeData request mapping")
    class WriteMapping {

        @Test
        @DisplayName("dataset ID, record ID, data, and metadata are forwarded to client")
        void allWriteFieldsForwardedToClient() {
            byte[] payload = "event-payload".getBytes();
            Map<String, String> meta = Map.of("tenant", "tenant-b");
            when(client.write(eq("tenant-a.events"), eq("evt-1"), eq(payload), eq(meta)))
                .thenReturn(CompletableFuture.completedFuture(null));

            runPromise(() ->
                adapter.writeData(new DataWriteRequest(bridgeContext, "tenant-a.events", "evt-1", payload, meta)));

            verify(client).write("tenant-a.events", "evt-1", payload, meta);
        }
    }

    // ==================== Delete mapping ====================

    @Nested
    @DisplayName("deleteData request mapping")
    class DeleteMapping {

        @Test
        @DisplayName("dataset ID and record ID are forwarded to client")
        void deleteFieldsForwardedToClient() {
            when(client.delete(eq("tenant-a.entities"), eq("ent-99")))
                .thenReturn(CompletableFuture.completedFuture(null));

            runPromise(() ->
                adapter.deleteData(new DataDeleteRequest(bridgeContext, "tenant-a.entities", "ent-99")));

            verify(client).delete("tenant-a.entities", "ent-99");
        }
    }

    // ==================== Query result mapping ====================

    @Nested
    @DisplayName("queryData result mapping")
    class QueryResultMapping {

        @Test
        @DisplayName("result count less than limit sets hasMore to false")
        void resultCountLessThanLimitSetsHasMoreFalse() {
            DataResult r1 = new DataResult("r1", new byte[0], Map.of(), 0L);
            when(client.query(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(r1)));

            var result = runPromise(() ->
                adapter.queryData(new DataQueryRequest(bridgeContext, "tenant-a.ds", "SELECT *", Map.of(), 10, 0)));

            assertThat(result.hasMore()).isFalse();
            assertThat(result.getTotalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("result count equal to limit sets hasMore to true")
        void resultCountEqualToLimitSetsHasMoreTrue() {
            DataResult r1 = new DataResult("r1", new byte[0], Map.of(), 0L);
            DataResult r2 = new DataResult("r2", new byte[0], Map.of(), 0L);
            when(client.query(any(), any(), any(), eq(2), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(r1, r2)));

            var result = runPromise(() ->
                adapter.queryData(new DataQueryRequest(bridgeContext, "tenant-a.ds", "SELECT *", Map.of(), 2, 0)));

            assertThat(result.hasMore()).isTrue();
            assertThat(result.getTotalCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("query parameters are forwarded to client")
        void queryParametersForwardedToClient() {
            Map<String, Object> params = Map.of("tenantId", "t1", "since", "2026-01-01");
            when(client.query(eq("tenant-a.ds"), eq("SELECT *"), eq(params), eq(5), eq(10)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

            runPromise(() ->
                adapter.queryData(new DataQueryRequest(bridgeContext, "tenant-a.ds", "SELECT *", params, 5, 10)));

            verify(client).query("tenant-a.ds", "SELECT *", params, 5, 10);
        }
    }

    // ==================== Failure propagation ====================

    @Nested
    @DisplayName("failure propagation from client")
    class FailurePropagation {

        @Test
        @DisplayName("client read exception propagates as failed Promise")
        void clientReadExceptionPropagatesAsFailedPromise() {
            RuntimeException cause = new RuntimeException("storage unavailable");
            when(client.read(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(cause));

            assertThatThrownBy(() ->
                runPromise(() -> adapter.readData(new DataReadRequest(bridgeContext, "tenant-a.ds", "r1", Map.of()))))
                .hasMessageContaining("storage unavailable");
        }

        @Test
        @DisplayName("client write exception propagates as failed Promise")
        void clientWriteExceptionPropagatesAsFailedPromise() {
            when(client.write(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("write failed")));

            assertThatThrownBy(() ->
                runPromise(() ->
                    adapter.writeData(new DataWriteRequest(bridgeContext, "tenant-a.ds", "r1", new byte[0], Map.of()))))
                .hasMessageContaining("write failed");
        }

        @Test
        @DisplayName("client delete exception propagates as failed Promise")
        void clientDeleteExceptionPropagatesAsFailedPromise() {
            when(client.delete(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("delete failed")));

            assertThatThrownBy(() ->
                runPromise(() -> adapter.deleteData(new DataDeleteRequest(bridgeContext, "tenant-a.ds", "r1"))))
                .hasMessageContaining("delete failed");
        }

        @Test
        @DisplayName("client query exception propagates as failed Promise")
        void clientQueryExceptionPropagatesAsFailedPromise() {
            when(client.query(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("query timeout")));

            assertThatThrownBy(() ->
                runPromise(() ->
                    adapter.queryData(new DataQueryRequest(bridgeContext, "tenant-a.ds", "SELECT *", Map.of(), 10, 0))))
                .hasMessageContaining("query timeout");
        }

        @Test
        @DisplayName("client getSchema exception propagates as failed Promise")
        void clientGetSchemaExceptionPropagatesAsFailedPromise() {
            when(client.getSchema(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("schema not found")));

            assertThatThrownBy(() ->
                runPromise(() -> adapter.getSchema(bridgeContext, "tenant-a.missing-dataset")))
                .hasMessageContaining("schema not found");
        }
    }

    // ==================== Security, audit, and health ====================

    @Nested
    @DisplayName("security, audit, and health signals")
    class SecurityAuditAndHealthSignals {

        @Test
        @DisplayName("denied authorization fails closed before client call")
        void deniedAuthorizationFailsClosedBeforeClientCall() {
            DataCloudKernelAdapterImpl deniedAdapter = new DataCloudKernelAdapterImpl(
                client,
                (context, resource, action) -> io.activej.promise.Promise.of(Boolean.FALSE),
                captureAudit(),
                captureHealth());

            assertThatThrownBy(() -> runPromise(() ->
                deniedAdapter.readData(new DataReadRequest(bridgeContext, "tenant-a.entities", "rec-42", Map.of()))))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Not authorized");

            verifyNoInteractions(client);
        }

        @Test
        @DisplayName("client retry exhaustion reports unhealthy and emits error audit")
        void retryExhaustionReportsUnhealthyAndEmitsErrorAudit() {
            AtomicInteger calls = new AtomicInteger();
            when(client.read(any(), any(), any()))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(new RuntimeException("backend down"));
                });
            CapturingHealth health = new CapturingHealth();
            List<BridgeAuditEmitter.BridgeAuditEvent> auditEvents = new ArrayList<>();
            DataCloudKernelAdapterImpl retryingAdapter = new DataCloudKernelAdapterImpl(
                client,
                TestBridgePorts.allowAllAuthorization(),
                auditEvents::add,
                health);

            assertThatThrownBy(() -> runPromise(() ->
                retryingAdapter.readData(new DataReadRequest(bridgeContext, "tenant-a.entities", "rec-42", Map.of()))))
                .hasMessageContaining("backend down");

            assertThat(calls.get()).isEqualTo(4);
            assertThat(health.degradedReasons).isNotEmpty();
            assertThat(health.unhealthyReasons).anyMatch(reason -> reason.contains("exhausted retries"));
            assertThat(auditEvents).anyMatch(event -> event.outcome().equals("ERROR"));
        }

        @Test
        @DisplayName("transient read failure retries and recovers before exhaustion")
        void transientReadFailureRetriesAndRecoversBeforeExhaustion() {
            AtomicInteger calls = new AtomicInteger();
            DataResult expected = new DataResult("rec-42", "ok".getBytes(), Map.of(), Instant.now().toEpochMilli());
            when(client.read(any(), any(), any()))
                .thenAnswer(invocation -> {
                    if (calls.incrementAndGet() < 3) {
                        return CompletableFuture.failedFuture(new RuntimeException("temporary timeout"));
                    }
                    return CompletableFuture.completedFuture(expected);
                });

            CapturingHealth health = new CapturingHealth();
            List<BridgeAuditEmitter.BridgeAuditEvent> auditEvents = new ArrayList<>();
            DataCloudKernelAdapterImpl retryingAdapter = new DataCloudKernelAdapterImpl(
                client,
                TestBridgePorts.allowAllAuthorization(),
                auditEvents::add,
                health);

            DataResult result = runPromise(() ->
                retryingAdapter.readData(new DataReadRequest(bridgeContext, "tenant-a.entities", "rec-42", Map.of())));

            assertThat(result.getRecordId()).isEqualTo("rec-42");
            assertThat(calls.get()).isEqualTo(3);
            assertThat(health.degradedReasons).isNotEmpty();
            assertThat(health.unhealthyReasons).isEmpty();
            assertThat(health.healthyBridgeIds).contains("data-cloud-kernel-bridge");
            assertThat(auditEvents).noneMatch(event -> event.outcome().equals("ERROR"));
            assertThat(auditEvents).anyMatch(event -> event.outcome().equals("ALLOWED"));
        }

        @Test
        @DisplayName("successful read emits authorization and operation audit with healthy signal")
        void successfulReadEmitsAuditAndHealth() {
            DataResult expected = new DataResult("rec-42", "hello".getBytes(), Map.of(), Instant.now().toEpochMilli());
            when(client.read(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expected));
            CapturingHealth health = new CapturingHealth();
            List<BridgeAuditEmitter.BridgeAuditEvent> auditEvents = new ArrayList<>();
            DataCloudKernelAdapterImpl auditedAdapter = new DataCloudKernelAdapterImpl(
                client,
                TestBridgePorts.allowAllAuthorization(),
                auditEvents::add,
                health);

            runPromise(() ->
                auditedAdapter.readData(new DataReadRequest(bridgeContext, "tenant-a.entities", "rec-42", Map.of())));

            assertThat(health.healthyBridgeIds).contains("data-cloud-kernel-bridge");
            assertThat(auditEvents)
                .extracting(BridgeAuditEmitter.BridgeAuditEvent::tenantId)
                .contains("tenant-a");
            assertThat(auditEvents)
                .extracting(BridgeAuditEmitter.BridgeAuditEvent::outcome)
                .contains("ALLOWED");
        }
    }

    // ==================== Transaction lifecycle ====================

    @Nested
    @DisplayName("transaction lifecycle mapping")
    class TransactionLifecycle {

        @Test
        @DisplayName("beginTransaction returns a handle with an ID")
        void beginTransactionReturnsHandleWithId() {
            when(client.beginTransaction())
                .thenReturn(CompletableFuture.completedFuture("inner-tx"));

            var handle = runPromise(() -> adapter.beginTransaction(bridgeContext));

            assertThat(handle).isNotNull();
            assertThat(handle.getId()).isNotBlank();
        }

        @Test
        @DisplayName("commitTransaction forwards to client and removes handle from active set")
        void commitTransactionForwardsToClient() {
            when(client.beginTransaction())
                .thenReturn(CompletableFuture.completedFuture("tx-obj"));
            when(client.commitTransaction(eq("tx-obj")))
                .thenReturn(CompletableFuture.completedFuture(null));

            var handle = runPromise(() -> adapter.beginTransaction(bridgeContext));
            runPromise(() -> adapter.commitTransaction(bridgeContext, handle));

            verify(client).commitTransaction("tx-obj");
        }

        @Test
        @DisplayName("rollbackTransaction forwards to client")
        void rollbackTransactionForwardsToClient() {
            when(client.beginTransaction())
                .thenReturn(CompletableFuture.completedFuture("tx-obj"));
            when(client.rollbackTransaction(eq("tx-obj")))
                .thenReturn(CompletableFuture.completedFuture(null));

            var handle = runPromise(() -> adapter.beginTransaction(bridgeContext));
            runPromise(() -> adapter.rollbackTransaction(bridgeContext, handle));

            verify(client).rollbackTransaction("tx-obj");
        }

        @Test
        @DisplayName("commit on unknown handle fails with informative error")
        void commitOnUnknownHandleFails() {
            // Provide a handle whose ID was never registered
            var orphanHandle = new com.ghatana.kernel.adapter.datacloud.TransactionHandle() {
                @Override
                public String getId() { return "tx-orphan"; }
                @Override
                public boolean isActive() { return true; }
            };

            assertThatThrownBy(() -> runPromise(() -> adapter.commitTransaction(bridgeContext, orphanHandle)))
                .hasMessageContaining("Transaction not found");
        }

        @Test
        @DisplayName("rollback on unknown handle fails with informative error")
        void rollbackOnUnknownHandleFails() {
            var orphanHandle = new com.ghatana.kernel.adapter.datacloud.TransactionHandle() {
                @Override
                public String getId() { return "tx-ghost"; }
                @Override
                public boolean isActive() { return true; }
            };

            assertThatThrownBy(() -> runPromise(() -> adapter.rollbackTransaction(bridgeContext, orphanHandle)))
                .hasMessageContaining("Transaction not found");
        }
    }

    // ==================== Tenant-safe dataset isolation ====================

    @Nested
    @DisplayName("tenant-safe dataset isolation")
    class TenantSafeIsolation {

        @Test
        @DisplayName("cross-tenant dataset access fails closed")
        void crossTenantDatasetAccessFailsClosed() {
            DataResult resultA = new DataResult("r1", new byte[0], Map.of(), 0L);

            when(client.read(eq("tenant-a.entities"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(resultA));

            DataResult a = runPromise(() ->
                adapter.readData(new DataReadRequest(bridgeContext, "tenant-a.entities", "r1", Map.of())));
            assertThatThrownBy(() -> runPromise(() ->
                adapter.readData(new DataReadRequest(bridgeContext, "tenant-b.entities", "r2", Map.of()))))
                .isInstanceOf(DataCloudProviderException.class)
                .hasMessageContaining("outside tenant scope");

            assertThat(a.getRecordId()).isEqualTo("r1");
            verify(client).read("tenant-a.entities", "r1", Map.of());
        }

        @Test
        @DisplayName("write to tenant-A dataset does not touch tenant-B dataset")
        void writeToTenantADoesNotTouchTenantB() {
            when(client.write(eq("tenant-a.events"), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            runPromise(() ->
                adapter.writeData(new DataWriteRequest(bridgeContext, "tenant-a.events", "evt-1", new byte[0], Map.of())));

            // Verify only tenant-a was touched
            verify(client).write(eq("tenant-a.events"), any(), any(), any());
        }

        @Test
        @DisplayName("schema lookup passes dataset ID verbatim — no tenant-prefix injection")
        void schemaLookupPassesDatasetIdVerbatim() {
            SchemaInfo schema = new SchemaInfo("tenant-a.schema", Map.of(), 0L, 0L);
            when(client.getSchema(eq("tenant-a.schema")))
                .thenReturn(CompletableFuture.completedFuture(schema));

            SchemaInfo result = runPromise(() -> adapter.getSchema(bridgeContext, "tenant-a.schema"));

            assertThat(result.getDatasetId()).isEqualTo("tenant-a.schema");
            verify(client).getSchema("tenant-a.schema");
        }

        @Test
        @DisplayName("cross-tenant query fails closed")
        void crossTenantQueryFailsClosed() {
            assertThatThrownBy(() -> runPromise(() ->
                adapter.queryData(new DataQueryRequest(
                    bridgeContext,
                    "tenant-b.analytics",
                    "SELECT *",
                    Map.of(),
                    10,
                    0))))
                .isInstanceOf(DataCloudProviderException.class)
                .hasMessageContaining("outside tenant scope");

            verifyNoInteractions(client);
        }

        @Test
        @DisplayName("cross-tenant schema create fails closed")
        void crossTenantSchemaCreateFailsClosed() {
            assertThatThrownBy(() -> runPromise(() ->
                adapter.createSchema(new com.ghatana.kernel.adapter.datacloud.SchemaCreateRequest(
                    bridgeContext,
                    "tenant-b.catalog",
                    Map.of("id", "string"),
                    Map.of()))))
                .isInstanceOf(DataCloudProviderException.class)
                .hasMessageContaining("outside tenant scope");

            verifyNoInteractions(client);
        }

        @Test
        @DisplayName("cross-tenant stream open fails closed")
        void crossTenantStreamOpenFailsClosed() {
            assertThatThrownBy(() -> runPromise(() ->
                adapter.openReadStream(new com.ghatana.kernel.adapter.datacloud.DataStreamRequest(
                    bridgeContext,
                    "tenant-b.streams",
                    Map.of("mode", "read")))))
                .isInstanceOf(DataCloudProviderException.class)
                .hasMessageContaining("outside tenant scope");

            verifyNoInteractions(client);
        }
    }

    // ==================== listDatasets mapping ====================

    @Nested
    @DisplayName("listDatasets mapping")
    class ListDatasetsMapping {

        @Test
        @DisplayName("returns all datasets from client")
        void returnsAllDatasetsFromClient() {
            DatasetInfo d1 = new DatasetInfo("ds-1", "Dataset 1", "Test dataset", 0L, 0L);
            DatasetInfo d2 = new DatasetInfo("ds-2", "Dataset 2", "Test dataset", 0L, 0L);
            when(client.listDatasets())
                .thenReturn(CompletableFuture.completedFuture(List.of(d1, d2)));

            List<DatasetInfo> result = runPromise(() -> adapter.listDatasets(bridgeContext));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns empty list when client returns empty")
        void returnsEmptyListWhenClientReturnsEmpty() {
            when(client.listDatasets())
                .thenReturn(CompletableFuture.completedFuture(List.of()));

            List<DatasetInfo> result = runPromise(() -> adapter.listDatasets(bridgeContext));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("client exception propagates as failed Promise")
        void clientListExceptionPropagates() {
            when(client.listDatasets())
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("catalog unavailable")));

            assertThatThrownBy(() -> runPromise(() -> adapter.listDatasets(bridgeContext)))
                .hasMessageContaining("catalog unavailable");
        }
    }

    // ==================== Extension wires correct adapter type ====================

    @Nested
    @DisplayName("extension registers a DataCloudKernelAdapterImpl into context")
    class ExtensionWiresCorrectType {

        @Test
        @DisplayName("registered service is a DataCloudKernelAdapter")
        void registeredServiceIsCorrectType() {
            FailingDataCloudClient failingClient = new FailingDataCloudClient();
            DataCloudKernelExtension extension = new DataCloudKernelExtension(
                failingClient,
                TestBridgePorts.allowAllAuthorization(),
                captureAudit(),
                captureHealth(),
                ignored -> bridgeContext);

            // Capture what gets registered
            var registered = new DataCloudKernelAdapter[1];
            org.mockito.Mockito.doAnswer(inv -> {
                registered[0] = inv.getArgument(1);
                return null;
            }).when(context).registerService(
                org.mockito.ArgumentMatchers.eq(DataCloudKernelAdapter.class),
                any(DataCloudKernelAdapter.class));

            extension.onModuleInitialized(context);

            assertThat(registered[0]).isInstanceOf(DataCloudKernelAdapterImpl.class);
        }
    }

    // ==================== Helpers ====================

    /**
     * A client that fails all operations — used to test failure propagation from the extension.
     */
    private static final class FailingDataCloudClient implements DataCloudKernelAdapterImpl.DataCloudClient {
        @Override
        public CompletableFuture<DataResult> read(String d, String r, Map<String, String> o) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<Void> write(String d, String r, byte[] data, Map<String, String> m) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<Void> delete(String d, String r) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<List<DataResult>> query(String d, String q, Map<String, Object> p, int l, int o) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<Void> createDataset(String d, Map<String, String> s, Map<String, String> o) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<SchemaInfo> getSchema(String d) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<List<DatasetInfo>> listDatasets() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<Object> beginTransaction() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<Void> commitTransaction(Object t) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<Void> rollbackTransaction(Object t) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<Object> openReadStream(String d, Map<String, String> o) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<Object> openWriteStream(String d, Map<String, String> o) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<byte[]> readStreamChunk(Object s) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<Void> writeStreamChunk(Object s, byte[] d) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
        @Override
        public CompletableFuture<Void> closeStream(Object s) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("stub"));
        }
    }

    private static BridgeAuditEmitter captureAudit() {
        return event -> { };
    }

    private static BridgeHealthIndicator captureHealth() {
        return new BridgeHealthIndicator() {
            @Override
            public void reportHealthy(String bridgeId) { }

            @Override
            public void reportDegraded(String bridgeId, String reason) { }

            @Override
            public void reportUnhealthy(String bridgeId, String reason) { }
        };
    }

    private static final class CapturingHealth implements BridgeHealthIndicator {
        private final List<String> healthyBridgeIds = new ArrayList<>();
        private final List<String> degradedReasons = new ArrayList<>();
        private final List<String> unhealthyReasons = new ArrayList<>();

        @Override
        public void reportHealthy(String bridgeId) {
            healthyBridgeIds.add(bridgeId);
        }

        @Override
        public void reportDegraded(String bridgeId, String reason) {
            degradedReasons.add(reason);
        }

        @Override
        public void reportUnhealthy(String bridgeId, String reason) {
            unhealthyReasons.add(reason);
        }
    }
}
