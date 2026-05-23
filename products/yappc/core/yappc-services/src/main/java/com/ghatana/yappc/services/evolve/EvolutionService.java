package com.ghatana.yappc.services.evolve;

import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.intent.ConstraintSpec;
import com.ghatana.yappc.domain.learn.Insights;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * @doc.type interface
 * @doc.purpose Proposes system improvements based on insights
 * @doc.layer service
 * @doc.pattern Service
 */
public interface EvolutionService {
    /**
     * Proposes evolution plan from insights.
     *
     * @param insights The insights from learning phase
     * @return Promise of evolution plan
     */
    Promise<EvolutionPlan> propose(Insights insights);

    /**
     * Proposes evolution with business/technical constraints.
     *
     * @param insights The insights
     * @param constraints Constraints to honor
     * @return Promise of constrained evolution plan
     */
    Promise<EvolutionPlan> proposeWithConstraints(Insights insights, ConstraintSpec constraints);

    /**
     * Approves an evolution proposal and returns lifecycle handoff details.
     *
     * @param proposalId proposal identifier
     * @param decidedBy actor who approved the proposal
     * @param reason optional decision rationale
     * @return decision payload including suggested lifecycle execution phases
     */
    Promise<EvolutionDecision> approveProposal(String proposalId, String decidedBy, String reason);

    /**
     * Rejects an evolution proposal and records decision traceability.
     *
     * @param proposalId proposal identifier
     * @param decidedBy actor who rejected the proposal
     * @param reason optional decision rationale
     * @return decision payload indicating no execution handoff
     */
    Promise<EvolutionDecision> rejectProposal(String proposalId, String decidedBy, String reason);

    /**
     * Result payload for evolve approval decisions.
     *
     * @param proposalId proposal identifier
     * @param tenantId tenant owner
     * @param projectId project reference
     * @param decision APPROVED or REJECTED
     * @param shouldExecuteLifecycle whether validate/generate/run should be triggered
     * @param executionPhases ordered lifecycle phases for handoff
     * @param productUnitIntentRef generated intent reference to validate and execute
     * @param metadata decision metadata for traceability
     */
    record EvolutionDecision(
            String proposalId,
            String tenantId,
            String projectId,
            String decision,
            boolean shouldExecuteLifecycle,
            List<String> executionPhases,
            String productUnitIntentRef,
            Map<String, Object> metadata
    ) {
    }
}
