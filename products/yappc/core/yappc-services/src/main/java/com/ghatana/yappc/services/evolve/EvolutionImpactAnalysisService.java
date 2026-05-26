package com.ghatana.yappc.services.evolve;

import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.learn.Insights;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @doc.type interface
 * @doc.purpose Computes Evolve proposal blast radius before lifecycle execution
 * @doc.layer service
 * @doc.pattern Service Port
 */
public interface EvolutionImpactAnalysisService {

    /**
     * Computes affected surfaces, modules, tests, runtime impacts, and dependency nodes.
     *
     * @param request scoped Evolve impact analysis request
     * @return impact analysis payload for proposal metadata and review UI
     */
    Promise<EvolutionImpactAnalysis> analyze(@NotNull ImpactAnalysisRequest request);

    /**
     * Creates an explicit unavailable analyzer for isolated tests and legacy composition.
     *
     * @return analyzer that records why canonical graph impact is unavailable
     */
    static EvolutionImpactAnalysisService unavailable() {
        return request -> Promise.of(EvolutionImpactAnalysis.unavailable(
                "Evolution impact analysis is not wired for this composition"));
    }

    /**
     * Scoped impact analysis input.
     *
     * @param tenantId tenant owner
     * @param workspaceId workspace owner when available
     * @param projectId project identifier
     * @param insights source learning insights
     * @param plan proposed evolution plan
     */
    record ImpactAnalysisRequest(
            @NotNull String tenantId,
            @Nullable String workspaceId,
            @NotNull String projectId,
            @NotNull Insights insights,
            @NotNull EvolutionPlan plan
    ) {
    }
}
