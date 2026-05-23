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
import java.util.Optional;

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
         * Resolves one proposal state by ID.
         *
         * @param tenantId tenant owner
         * @param proposalId proposal identifier
         * @return optional proposal state when found
         */
        Promise<Optional<EvolutionProposalState>> findProposalState(
            @NotNull String tenantId,
            @NotNull String proposalId
        );

        /**
         * Transitions proposal approval state and stores decision metadata.
         *
         * @param tenantId tenant owner
         * @param proposalId proposal identifier
         * @param approvalState new approval state (for example APPROVED or REJECTED)
         * @param decidedBy actor that made the decision
         * @param decisionReason optional freeform decision reason
         * @param transitionMetadata metadata describing downstream handoff actions
         * @return promise completing after update
         */
        Promise<Void> transitionApprovalState(
            @NotNull String tenantId,
            @NotNull String proposalId,
            @NotNull String approvalState,
            @NotNull String decidedBy,
            @Nullable String decisionReason,
            @NotNull Map<String, Object> transitionMetadata
        );

    /**
     * Creates a repository that intentionally performs no durable write.
     *
     * @return no-op repository for isolated tests that do not compose Data Cloud
     */
    static EvolutionPlanRepository noop() {
        return new EvolutionPlanRepository() {
            @Override
            public Promise<Void> save(@NotNull EvolutionProposal proposal) {
                return Promise.complete();
            }

            @Override
            public Promise<Optional<EvolutionProposalState>> findProposalState(
                    @NotNull String tenantId,
                    @NotNull String proposalId
            ) {
                return Promise.of(Optional.empty());
            }

            @Override
            public Promise<Void> transitionApprovalState(
                    @NotNull String tenantId,
                    @NotNull String proposalId,
                    @NotNull String approvalState,
                    @NotNull String decidedBy,
                    @Nullable String decisionReason,
                    @NotNull Map<String, Object> transitionMetadata
            ) {
                return Promise.complete();
            }
        };
    }

    /**
     * Minimal persisted proposal state for approval transitions.
     *
     * @param proposalId proposal identifier
     * @param tenantId tenant owner
     * @param projectId project reference
     * @param approvalState current approval state
     * @param productUnitIntentRef generated ProductUnitIntent reference
     * @param metadata metadata captured at proposal creation/update
     * @param createdAt creation timestamp
     */
    record EvolutionProposalState(
            @NotNull String proposalId,
            @NotNull String tenantId,
            @NotNull String projectId,
            @NotNull String approvalState,
            @NotNull String productUnitIntentRef,
            @NotNull Map<String, Object> metadata,
            @NotNull Instant createdAt
    ) {
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
