package com.ghatana.yappc.services.evolve;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.learn.Insights;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies Data Cloud evolution proposal persistence lineage fields
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("DataCloudEvolutionPlanRepository")
class DataCloudEvolutionPlanRepositoryTest extends EventloopTestBase {

    @Test
    @DisplayName("save persists source observation and learning evidence links")
    void savePersistsSourceObservationAndLearningEvidenceLinks() {
        DataCloudClient dataCloudClient = mock(DataCloudClient.class);
        when(dataCloudClient.save(eq("tenant-123"), eq("yappc_evolution_proposals"), any()))
                .thenReturn(Promise.of(DataCloudClient.Entity.of(
                        "proposal-123",
                        "yappc_evolution_proposals",
                        Map.of())));

        DataCloudEvolutionPlanRepository repository = new DataCloudEvolutionPlanRepository(dataCloudClient);
        EvolutionPlanRepository.EvolutionProposal proposal = new EvolutionPlanRepository.EvolutionProposal(
                "proposal-123",
                "tenant-123",
                "project-123",
                Insights.builder()
                        .id("insight-123")
                        .observationRef("project-123:obs-123")
                        .build(),
                EvolutionPlan.builder()
                        .id("plan-123")
                        .insightsRef("insight-123")
                        .newIntentRef("pui-123")
                        .build(),
                null,
                "PENDING_APPROVAL",
                "pui-123",
                List.of("insight-123", "project-123:obs-123", "learn-run-1"),
                Map.of(
                        "sourceInsightsRef", "insight-123",
                        "sourceObservationRef", "project-123:obs-123",
                        "sourceLearningEvidenceIds", List.of("learn-run-1"),
                        "impactAnalysis", Map.of(
                                "status", "READY",
                                "affectedSurfaces", List.of("web"),
                                "affectedModules", List.of("checkout"),
                                "affectedTests", List.of("checkout validation"),
                                "runtimeImpacts", List.of("preview"))),
                Instant.parse("2026-05-26T22:00:00Z"));

        runPromise(() -> repository.save(proposal));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> documentCaptor =
                (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(dataCloudClient).save(eq("tenant-123"), eq("yappc_evolution_proposals"), documentCaptor.capture());
        assertThat(documentCaptor.getValue()).containsEntry("sourceInsightsRef", "insight-123");
        assertThat(documentCaptor.getValue()).containsEntry("sourceObservationRef", "project-123:obs-123");
        assertThat(documentCaptor.getValue()).containsEntry("sourceLearningEvidenceIds", List.of("learn-run-1"));
        assertThat(documentCaptor.getValue()).containsEntry("provenance", List.of("insight-123", "project-123:obs-123", "learn-run-1"));
        assertThat(documentCaptor.getValue()).containsEntry("impactAnalysis", Map.of(
                "status", "READY",
                "affectedSurfaces", List.of("web"),
                "affectedModules", List.of("checkout"),
                "affectedTests", List.of("checkout validation"),
                "runtimeImpacts", List.of("preview")));
    }
}
