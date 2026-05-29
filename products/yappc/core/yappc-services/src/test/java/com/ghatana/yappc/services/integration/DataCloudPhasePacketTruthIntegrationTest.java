package com.ghatana.yappc.services.integration;

import com.ghatana.core.runtime.PreviewRuntimeService;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.AdminFeatureFlagController;
import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.kernelvisibility.KernelHealthSnapshotService;
import com.ghatana.yappc.services.capability.CapabilityEvaluationService;
import com.ghatana.yappc.services.lifecycle.StageConfigLoader;
import com.ghatana.yappc.services.lifecycle.TransitionConfigLoader;
import com.ghatana.yappc.services.lifecycle.gate.PhaseGateValidator;
import com.ghatana.yappc.services.phase.DataCloudPlatformRunStatusService;
import com.ghatana.yappc.services.phase.DataCloudPlatformRunStatusWriter;
import com.ghatana.yappc.services.phase.DegradedPhasePacketFactory;
import com.ghatana.yappc.services.phase.PhaseActionAuthorizationService;
import com.ghatana.yappc.services.phase.PhasePacketServiceImpl;
import com.ghatana.yappc.services.phase.PhaseRequiredArtifactProvider;
import com.ghatana.yappc.services.phase.PhaseFeatureFlagProvider;
import com.ghatana.yappc.services.phase.PhaseProjectStateService;
import com.ghatana.yappc.services.phase.PhaseEvidenceService;
import com.ghatana.yappc.services.phase.PhaseGovernanceService;
import com.ghatana.yappc.services.phase.PhaseActivityFeedService;
import com.ghatana.yappc.services.phase.PhaseBlockerMapper;
import com.ghatana.yappc.services.phase.PhaseGateContextFactory;
import com.ghatana.yappc.services.phase.PhaseReadinessEvaluator;
import com.ghatana.yappc.services.phase.PhaseHealthSignalProvider;
import com.ghatana.yappc.services.phase.PhasePacketAssembler;
import com.ghatana.yappc.services.platform.PlatformIntegrationClient;
import com.ghatana.yappc.services.platform.PlatformPolicy;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Backend integration coverage for YAPPC's Data Cloud-backed runtime truth path.
 *
 * @doc.type test
 * @doc.purpose Verifies phase packets, Kernel truth, and platform run status share Data Cloud-backed fixtures
 * @doc.layer integration
 * @doc.pattern IntegrationTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Data Cloud phase packet truth integration")
class DataCloudPhasePacketTruthIntegrationTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-integration";
    private static final String WORKSPACE_ID = "workspace-integration";
    private static final String PROJECT_ID = "project-integration";
    private static final String PRODUCT_UNIT_ID = "product-unit-integration";

    @Mock private YappcArtifactRepository artifactRepository;
    @Mock private PhaseGateValidator phaseGateValidator;
    @Mock private PolicyEngine policyEngine;
    @Mock private CapabilityEvaluationService capabilityEvaluationService;
    @Mock private TransitionConfigLoader transitionConfigLoader;
    @Mock private PlatformIntegrationClient platformIntegrationClient;
    @Mock private AuditService auditService;
    @Mock private PreviewRuntimeService previewRuntimeService;
    @Mock private StageConfigLoader stageConfigLoader;

    private InMemoryDataCloudClient dataCloudClient;

    @BeforeEach
    void setUp() {
        dataCloudClient = new InMemoryDataCloudClient();

        runPromise(() -> dataCloudClient.save(TENANT_ID, "projects", Map.of(
                "id", PROJECT_ID,
                "name", "Integration Project",
                "workspaceId", WORKSPACE_ID,
                "workspaceName", "Integration Workspace",
                "tier", "PRO",
                "status", "active")));
        runPromise(() -> dataCloudClient.save(TENANT_ID, AdminFeatureFlagController.FLAG_COLLECTION, Map.of(
                "id", "flag-run",
                "key", "RUN",
                "enabled", true,
                "updatedAt", "2026-05-26T18:45:00Z")));
        runPromise(() -> dataCloudClient.save(TENANT_ID, "kernel_lifecycle_truth", Map.of(
                "id", PRODUCT_UNIT_ID,
                "productUnitId", PRODUCT_UNIT_ID,
                "status", "found",
                "healthSnapshot", Map.of("status", "healthy"),
                "lifecycleResult", Map.of(
                        "status", "succeeded",
                        "currentPhase", "observe",
                        "timestamp", "2026-05-26T18:40:00Z"),
                "deployment", Map.of("status", "ready"))));

        lenient().when(phaseGateValidator.validate(anyString(), any(), any(PhaseGateValidator.PhaseGateContext.class)))
                .thenReturn(Promise.of(new PhaseGateValidator.ValidationResult(
                        com.ghatana.yappc.domain.PhaseType.RUN,
                        true,
                        List.of())));
        lenient().when(capabilityEvaluationService.evaluate(any()))
                .thenReturn(Promise.of(CapabilityEvaluationService.CapabilityModel.allGranted()));
        lenient().when(platformIntegrationClient.searchEvidence(any())).thenReturn(List.of());
        lenient().when(platformIntegrationClient.evaluatePolicy(any()))
                .thenReturn(new PlatformPolicy("policy-integration", true, List.of(), Map.of(), Instant.parse("2026-05-26T18:40:00Z")));
        lenient().when(artifactRepository.listVersions(anyString(), any())).thenReturn(Promise.of(List.of()));
        lenient().when(artifactRepository.listCompletedArtifactMetadata(anyString(), any())).thenReturn(Promise.of(List.of()));
        lenient().when(auditService.queryByPhase(anyString(), anyString(), any(), any())).thenReturn(Promise.of(List.of()));
        lenient().when(stageConfigLoader.findById(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("phase packet reads Data Cloud platform run status written from execution event")
    void phasePacketReadsDataCloudPlatformRunStatusWrittenFromExecutionEvent() {
        DataCloudPlatformRunStatusWriter writer = new DataCloudPlatformRunStatusWriter(dataCloudClient);
        runPromise(() -> writer.ingestEvent(TENANT_ID, DataCloudClient.Event.builder()
                .type("kernel.execution.succeeded")
                .source("kernel")
                .subjectType("platform-run")
                .subjectId("run-integration-1")
                .correlationId("corr-integration-1")
                .traceContext("trace-integration-1")
                .timestamp(Instant.parse("2026-05-26T18:41:00Z"))
                .payload(Map.of(
                        "workspaceId", WORKSPACE_ID,
                        "projectId", PROJECT_ID,
                        "phase", "RUN",
                        "runId", "run-integration-1",
                        "status", "SUCCEEDED",
                        "platform", "kernel",
                        "startedAt", "2026-05-26T18:40:00Z",
                        "completedAt", "2026-05-26T18:41:00Z",
                        "evidenceIds", List.of("evidence-run-integration-1")))
                .build()));

        PhasePacketServiceImpl phasePacketService = new PhasePacketServiceImpl(
                dataCloudClient,
                artifactRepository,
                phaseGateValidator,
                policyEngine,
                capabilityEvaluationService,
                transitionConfigLoader,
                platformIntegrationClient,
                null,
                auditService,
                previewRuntimeService,
                new DataCloudPlatformRunStatusService(dataCloudClient),
                new PhaseActionAuthorizationService(),
                new PhaseRequiredArtifactProvider(stageConfigLoader),
                new DegradedPhasePacketFactory(),
                new PhaseFeatureFlagProvider(dataCloudClient),
                new PhaseProjectStateService(dataCloudClient, new PhaseFeatureFlagProvider(dataCloudClient)),
                new PhaseEvidenceService(platformIntegrationClient),
                new PhaseGovernanceService(platformIntegrationClient),
                new PhaseActivityFeedService(auditService),
                new PhaseBlockerMapper(),
                new PhaseGateContextFactory(),
                new PhaseReadinessEvaluator(transitionConfigLoader),
                new PhaseHealthSignalProvider(previewRuntimeService),
                new PhasePacketAssembler());

        PhasePacket packet = runPromise(() -> phasePacketService.buildPhasePacket(
                "RUN",
                PROJECT_ID,
                WORKSPACE_ID,
                new Principal("integration-user", List.of("OWNER"), TENANT_ID),
                "corr-integration-1"));

        assertThat(packet.platformRunStatus()).isNotNull();
        assertThat(packet.platformRunStatus().runId()).isEqualTo("run-integration-1");
        assertThat(packet.platformRunStatus().status()).isEqualTo("SUCCEEDED");
        assertThat(packet.platformRunStatus().traceId()).isEqualTo("trace-integration-1");
        assertThat(packet.platformRunStatus().evidenceIds()).containsExactly("evidence-run-integration-1");
        assertThat(packet.correlationId()).isEqualTo("corr-integration-1");
    }

    @Test
    @DisplayName("Kernel health reads typed lifecycle truth from same Data Cloud fixture")
    void kernelHealthReadsTypedLifecycleTruthFromSameDataCloudFixture() {
        KernelHealthSnapshotService kernelHealth = new KernelHealthSnapshotService(dataCloudClient, TENANT_ID);

        KernelHealthSnapshotService.ProductUnitHealthView health =
                runPromise(() -> kernelHealth.getProductUnitHealth(PRODUCT_UNIT_ID));

        assertThat(health.productUnitId()).isEqualTo(PRODUCT_UNIT_ID);
        assertThat(health.overallStatus()).isEqualTo("healthy");
        assertThat(health.currentPhase()).isEqualTo("observe");
        assertThat(health.deploymentStatus()).isEqualTo("ready");
    }

    private static final class InMemoryDataCloudClient implements DataCloudClient {
        private final Map<String, Map<String, Map<String, Entity>>> tenants = new ConcurrentHashMap<>();
        private final AtomicLong offsets = new AtomicLong();

        @Override
        public Promise<Entity> save(String tenantId, String collection, Map<String, Object> data) {
            String id = idFor(collection, data);
            Entity entity = Entity.of(id, collection, data);
            tenants
                    .computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>())
                    .computeIfAbsent(collection, ignored -> new ConcurrentHashMap<>())
                    .put(id, entity);
            return Promise.of(entity);
        }

        @Override
        public Promise<Optional<Entity>> findById(String tenantId, String collection, String id) {
            return Promise.of(Optional.ofNullable(tenants
                    .getOrDefault(tenantId, Map.of())
                    .getOrDefault(collection, Map.of())
                    .get(id)));
        }

        @Override
        public Promise<List<Entity>> query(String tenantId, String collection, Query query) {
            List<Entity> matches = tenants
                    .getOrDefault(tenantId, Map.of())
                    .getOrDefault(collection, Map.of())
                    .values()
                    .stream()
                    .filter(entity -> matchesFilters(entity.data(), query.filters()))
                    .skip(query.offset())
                    .limit(query.limit())
                    .toList();
            return Promise.of(matches);
        }

        @Override
        public Promise<Void> delete(String tenantId, String collection, String id) {
            Optional.ofNullable(tenants.get(tenantId))
                    .map(collections -> collections.get(collection))
                    .ifPresent(items -> items.remove(id));
            return Promise.complete();
        }

        @Override
        public Promise<Offset> appendEvent(String tenantId, Event event) {
            Objects.requireNonNull(event, "event");
            return Promise.of(Offset.of(offsets.incrementAndGet()));
        }

        @Override
        public Promise<List<Event>> queryEvents(String tenantId, EventQuery query) {
            return Promise.of(List.of());
        }

        @Override
        public Subscription tailEvents(String tenantId, TailRequest request, java.util.function.Consumer<Event> handler) {
            return new Subscription() {
                private boolean cancelled;

                @Override
                public void cancel() {
                    cancelled = true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }
            };
        }

        @Override
        public void close() {
            tenants.clear();
        }

        @Override
        public EntityStore entityStore() {
            return null;
        }

        @Override
        public EventLogStore eventLogStore() {
            return null;
        }

        private static boolean matchesFilters(Map<String, Object> data, List<Filter> filters) {
            for (Filter filter : filters) {
                Object actual = data.get(filter.field());
                if (!Objects.equals(actual, filter.value())) {
                    return false;
                }
            }
            return true;
        }

        private String idFor(String collection, Map<String, Object> data) {
            for (String key : List.of("id", "productUnitId", "runId")) {
                Object value = data.get(key);
                if (value instanceof String text && !text.isBlank()) {
                    return text;
                }
            }
            int next = tenants.values().stream()
                    .map(items -> items.getOrDefault(collection, Map.of()).size())
                    .reduce(0, Integer::sum) + 1;
            return collection + "-" + next;
        }
    }
}
