package com.ghatana.yappc.services.evolve;

import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.services.kernel.KernelProductUnitHandoffService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies Evolve produces Kernel-compatible ProductUnitIntent update requests
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("KernelProductUnitEvolutionUpdateService")
class KernelProductUnitEvolutionUpdateServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("prepare update generates validated Kernel ProductUnitIntent change request")
    void prepareUpdateGeneratesValidatedKernelProductUnitIntentChangeRequest() {
        KernelProductUnitEvolutionUpdateService service =
                new KernelProductUnitEvolutionUpdateService(new KernelProductUnitHandoffService());
        EvolutionPlan plan = EvolutionPlan.builder()
                .id("plan-123")
                .insightsRef("insights-123")
                .newIntentRef("intent-local")
                .createdAt(Instant.parse("2026-05-26T23:00:00Z"))
                .metadata(Map.of(
                        "kernelGoverned", "true",
                        "workspaceId", "workspace-1",
                        "projectName", "Project One",
                        "surfaces", "web,backend-api",
                        "runtimeProvider", "ghatana-file-registry",
                        "sourceProvider", "ghatana-file-registry",
                        "lifecycleProfile", "standard-web-api-product",
                        "correlationId", "corr-1"))
                .build();
        EvolutionImpactAnalysis impact = new EvolutionImpactAnalysis(
                "READY",
                "artifact-graph",
                List.of("web"),
                List.of("checkout"),
                List.of("checkout validation"),
                List.of("preview"),
                List.of("node-1"),
                List.of());

        EvolutionKernelUpdateService.EvolutionKernelUpdate update = runPromise(() -> service.prepareUpdate(
                new EvolutionKernelUpdateService.EvolutionKernelUpdateRequest(
                        "tenant-1",
                        "project-1",
                        plan,
                        impact)));

        assertThat(update.status()).isEqualTo("GENERATED");
        assertThat(update.productUnitIntentRef()).isNotBlank();
        assertThat(update.metadata()).containsEntry("changeRequestType", "evolution-update");
        assertThat(update.productUnitIntent()).containsEntry("intentType", "create");
        Map<?, ?> productUnit = (Map<?, ?>) update.productUnitIntent().get("productUnit");
        assertThat(productUnit.get("id")).isEqualTo("project-1");
        assertThat(productUnit.get("metadata").toString())
                .contains("evolution-update", "plan-123", "impactAnalysis");
    }

    @Test
    @DisplayName("prepare update blocks Kernel-governed plans without required metadata")
    void prepareUpdateBlocksKernelGovernedPlansWithoutRequiredMetadata() {
        KernelProductUnitEvolutionUpdateService service =
                new KernelProductUnitEvolutionUpdateService(new KernelProductUnitHandoffService());
        EvolutionPlan plan = EvolutionPlan.builder()
                .id("plan-123")
                .insightsRef("insights-123")
                .metadata(Map.of("kernelGoverned", "true"))
                .build();

        EvolutionKernelUpdateService.EvolutionKernelUpdate update = runPromise(() -> service.prepareUpdate(
                new EvolutionKernelUpdateService.EvolutionKernelUpdateRequest(
                        "tenant-1",
                        "project-1",
                        plan,
                        EvolutionImpactAnalysis.empty("artifact-graph", List.of()))));

        assertThat(update.status()).isEqualTo("BLOCKED");
        assertThat(update.metadata().get("reason").toString()).contains("workspaceId");
    }
}
