package com.ghatana.yappc.services.evolve;

import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.intent.ConstraintSpec;
import com.ghatana.yappc.domain.learn.Insights;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persists evolution proposals with approval and traceability metadata.
 *
 * @doc.type interface
 * @doc.purpose Durable evolution proposal repository contract
 * @doc.layer service
 * @doc.pattern Repository Port
 */
public interface EvolutionPlanRepository {

    /**
     * Persists one evolution proposal.
     *
     * @param proposal evolution proposal payload
     * @return promise that completes after persistence
     */
    Promise<Void> save(@NotNull EvolutionProposal proposal);

    /**
     * Creates a repository that intentionally performs no durable write.
     *
     * @return no-op repository for isolated tests that do not compose Data Cloud
     */
    static EvolutionPlanRepository noop() {
        return proposal -> Promise.complete();
    }

    /**
     * Durable evolution proposal payload.
     *
     * @param proposalId unique proposal identifier
     * @param tenantId tenant that owns the proposal
     * @param projectId project or insight-derived project reference
     * @param insights source insights
     * @param plan generated evolution plan
     * @param constraints optional constraints used during planning
     * @param approvalState current approval state
     * @param productUnitIntentRef generated ProductUnitIntent update reference
     * @param provenance ordered provenance references
     * @param metadata repository-specific metadata
     * @param createdAt proposal creation timestamp
     */
    record EvolutionProposal(
            @NotNull String proposalId,
            @NotNull String tenantId,
            @NotNull String projectId,
            @NotNull Insights insights,
            @NotNull EvolutionPlan plan,
            @Nullable ConstraintSpec constraints,
            @NotNull String approvalState,
            @NotNull String productUnitIntentRef,
            @NotNull List<String> provenance,
            @NotNull Map<String, Object> metadata,
            @NotNull Instant createdAt
    ) {
    }
}
