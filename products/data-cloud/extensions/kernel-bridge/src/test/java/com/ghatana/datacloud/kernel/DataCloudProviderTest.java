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
    @DisplayName("event provider accepts opening pilot lifecycle events")
    void eventProviderAcceptsOpeningPilotLifecycleEvents() {
        when(adapter.writeData(org.mockito.ArgumentMatchers.any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());
        DataCloudEventProvider provider = new DataCloudEventProvider(adapter, context);

        runPromise(() -> provider.appendKernelLifecycleEvent(
            "evt-dm-1",
            "1.0.0",
            "lifecycle.gate.evaluated",
            "digital-marketing",
            "run-1",
            "validate",
            "kernel-lifecycle",
            "corr-dm-1",
            Map.of(
                "gateId", "bridge-compliance",
                "evidenceRefs", java.util.List.of("dry-run:gate:bridge-compliance")
            )
        ));
        runPromise(() -> provider.appendKernelLifecycleEvent(
            "evt-phr-1",
            "1.0.0",
            "lifecycle.gate.evaluated",
            "phr",
            "run-2",
            "validate",
            "kernel-lifecycle",
            "corr-phr-1",
            Map.of(
                "gateId", "consent",
                "evidenceRefs", java.util.List.of("dry-run:gate:consent")
            )
        ));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter, org.mockito.Mockito.times(2)).writeData(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(DataWriteRequest::getRecordId)
            .containsExactly("evt-dm-1", "evt-phr-1");
        assertThat(new String(captor.getAllValues().get(1).getData(), StandardCharsets.UTF_8))
            .contains("\"productUnitId\":\"phr\"")
            .contains("\"correlationId\":\"corr-phr-1\"");
    }

    @Test
    @DisplayName("event provider rejects lifecycle event without productUnitId")
    void eventProviderRejectsLifecycleEventWithoutProductUnitId() {
        DataCloudEventProvider provider = new DataCloudEventProvider(adapter, context);

        assertThatThrownBy(() -> runPromise(() -> provider.appendKernelLifecycleEvent(
            "evt-missing-product",
            "1.0.0",
            "lifecycle.plan.created",
            "",
            "run-1",
            "build",
            "kernel-lifecycle",
            "corr-1",
            Map.of("planRunId", "run-1")
        )))
            .isInstanceOf(DataCloudProviderException.class)
            .hasMessageContaining("productUnitId is required");
    }

    @Test
    @DisplayName("event provider rejects lifecycle event without correlationId")
    void eventProviderRejectsLifecycleEventWithoutCorrelationId() {
        DataCloudEventProvider provider = new DataCloudEventProvider(adapter, context);

        assertThatThrownBy(() -> runPromise(() -> provider.appendKernelLifecycleEvent(
            "evt-missing-correlation",
            "1.0.0",
            "lifecycle.plan.created",
            "digital-marketing",
            "run-1",
            "build",
            "kernel-lifecycle",
            "",
            Map.of("planRunId", "run-1")
        )))
            .isInstanceOf(DataCloudProviderException.class)
            .hasMessageContaining("correlationId is required");
    }

    @Test
    @DisplayName("event provider rejects PHR healthcare gate event without evidence refs")
    void eventProviderRejectsPhrHealthcareGateWithoutEvidenceRefs() {
        DataCloudEventProvider provider = new DataCloudEventProvider(adapter, context);

        assertThatThrownBy(() -> runPromise(() -> provider.appendKernelLifecycleEvent(
            "evt-phr-missing-evidence",
            "1.0.0",
            "lifecycle.gate.evaluated",
            "phr",
            "run-1",
            "validate",
            "kernel-lifecycle",
            "corr-phr-missing-evidence",
            Map.of("gateId", "consent", "evidenceRefs", java.util.List.of())
        )))
            .isInstanceOf(DataCloudProviderException.class)
            .hasMessageContaining("PHR healthcare gate events require evidenceRefs");
    }

    @Test
    @DisplayName("event provider preserves lifecycle correlation ID")
    void eventProviderPreservesCorrelationId() {
        when(adapter.writeData(org.mockito.ArgumentMatchers.any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());
        DataCloudEventProvider provider = new DataCloudEventProvider(adapter, context);

        runPromise(() -> provider.appendKernelLifecycleEvent(
            "evt-correlation",
            "1.0.0",
            "lifecycle.run.completed",
            "digital-marketing",
            "run-correlation",
            "verify",
            "kernel-lifecycle",
            "corr-preserved",
            Map.of("status", "healthy")
        ));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter).writeData(captor.capture());
        assertThat(new String(captor.getValue().getData(), StandardCharsets.UTF_8))
            .contains("\"correlationId\":\"corr-preserved\"");
    }

    @Test
    @DisplayName("artifact provider validates typed artifact references")
    void artifactProviderValidatesTypedArtifactReferences() {
        when(adapter.writeData(org.mockito.ArgumentMatchers.any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());
        DataCloudArtifactProvider provider = new DataCloudArtifactProvider(adapter, context);

        runPromise(() -> provider.persistArtifactManifestTyped(
            new DataCloudArtifactProvider.ArtifactManifestPersistRequest(
                "artifact-phr-web",
                "phr",
                "web",
                "build",
                "git:f9e7f49",
                "sha256:abc123",
                Map.of("manifestRef", "artifact-manifest://phr/build"),
                Instant.parse("2026-05-18T00:00:00.000Z"),
                "corr-artifact"
            )
        ));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter).writeData(captor.capture());
        assertThat(captor.getValue().getRecordId()).isEqualTo("artifact-phr-web");
        assertThat(new String(captor.getValue().getData(), StandardCharsets.UTF_8))
            .contains("\"productUnitId\":\"phr\"")
            .contains("\"digest\":\"sha256:abc123\"")
            .contains("\"correlationId\":\"corr-artifact\"");
    }

    @Test
    @DisplayName("artifact provider rejects typed artifact reference without digest")
    void artifactProviderRejectsTypedArtifactReferenceWithoutDigest() {
        DataCloudArtifactProvider provider = new DataCloudArtifactProvider(adapter, context);

        assertThatThrownBy(() -> runPromise(() -> provider.persistArtifactManifestTyped(
            new DataCloudArtifactProvider.ArtifactManifestPersistRequest(
                "artifact-phr-web",
                "phr",
                "web",
                "build",
                "git:f9e7f49",
                "",
                Map.of("manifestRef", "artifact-manifest://phr/build"),
                Instant.parse("2026-05-18T00:00:00.000Z"),
                "corr-artifact"
            )
        )))
            .isInstanceOf(DataCloudProviderException.class)
            .hasMessageContaining("digest is required");
    }

    @Test
    @DisplayName("health provider validates typed health snapshots")
    void healthProviderValidatesTypedHealthSnapshots() {
        when(adapter.writeData(org.mockito.ArgumentMatchers.any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());
        DataCloudHealthProvider provider = new DataCloudHealthProvider(adapter, context);

        runPromise(() -> provider.persistHealthSnapshotTyped(
            new DataCloudHealthProvider.HealthSnapshotPersistRequest(
                "health-phr-verify",
                "healthy",
                Map.of("healthCheckRef", "health://phr/verify"),
                Instant.parse("2026-05-18T00:00:00.000Z"),
                "corr-health"
            )
        ));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter).writeData(captor.capture());
        assertThat(captor.getValue().getRecordId()).isEqualTo("health-phr-verify");
        assertThat(new String(captor.getValue().getData(), StandardCharsets.UTF_8))
            .contains("\"status\":\"healthy\"")
            .contains("\"correlationId\":\"corr-health\"");
    }

    @Test
    @DisplayName("health provider rejects typed health snapshot without correlation ID")
    void healthProviderRejectsTypedHealthSnapshotWithoutCorrelationId() {
        DataCloudHealthProvider provider = new DataCloudHealthProvider(adapter, context);

        assertThatThrownBy(() -> runPromise(() -> provider.persistHealthSnapshotTyped(
            new DataCloudHealthProvider.HealthSnapshotPersistRequest(
                "health-phr-verify",
                "healthy",
                Map.of("healthCheckRef", "health://phr/verify"),
                Instant.parse("2026-05-18T00:00:00.000Z"),
                ""
            )
        )))
            .isInstanceOf(DataCloudProviderException.class)
            .hasMessageContaining("correlationId is required");
    }

    @Test
    @DisplayName("interaction evidence provider persists typed evidence with tenant scope")
    void interactionEvidenceProviderPersistsTypedEvidenceWithTenantScope() {
        when(adapter.writeData(org.mockito.ArgumentMatchers.any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());
        DataCloudProductInteractionEvidenceProvider provider =
            new DataCloudProductInteractionEvidenceProvider(adapter, context);

        DataCloudProductInteractionEvidenceProvider.InteractionEvidencePersistResponse response = runPromise(() ->
            provider.persistInteractionEvidenceTyped(
                new DataCloudProductInteractionEvidenceProvider.InteractionEvidencePersistRequest(
                    "interaction-evidence-1",
                    "kernel://interactions/phr.consent-status.v1",
                    "phr",
                    "digital-marketing",
                    Map.of("status", "allowed", "evidenceRefs", java.util.List.of("datacloud://consent/1")),
                    Instant.parse("2026-05-21T00:00:00.000Z"),
                    "corr-interaction-evidence"
                )));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter).writeData(captor.capture());
        DataWriteRequest request = captor.getValue();
        String data = new String(request.getData(), StandardCharsets.UTF_8);
        assertThat(response.success()).isTrue();
        assertThat(response.evidenceId()).isEqualTo("interaction-evidence-1");
        assertThat(request.getDatasetId()).isEqualTo("kernel.interaction-evidence.tenant-provider");
        assertThat(request.getRecordId()).isEqualTo("interaction-evidence-1");
        assertThat(request.getMetadata()).containsEntry("provider", "interaction-evidence");
        assertThat(data)
            .contains("\"contractId\":\"kernel://interactions/phr.consent-status.v1\"")
            .contains("\"providerProductId\":\"phr\"")
            .contains("\"consumerProductId\":\"digital-marketing\"")
            .contains("\"tenantId\":\"tenant-provider\"")
            .contains("\"correlationId\":\"corr-interaction-evidence\"")
            .contains("\"capturedAt\":\"2026-05-21T00:00:00Z\"");
    }

    @Test
    @DisplayName("interaction evidence provider rejects typed evidence without correlation ID")
    void interactionEvidenceProviderRejectsTypedEvidenceWithoutCorrelationId() {
        DataCloudProductInteractionEvidenceProvider provider =
            new DataCloudProductInteractionEvidenceProvider(adapter, context);

        assertThatThrownBy(() -> runPromise(() -> provider.persistInteractionEvidenceTyped(
            new DataCloudProductInteractionEvidenceProvider.InteractionEvidencePersistRequest(
                "interaction-evidence-1",
                "kernel://interactions/phr.consent-status.v1",
                "phr",
                "digital-marketing",
                Map.of("status", "allowed"),
                Instant.parse("2026-05-21T00:00:00.000Z"),
                ""
            ))))
            .isInstanceOf(DataCloudProviderException.class)
            .hasMessageContaining("correlationId is required");
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
        runPromise(() -> new DataCloudProductInteractionEvidenceProvider(adapter, context)
            .persistInteractionEvidence("interaction-1", Map.of("decision", "allow")));

        ArgumentCaptor<DataWriteRequest> captor = ArgumentCaptor.forClass(DataWriteRequest.class);
        verify(adapter, org.mockito.Mockito.times(8)).writeData(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(DataWriteRequest::getDatasetId)
            .containsExactly(
                "kernel.artifacts.tenant-provider",
                "kernel.health.tenant-provider",
                "kernel.provenance.tenant-provider",
                "kernel.memory.tenant-provider",
                "kernel.knowledge.tenant-provider",
                "kernel.runtime-truth.tenant-provider",
                "kernel.policy-evidence.tenant-provider",
                "kernel.interaction-evidence.tenant-provider"
            );
    }
}
