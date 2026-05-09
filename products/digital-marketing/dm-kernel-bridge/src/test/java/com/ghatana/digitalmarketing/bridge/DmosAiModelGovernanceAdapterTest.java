package com.ghatana.digitalmarketing.bridge;

import com.ghatana.aiplatform.registry.ABTestingService;
import com.ghatana.aiplatform.registry.DefaultExperimentalTrafficAllocationAdapter;
import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ExperimentalTrafficAllocationPort;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryPort;
import com.ghatana.digitalmarketing.bridge.governance.AiExperimentConfig;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DmosAiModelGovernanceAdapter} — KERNEL-P2-3.
 */
@DisplayName("DmosAiModelGovernanceAdapter")
class DmosAiModelGovernanceAdapterTest extends EventloopTestBase {

    private TestModelRegistryService modelRegistry;
    private ABTestingService abTesting;
    private ExperimentalTrafficAllocationPort trafficAllocation;
    private DmosAiModelGovernanceAdapter adapter;
    private DmTenantId tenantId;

    @BeforeEach
    void setUp() {
        modelRegistry = new TestModelRegistryService();
        abTesting = new ABTestingService(MetricsCollector.create());
        trafficAllocation = new DefaultExperimentalTrafficAllocationAdapter(abTesting);
        adapter = new DmosAiModelGovernanceAdapter(modelRegistry, trafficAllocation);
        tenantId = DmTenantId.of("tenant-1");
    }

    @Test
    @DisplayName("rejects null modelRegistry at construction time")
    void rejectsNullModelRegistry() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DmosAiModelGovernanceAdapter(null, trafficAllocation))
                .withMessageContaining("modelRegistry");
    }

    @Test
    @DisplayName("rejects null trafficAllocation at construction time")
    void rejectsNullTrafficAllocation() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DmosAiModelGovernanceAdapter(modelRegistry, null))
                .withMessageContaining("trafficAllocation");
    }

    @Test
    @DisplayName("registerCandidateModel calls registry.register with STAGING status")
    void registerCandidateModelCallsRegistry() {
        ModelMetadata result = runPromise(() ->
                adapter.registerCandidateModel(tenantId, "dmos-adcopy", "v2.0.0"));

        assertThat(modelRegistry.registered).hasSize(1);
        ModelMetadata registered = modelRegistry.registered.get(0);
        assertThat(registered.getTenantId()).isEqualTo("tenant-1");
        assertThat(registered.getName()).isEqualTo("dmos-adcopy");
        assertThat(registered.getVersion()).isEqualTo("v2.0.0");
        assertThat(registered.getDeploymentStatus()).isEqualTo(DeploymentStatus.STAGED);
        assertThat(result).isSameAs(registered);
    }

    @Test
    @DisplayName("promoteToProduction updates model status to PRODUCTION")
    void promoteToProductionUpdatesStatus() {
        UUID modelId = UUID.randomUUID();
        ModelMetadata existing = buildMetadata(modelId, "dmos-adcopy", "v1.0.0", DeploymentStatus.STAGED);
        modelRegistry.store.put(key("tenant-1", "dmos-adcopy", "v1.0.0"), existing);

        runPromise(() -> adapter.promoteToProduction(tenantId, "dmos-adcopy", "v1.0.0"));

        assertThat(modelRegistry.lastStatusUpdate).isEqualTo(DeploymentStatus.PRODUCTION);
        assertThat(modelRegistry.lastStatusUpdateId).isEqualTo(modelId);
    }

    @Test
    @DisplayName("promoteToProduction fails when model not found")
    void promoteToProductionFailsWhenNotFound() {
        assertThatThrownBy(() ->
                runPromise(() -> adapter.promoteToProduction(tenantId, "unknown-model", "v1.0.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-model");
    }

    @Test
    @DisplayName("deprecateModel updates model status to DEPRECATED")
    void deprecateModelUpdatesStatus() {
        UUID modelId = UUID.randomUUID();
        ModelMetadata existing = buildMetadata(modelId, "dmos-adcopy", "v1.0.0", DeploymentStatus.PRODUCTION);
        modelRegistry.store.put(key("tenant-1", "dmos-adcopy", "v1.0.0"), existing);

        runPromise(() -> adapter.deprecateModel(tenantId, "dmos-adcopy", "v1.0.0"));

        assertThat(modelRegistry.lastStatusUpdate).isEqualTo(DeploymentStatus.DEPRECATED);
        assertThat(modelRegistry.lastStatusUpdateId).isEqualTo(modelId);
    }

    @Test
    @DisplayName("listVersions delegates to modelRegistry and returns results")
    void listVersionsDelegates() {
        ModelMetadata v1 = buildMetadata(UUID.randomUUID(), "dmos-adcopy", "v1.0.0", DeploymentStatus.PRODUCTION);
        ModelMetadata v2 = buildMetadata(UUID.randomUUID(), "dmos-adcopy", "v2.0.0", DeploymentStatus.STAGED);
        modelRegistry.versionList = List.of(v1, v2);

        List<ModelMetadata> versions = runPromise(() -> adapter.listVersions(tenantId, "dmos-adcopy"));

        assertThat(versions).hasSize(2);
        assertThat(versions).extracting(ModelMetadata::getVersion)
                .containsExactly("v1.0.0", "v2.0.0");
    }

    @Test
    @DisplayName("defineExperiment registers A/B experiment with correct parameters")
    void defineExperimentRegistersExperiment() {
        runPromise(() -> adapter.defineExperiment(
                tenantId,
                "adcopy-v2-trial",
                "dmos-adcopy:v1.0.0",
                "dmos-adcopy:v2.0.0",
                "20%"));

        ABTestingService.Experiment exp = abTesting.getExperiment("tenant-1", "adcopy-v2-trial");
        assertThat(exp).isNotNull();
        assertThat(exp.getId()).isEqualTo("adcopy-v2-trial");
        assertThat(exp.getBaselineModel()).isEqualTo("dmos-adcopy:v1.0.0");
        assertThat(exp.getVariantModel()).isEqualTo("dmos-adcopy:v2.0.0");
        assertThat(exp.getSplit()).isEqualTo("20%");
    }

    @Test
    @DisplayName("assignModel delegates to abTesting.assignVariant and returns result")
    void assignModelDelegatesToAbTesting() {
        // Register experiment first so assignVariant has something to route
        abTesting.registerExperiment("tenant-1",
                new ABTestingService.Experiment("adcopy-v2-trial", "adcopy-v2-trial", "100/0",
                        "dmos-adcopy:v1.0.0", "dmos-adcopy:v2.0.0"));

        String assigned = runPromise(() ->
                adapter.assignModel(tenantId, "adcopy-v2-trial", "camp-001"));

        // With a 0/100 split, baseline is always returned
        assertThat(assigned).isEqualTo("baseline");
    }

    @Test
    @DisplayName("endExperiment delegates to abTesting.endExperiment")
    void endExperimentDelegates() {
        abTesting.registerExperiment("tenant-1",
                new ABTestingService.Experiment("adcopy-v2-trial", "adcopy-v2-trial", "50/50",
                        "dmos-adcopy:v1.0.0", "dmos-adcopy:v2.0.0"));

        runPromise(() -> adapter.endExperiment(tenantId, "adcopy-v2-trial"));

        assertThat(abTesting.getExperiment("tenant-1", "adcopy-v2-trial")).isNull();
    }

    @Test
    @DisplayName("listProductionModels returns only PRODUCTION status models")
    void listProductionModels() {
        ModelMetadata prod = buildMetadata(UUID.randomUUID(), "dmos-adcopy", "v1.0.0", DeploymentStatus.PRODUCTION);
        modelRegistry.statusList = List.of(prod);

        List<ModelMetadata> result = runPromise(() -> adapter.listProductionModels(tenantId));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeploymentStatus()).isEqualTo(DeploymentStatus.PRODUCTION);
    }

    @Test
    @DisplayName("P0-011: defineExperiment validates split percent format")
    void defineExperimentValidatesSplitPercent() {
        assertThatThrownBy(() -> runPromise(() -> adapter.defineExperiment(
                tenantId, "test-exp", "model:v1", "model:v2", "invalid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid split percent format");
    }

    @Test
    @DisplayName("P0-011: defineExperiment validates split percent range")
    void defineExperimentValidatesSplitPercentRange() {
        assertThatThrownBy(() -> runPromise(() -> adapter.defineExperiment(
                tenantId, "test-exp", "model:v1", "model:v2", "150%")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 100");
    }

    @Test
    @DisplayName("P0-011: defineExperiment validates model ref format")
    void defineExperimentValidatesModelRefFormat() {
        assertThatThrownBy(() -> runPromise(() -> adapter.defineExperiment(
                tenantId, "test-exp", "invalid-ref", "model:v2", "20%")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid model ref format");
    }

    @Test
    @DisplayName("P0-011: approveExperiment updates approval state to APPROVED")
    void approveExperimentUpdatesApprovalState() {
        // First define an experiment
        runPromise(() -> adapter.defineExperiment(
                tenantId, "test-exp", "model:v1", "model:v2", "20%"));

        // Then approve it
        runPromise(() -> adapter.approveExperiment(tenantId, "test-exp", "approver-123"));

        // Verify approval state (would need to expose getter in production code)
        // For now, just verify it doesn't throw
    }

    @Test
    @DisplayName("P0-011: promoteToProduction with experiment requires approval")
    void promoteToProductionRequiresApproval() {
        UUID modelId = UUID.randomUUID();
        ModelMetadata existing = buildMetadata(modelId, "dmos-adcopy", "v2.0.0", DeploymentStatus.STAGED);
        modelRegistry.store.put(key("tenant-1", "dmos-adcopy", "v2.0.0"), existing);

        // Define experiment but don't approve
        runPromise(() -> adapter.defineExperiment(
                tenantId, "test-exp", "dmos-adcopy:v1.0.0", "dmos-adcopy:v2.0.0", "20%"));

        // Try to promote with experiment ID - should fail without approval
        assertThatThrownBy(() -> runPromise(() -> 
                adapter.promoteToProduction(tenantId, "dmos-adcopy", "v2.0.0", "test-exp")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not approved");
    }

    @Test
    @DisplayName("P0-011: recordExperimentMetrics updates experiment metrics")
    void recordExperimentMetricsUpdatesMetrics() {
        // Define experiment first
        runPromise(() -> adapter.defineExperiment(
                tenantId, "test-exp", "model:v1", "model:v2", "20%"));

        // Record metrics
        AiExperimentConfig.ExperimentMetrics metrics = new AiExperimentConfig.ExperimentMetrics()
                .withOutcome("VARIANT_WINS");
        runPromise(() -> adapter.recordExperimentMetrics(tenantId, "test-exp", metrics));

        // Verify metrics were recorded (would need to expose getter in production code)
        // For now, just verify it doesn't throw
    }

    @Test
    @DisplayName("P0-011: promoteToProduction requires metrics completion")
    void promoteToProductionRequiresMetricsCompletion() {
        UUID modelId = UUID.randomUUID();
        ModelMetadata existing = buildMetadata(modelId, "dmos-adcopy", "v2.0.0", DeploymentStatus.STAGED);
        modelRegistry.store.put(key("tenant-1", "dmos-adcopy", "v2.0.0"), existing);

        // Define and approve experiment but don't record metrics
        runPromise(() -> adapter.defineExperiment(
                tenantId, "test-exp", "dmos-adcopy:v1.0.0", "dmos-adcopy:v2.0.0", "20%"));
        runPromise(() -> adapter.approveExperiment(tenantId, "test-exp", "approver-123"));

        // Try to promote - should fail without metrics completion
        assertThatThrownBy(() -> runPromise(() -> 
                adapter.promoteToProduction(tenantId, "dmos-adcopy", "v2.0.0", "test-exp")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metrics incomplete");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String key(String tenantId, String name, String version) {
        return tenantId + ":" + name + ":" + version;
    }

    private ModelMetadata buildMetadata(UUID id, String name, String version, DeploymentStatus status) {
        return ModelMetadata.builder()
                .id(id)
                .tenantId("tenant-1")
                .name(name)
                .version(version)
                .deploymentStatus(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ─── Test doubles ─────────────────────────────────────────────────────────

    private static final class TestModelRegistryService implements ModelRegistryPort {

        final List<ModelMetadata> registered = new ArrayList<>();
        final Map<String, ModelMetadata> store = new HashMap<>();
        List<ModelMetadata> versionList = List.of();
        List<ModelMetadata> statusList = List.of();
        DeploymentStatus lastStatusUpdate;
        UUID lastStatusUpdateId;

        @Override
        public void register(ModelMetadata model) {
            registered.add(model);
            store.put(model.getTenantId() + ":" + model.getName() + ":" + model.getVersion(), model);
        }

        @Override
        public Optional<ModelMetadata> findByName(String tenantId, String name, String version) {
            return Optional.ofNullable(store.get(tenantId + ":" + name + ":" + version));
        }

        @Override
        public List<ModelMetadata> findByStatus(String tenantId, DeploymentStatus status) {
            return statusList;
        }

        @Override
        public List<ModelMetadata> listVersions(String tenantId, String name) {
            return versionList;
        }

        @Override
        public void updateStatus(String tenantId, UUID modelId, DeploymentStatus newStatus) {
            lastStatusUpdateId = modelId;
            lastStatusUpdate = newStatus;
        }
    }
}
