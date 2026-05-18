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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for Data Cloud-backed kernel provider implementations.
 *
 * @doc.type class
 * @doc.purpose Verify provider persistence uses Data Cloud and surfaces typed failures
 * @doc.layer adapter
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloud platform providers")
class DataCloudProviderTest extends EventloopTestBase {

    @Mock
    private DataCloudKernelAdapter adapter;

    private final BridgeContext context = BridgeContext.builder()
        .tenantId("tenant-provider")
        .principalId("provider-test")
        .correlationId("corr-provider")
        .build();

    @Test
    @DisplayName("event provider persists tenant-scoped lifecycle event records")
    void eventProviderPersistsTenantScopedRecords() {
        when(adapter.writeData(org.mockito.ArgumentMatchers.any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());
        DataCloudEventProvider provider = new DataCloudEventProvider(adapter, context);

        runPromise(() -> provider.appendEvent("event-1", Map.of("eventType", "kernel.lifecycle.started")));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter).writeData(captor.capture());
        DataWriteRequest request = captor.getValue();
        assertThat(request.getContext()).isSameAs(context);
        assertThat(request.getDatasetId()).isEqualTo("kernel.events.tenant-provider");
        assertThat(request.getRecordId()).isEqualTo("event-1");
        assertThat(request.getMetadata()).containsEntry("provider", "events");
        assertThat(new String(request.getData(), StandardCharsets.UTF_8))
            .contains("kernel.lifecycle.started");
    }

    @Test
    @DisplayName("provider failures surface without falling back to bootstrap storage")
    void providerFailuresSurfaceWithoutFallback() {
        when(adapter.writeData(org.mockito.ArgumentMatchers.any(DataWriteRequest.class)))
            .thenReturn(Promise.ofException(new DataCloudProviderException("events", "write", "boom")));
        DataCloudEventProvider provider = new DataCloudEventProvider(adapter, context);

        assertThatThrownBy(() -> runPromise(() ->
            provider.appendEvent("event-2", Map.of("eventType", "kernel.lifecycle.failed"))))
            .isInstanceOf(DataCloudProviderException.class)
            .hasMessageContaining("boom");
    }

    @Test
    @DisplayName("Data Cloud provider rejects missing tenant context")
    void providerRejectsMissingTenantContext() {
        BridgeContext missingTenantContext = BridgeContext.builder()
            .tenantId("")
            .principalId("provider-test")
            .correlationId("corr-missing-tenant")
            .build();

        assertThatThrownBy(() -> new DataCloudEventProvider(adapter, missingTenantContext))
            .isInstanceOf(DataCloudProviderException.class)
            .satisfies(error -> assertThat(((DataCloudProviderException) error).reasonCode())
                .isEqualTo(DataCloudProviderException.ReasonCode.TENANT_ISOLATION));
    }

    @Test
    @DisplayName("provider health result exposes degraded and unavailable states")
    void providerHealthResultExposesDegradedAndUnavailableStates() {
        DataCloudEventProvider provider = new DataCloudEventProvider(adapter, context);

        KernelBridgeProviderHealthResult degraded = provider.providerHealth(
            KernelBridgeProviderMode.PLATFORM,
            KernelBridgeProviderStatus.DEGRADED,
            "latency threshold exceeded",
            1200,
            "2026-01-01T00:00:00.000Z",
            null,
            java.util.List.of("datacloud://health/events"));
        KernelBridgeProviderHealthResult unavailable = provider.providerHealth(
            KernelBridgeProviderMode.PLATFORM,
            KernelBridgeProviderStatus.UNAVAILABLE,
            "provider disabled",
            0,
            null,
            "2026-01-01T00:01:00.000Z",
            java.util.List.of("datacloud://health/events"));

        assertThat(degraded.providerId()).isEqualTo("events");
        assertThat(degraded.status()).isEqualTo(KernelBridgeProviderStatus.DEGRADED);
        assertThat(degraded.evidenceRefs()).containsExactly("datacloud://health/events");
        assertThat(unavailable.status()).isEqualTo(KernelBridgeProviderStatus.UNAVAILABLE);
        assertThat(unavailable.lastFailureAt()).isEqualTo("2026-01-01T00:01:00.000Z");
    }

    @Test
    @DisplayName("all provider types persist through the Data Cloud adapter")
    void allProviderTypesPersistThroughAdapter() {
        when(adapter.writeData(org.mockito.ArgumentMatchers.any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());

        runPromise(() -> new DataCloudArtifactProvider(adapter, context)
            .persistArtifactManifest("artifact-1", Map.of("artifactType", "static-web-bundle")));
        runPromise(() -> new DataCloudHealthProvider(adapter, context)
            .persistHealthSnapshot("health-1", "healthy", Map.of("component", "bridge")));
        runPromise(() -> new DataCloudProvenanceProvider(adapter, context)
            .persistProvenance("prov-1", Map.of("source", "kernel")));
        runPromise(() -> new DataCloudMemoryProvider(adapter, context)
            .remember("memory-1", Map.of("kind", "decision")));
        runPromise(() -> new DataCloudKnowledgeProvider(adapter, context)
            .persistKnowledge("knowledge-1", Map.of("topic", "artifact")));
        runPromise(() -> new DataCloudRuntimeTruthProvider(adapter, context)
            .persistRuntimeTruth("runtime-1", Map.of("mode", "platform")));
        runPromise(() -> new DataCloudPolicyEvidenceProvider(adapter, context)
            .persistPolicyEvidence("policy-1", Map.of("decision", "allow")));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter, org.mockito.Mockito.times(7)).writeData(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(DataWriteRequest::getDatasetId)
            .containsExactly(
                "kernel.artifacts.tenant-provider",
                "kernel.health.tenant-provider",
                "kernel.provenance.tenant-provider",
                "kernel.memory.tenant-provider",
                "kernel.knowledge.tenant-provider",
                "kernel.runtime-truth.tenant-provider",
                "kernel.policy-evidence.tenant-provider"
            );
    }
}
