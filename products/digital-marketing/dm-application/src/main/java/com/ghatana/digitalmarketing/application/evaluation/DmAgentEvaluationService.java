package com.ghatana.digitalmarketing.application.evaluation;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.evaluation.DmAgentEvaluation;
import com.ghatana.digitalmarketing.domain.evaluation.DmAgentEvaluation.DmEvalMetric;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for agent evaluation submission and retrieval.
 *
 * @doc.type interface
 * @doc.purpose Submit and query agent performance evaluations (DMOS-F3-005)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmAgentEvaluationService {

    Promise<DmAgentEvaluation> submit(DmOperationContext ctx, SubmitEvaluationCommand command);

    Promise<Optional<DmAgentEvaluation>> findById(DmOperationContext ctx, String evaluationId);

    Promise<List<DmAgentEvaluation>> listByAgent(DmOperationContext ctx, String agentId);

    /**
     * Command to submit an agent evaluation.
     */
    record SubmitEvaluationCommand(
        String agentId,
        String agentType,
        List<DmEvalMetric> metrics,
        double overallScore,
        String verdict
    ) {
        public SubmitEvaluationCommand {
            Objects.requireNonNull(agentId, "agentId must not be null");
            Objects.requireNonNull(metrics, "metrics must not be null");
            Objects.requireNonNull(verdict, "verdict must not be null");
            if (agentId.isBlank()) throw new IllegalArgumentException("agentId must not be blank");
        }
    }
}
