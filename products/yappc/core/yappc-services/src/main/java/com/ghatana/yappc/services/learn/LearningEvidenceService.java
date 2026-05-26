package com.ghatana.yappc.services.learn;

import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.platform.ai.InsightFeedbackService;
import com.ghatana.yappc.services.lifecycle.ApprovalRequest;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Records lifecycle outcomes that the Learn phase can use as evidence.
 *
 * @doc.type interface
 * @doc.purpose Records validation, generation, run, and user feedback outcomes as Learn evidence
 * @doc.layer service
 * @doc.pattern Service Port
 */
public interface LearningEvidenceService {

    /**
     * Records validation outcome evidence.
     *
     * @param context lifecycle evidence context
     * @param result validation result
     * @return durable evidence ID
     */
    Promise<String> recordValidationOutcome(
            @NotNull EvidenceContext context,
            @NotNull LifecycleValidationResult result);

    /**
     * Records generation outcome evidence.
     *
     * @param context lifecycle evidence context
     * @param artifacts generated artifacts
     * @return durable evidence ID
     */
    Promise<String> recordGenerationOutcome(
            @NotNull EvidenceContext context,
            @NotNull GeneratedArtifacts artifacts);

    /**
     * Records run outcome evidence.
     *
     * @param context lifecycle evidence context
     * @param result run result
     * @return durable evidence ID
     */
    Promise<String> recordRunOutcome(
            @NotNull EvidenceContext context,
            @NotNull RunResult result);

    /**
     * Records human feedback outcome evidence.
     *
     * @param context lifecycle evidence context
     * @param feedback user feedback
     * @return durable evidence ID
     */
    Promise<String> recordUserFeedbackOutcome(
            @NotNull EvidenceContext context,
            @NotNull InsightFeedbackService.InsightFeedback feedback);

    /**
     * Records human approval or rejection evidence.
     *
     * @param context lifecycle evidence context
     * @param request terminal approval request
     * @return durable evidence ID
     */
    Promise<String> recordApprovalDecisionOutcome(
            @NotNull EvidenceContext context,
            @NotNull ApprovalRequest request);

    /**
     * Returns a service that intentionally does not persist evidence.
     *
     * @return no-op service for tests or disabled composition
     */
    static LearningEvidenceService noop() {
        return new LearningEvidenceService() {
            @Override
            public Promise<String> recordValidationOutcome(
                    @NotNull EvidenceContext context,
                    @NotNull LifecycleValidationResult result) {
                return Promise.of("learning-evidence-disabled");
            }

            @Override
            public Promise<String> recordGenerationOutcome(
                    @NotNull EvidenceContext context,
                    @NotNull GeneratedArtifacts artifacts) {
                return Promise.of("learning-evidence-disabled");
            }

            @Override
            public Promise<String> recordRunOutcome(
                    @NotNull EvidenceContext context,
                    @NotNull RunResult result) {
                return Promise.of("learning-evidence-disabled");
            }

            @Override
            public Promise<String> recordUserFeedbackOutcome(
                    @NotNull EvidenceContext context,
                    @NotNull InsightFeedbackService.InsightFeedback feedback) {
                return Promise.of("learning-evidence-disabled");
            }

            @Override
            public Promise<String> recordApprovalDecisionOutcome(
                    @NotNull EvidenceContext context,
                    @NotNull ApprovalRequest request) {
                return Promise.of("learning-evidence-disabled");
            }
        };
    }

    /**
     * Shared provenance context for lifecycle learning evidence.
     *
     * @param tenantId tenant that owns the evidence
     * @param workspaceId workspace reference
     * @param projectId project reference
     * @param subjectId validation, generation, run, or feedback subject ID
     * @param correlationId request correlation ID
     * @param metadata additional non-sensitive metadata
     */
    record EvidenceContext(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String subjectId,
            String correlationId,
            @NotNull Map<String, Object> metadata
    ) {
        public EvidenceContext {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
