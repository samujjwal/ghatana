package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for Data Cloud-backed runtime truth provider with tenant-scoped persistence and query.
 *
 * @doc.type class
 * @doc.purpose Verify runtime truth persists tenant-scoped data and supports durable queries
 * @doc.layer adapter
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudRuntimeTruthProvider")
class DataCloudRuntimeTruthProviderTest extends EventloopTestBase {

    @Mock
    private DataCloudKernelAdapter adapter;

    private final BridgeContext context = BridgeContext.builder()
        .tenantId("tenant-runtime-truth")
        .principalId("runtime-truth-test")
        .correlationId("corr-runtime-truth")
        .workspaceId("workspace-1")
        .projectId("data-cloud")
        .build();

    @Test
    @DisplayName("persistRuntimeTruth uses tenant-scoped dataset ID")
    void persistRuntimeTruthUsesTenantScopedDatasetId() {
        when(adapter.writeData(any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());
        DataCloudRuntimeTruthProvider provider = new DataCloudRuntimeTruthProvider(adapter, context);

        runPromise(() -> provider.persistRuntimeTruth("snapshot-1", Map.of("mode", "platform", "status", "healthy")));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter).writeData(captor.capture());
        DataWriteRequest request = captor.getValue();
        assertThat(request.getContext()).isSameAs(context);
        assertThat(request.getDatasetId()).isEqualTo("kernel.runtime-truth.tenant-runtime-truth");
        assertThat(request.getRecordId()).isEqualTo("snapshot-1");
        assertThat(request.getMetadata()).containsEntry("provider", "runtime-truth");
    }

    @Test
    @DisplayName("persistRuntimeTruthTyped includes tenant context in snapshot data")
    void persistRuntimeTruthTypedIncludesTenantContext() {
        when(adapter.writeData(any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());
        DataCloudRuntimeTruthProvider provider = new DataCloudRuntimeTruthProvider(adapter, context);
        Instant capturedAt = Instant.parse("2026-05-14T00:00:00.000Z");

        runPromise(() -> provider.persistRuntimeTruthTyped(
            new DataCloudRuntimeTruthProvider.RuntimeTruthPersistRequest(
                "snapshot-2",
                Map.of("mode", "platform", "status", "healthy"),
                capturedAt,
                "corr-runtime-truth"
            )));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter).writeData(captor.capture());
        DataWriteRequest request = captor.getValue();
        String data = new String(request.getData(), StandardCharsets.UTF_8);
        
        assertThat(data).contains("\"tenantId\":\"tenant-runtime-truth\"");
        assertThat(data).contains("\"workspaceId\":\"workspace-1\"");
        assertThat(data).contains("\"projectId\":\"data-cloud\"");
        assertThat(data).contains("\"snapshotId\":\"snapshot-2\"");
        assertThat(data).contains("\"capturedAt\":\"2026-05-14T00:00:00.000Z\"");
        assertThat(data).contains("\"correlationId\":\"corr-runtime-truth\"");
        assertThat(data).contains("\"persistedAt\"");
    }

    @Test
    @DisplayName("persistRuntimeTruthTyped returns typed response with success status")
    void persistRuntimeTruthTypedReturnsTypedResponse() {
        when(adapter.writeData(any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());
        DataCloudRuntimeTruthProvider provider = new DataCloudRuntimeTruthProvider(adapter, context);

        DataCloudRuntimeTruthProvider.RuntimeTruthPersistResponse response = runPromise(() ->
            provider.persistRuntimeTruthTyped(
                new DataCloudRuntimeTruthProvider.RuntimeTruthPersistRequest(
                    "snapshot-3",
                    Map.of("mode", "platform"),
                    Instant.now(),
                    "corr-typed"
                )));

        assertThat(response.success()).isTrue();
        assertThat(response.snapshotId()).isEqualTo("snapshot-3");
        assertThat(response.persistedAt()).isNotNull();
    }

    @Test
    @DisplayName("multiple tenants have isolated runtime truth datasets")
    void multipleTenantsHaveIsolatedDatasets() {
        when(adapter.writeData(any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());
        
        BridgeContext tenant1Context = BridgeContext.builder()
            .tenantId("tenant-1")
            .principalId("test")
            .correlationId("corr-1")
            .build();
        
        BridgeContext tenant2Context = BridgeContext.builder()
            .tenantId("tenant-2")
            .principalId("test")
            .correlationId("corr-2")
            .build();

        DataCloudRuntimeTruthProvider provider1 = new DataCloudRuntimeTruthProvider(adapter, tenant1Context);
        DataCloudRuntimeTruthProvider provider2 = new DataCloudRuntimeTruthProvider(adapter, tenant2Context);

        runPromise(() -> provider1.persistRuntimeTruth("snap-1", Map.of("tenant", "tenant-1")));
        runPromise(() -> provider2.persistRuntimeTruth("snap-2", Map.of("tenant", "tenant-2")));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter, org.mockito.Mockito.times(2)).writeData(captor.capture());
        
        assertThat(captor.getAllValues())
            .extracting(DataWriteRequest::getDatasetId)
            .containsExactly("kernel.runtime-truth.tenant-1", "kernel.runtime-truth.tenant-2");
    }

    @Test
    @DisplayName("runtime truth snapshots include correlation ID for traceability")
    void snapshotsIncludeCorrelationIdForTraceability() {
        when(adapter.writeData(any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());
        DataCloudRuntimeTruthProvider provider = new DataCloudRuntimeTruthProvider(adapter, context);

        runPromise(() -> provider.persistRuntimeTruthTyped(
            new DataCloudRuntimeTruthProvider.RuntimeTruthPersistRequest(
                "snapshot-trace",
                Map.of("traceKey", "traceValue"),
                Instant.now(),
                "corr-trace-test"
            )));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter).writeData(captor.capture());
        String data = new String(captor.getValue().getData(), StandardCharsets.UTF_8);
        
        assertThat(data).contains("\"correlationId\":\"corr-trace-test\"");
        assertThat(context.getCorrelationId()).isEqualTo("corr-runtime-truth");
    }
}
