package com.ghatana.yappc.services.evolve;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.evolve.EvolutionTask;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.storage.ArtifactGraphRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies canonical artifact graph impact analysis for Evolve proposals
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("ArtifactGraphEvolutionImpactAnalysisService")
class ArtifactGraphEvolutionImpactAnalysisServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("analyze lists affected surfaces modules tests and runtime impacts from artifact graph")
    void analyzeListsAffectedSurfacesModulesTestsAndRuntimeImpacts() {
        ArtifactGraphRepository repository = mock(ArtifactGraphRepository.class);
        when(repository.findNodesByProduct("project-123", "tenant-123", "workspace-123", 10_000))
                .thenReturn(Promise.of(List.of(
                        node("surface-web", "surface", "web", Map.of("surface", "web")),
                        node("module-checkout", "module", "checkout", Map.of("module", "checkout")),
                        node("test-checkout", "integration-test", "checkout validation", Map.of("module", "checkout")),
                        node("runtime-preview", "preview-runtime", "preview", Map.of("runtime", "preview")))));

        ArtifactGraphEvolutionImpactAnalysisService service = new ArtifactGraphEvolutionImpactAnalysisService(repository);
        EvolutionPlan plan = EvolutionPlan.builder()
                .id("plan-123")
                .insightsRef("insights-123")
                .tasks(List.of(EvolutionTask.builder()
                        .id("task-1")
                        .type("fix")
                        .description("Fix checkout web preview validation")
                        .details(Map.of(
                                "surface", "web",
                                "module", "checkout",
                                "tests", List.of("checkout validation"),
                                "runtime", "preview"))
                        .build()))
                .createdAt(Instant.parse("2026-05-26T22:30:00Z"))
                .build();

        EvolutionImpactAnalysis analysis = runPromise(() -> service.analyze(
                new EvolutionImpactAnalysisService.ImpactAnalysisRequest(
                        "tenant-123",
                        "workspace-123",
                        "project-123",
                        Insights.builder().id("insights-123").observationRef("workspace-123/project-123:obs-1").build(),
                        plan)));

        assertThat(analysis.status()).isEqualTo("READY");
        assertThat(analysis.truthSource()).isEqualTo("artifact-graph");
        assertThat(analysis.affectedSurfaces()).contains("web");
        assertThat(analysis.affectedModules()).contains("checkout");
        assertThat(analysis.affectedTests()).contains("checkout validation");
        assertThat(analysis.runtimeImpacts()).contains("preview");
        assertThat(analysis.dependencyNodeIds())
                .contains("surface-web", "module-checkout", "test-checkout", "runtime-preview");
    }

    @Test
    @DisplayName("analyze returns degraded inferred impact when workspace scope is unavailable")
    void analyzeReturnsDegradedInferredImpactWhenWorkspaceScopeIsUnavailable() {
        ArtifactGraphRepository repository = mock(ArtifactGraphRepository.class);
        ArtifactGraphEvolutionImpactAnalysisService service = new ArtifactGraphEvolutionImpactAnalysisService(repository);
        EvolutionPlan plan = EvolutionPlan.builder()
                .id("plan-123")
                .insightsRef("insights-123")
                .tasks(List.of(EvolutionTask.builder()
                        .id("task-1")
                        .type("observe")
                        .description("Improve web run validation")
                        .details(Map.of("module", "runtime-health"))
                        .build()))
                .build();

        EvolutionImpactAnalysis analysis = runPromise(() -> service.analyze(
                new EvolutionImpactAnalysisService.ImpactAnalysisRequest(
                        "tenant-123",
                        "workspace-unavailable",
                        "project-123",
                        Insights.builder().id("insights-123").observationRef("project-123:obs-1").build(),
                        plan)));

        assertThat(analysis.status()).isEqualTo("DEGRADED");
        assertThat(analysis.truthSource()).isEqualTo("task-inference");
        assertThat(analysis.affectedSurfaces()).contains("web");
        assertThat(analysis.affectedModules()).contains("runtime-health");
        assertThat(analysis.affectedTests()).contains("validation");
        assertThat(analysis.runtimeImpacts()).contains("runtime");
        assertThat(analysis.notes()).contains("workspaceId is unavailable; canonical artifact graph traversal was skipped");
    }

    private static ArtifactNodeDto node(String id, String type, String name, Map<String, Object> properties) {
        return new ArtifactNodeDto(
                id,
                type,
                name,
                "src/" + name + ".java",
                null,
                properties,
                List.of(type),
                "tenant-123",
                "project-123",
                null,
                "test",
                "1.0.0",
                1.0,
                "exact",
                List.of(),
                List.of(),
                "source:" + id,
                id);
    }
}
