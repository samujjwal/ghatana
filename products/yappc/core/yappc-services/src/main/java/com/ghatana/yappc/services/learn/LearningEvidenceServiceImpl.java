package com.ghatana.yappc.services.learn;

import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.domain.learn.Recommendation;
import com.ghatana.yappc.domain.observe.LogEntry;
import com.ghatana.yappc.domain.observe.Observation;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.run.TaskResult;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.domain.validate.ValidationIssue;
import com.ghatana.yappc.platform.ai.InsightFeedbackService;
import com.ghatana.yappc.common.ServiceObservability;
import com.ghatana.yappc.services.lifecycle.ApprovalRequest;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Default lifecycle outcome evidence recorder for the Learn phase.
 *
 * @doc.type class
 * @doc.purpose Converts validation, generation, run, and feedback outcomes into durable learning evidence
 * @doc.layer service
 * @doc.pattern Service
 */
public final class LearningEvidenceServiceImpl implements LearningEvidenceService {

    private final LearningEvidenceRepository repository;

    /**
     * Creates a service backed by the supplied repository.
     *
     * @param repository durable learning evidence repository
     */
    public LearningEvidenceServiceImpl(@NotNull LearningEvidenceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<String> recordValidationOutcome(
            @NotNull EvidenceContext context,
            @NotNull LifecycleValidationResult result) {
        Objects.requireNonNull(result, "result must not be null");
        Map<String, Object> metadata = metadata(context, "VALIDATION", result.passed() ? "PASSED" : "FAILED");
        metadata.put("validatorVersion", result.validatorVersion());
        metadata.put("issueCount", size(result.issues()));
        metadata.put("blockingIssueCount", result.getBlockingIssues().size());
        metadata.put("issueIds", result.issues().stream().map(ValidationIssue::id).toList());

        String summary = result.passed()
                ? "Validation passed and is available for learning."
                : "Validation failed with blocking or non-blocking issues.";
        return save(context, "validation", summary, List.of("Review validation issues", "Update shape constraints"), metadata);
    }

    @Override
    public Promise<String> recordGenerationOutcome(
            @NotNull EvidenceContext context,
            @NotNull GeneratedArtifacts artifacts) {
        Objects.requireNonNull(artifacts, "artifacts must not be null");
        Map<String, Object> metadata = metadata(context, "GENERATION", "COMPLETED");
        metadata.put("artifactSetId", artifacts.id());
        metadata.put("specRef", artifacts.specRef());
        metadata.put("artifactCount", size(artifacts.artifacts()));
        metadata.put("generatorVersion", artifacts.generatorVersion());
        metadata.put("artifactIds", artifacts.artifacts().stream().map(com.ghatana.yappc.domain.generate.Artifact::id).toList());

        return save(
                context,
                "generation",
                "Generation completed and produced artifacts for downstream learning.",
                List.of("Track generated artifact assurance", "Compare future generation outcomes"),
                metadata);
    }

    @Override
    public Promise<String> recordRunOutcome(
            @NotNull EvidenceContext context,
            @NotNull RunResult result) {
        Objects.requireNonNull(result, "result must not be null");
        String status = result.status() == null ? "UNKNOWN" : result.status().name();
        Map<String, Object> metadata = metadata(context, "RUN", status);
        metadata.put("runId", result.id());
        metadata.put("runSpecRef", result.runSpecRef());
        metadata.put("taskCount", size(result.taskResults()));
        metadata.put("failedTaskIds", failedTaskIds(result));
        metadata.put("startedAt", stringify(result.startedAt()));
        metadata.put("completedAt", stringify(result.completedAt()));
        metadata.put("runMetadata", result.metadata() == null ? Map.of() : Map.copyOf(result.metadata()));

        boolean failed = result.status() == RunStatus.FAILED;
        String summary = failed
                ? "Run failed and produced learning evidence with task failure provenance."
                : "Run completed with status " + status + " and produced learning evidence.";
        List<String> actions = failed
                ? List.of("Inspect failed tasks", "Create regression coverage", "Retry after remediation")
                : List.of("Track run outcome", "Compare future runtime behavior");
        return save(context, "run", summary, actions, metadata);
    }

    @Override
    public Promise<String> recordUserFeedbackOutcome(
            @NotNull EvidenceContext context,
            @NotNull InsightFeedbackService.InsightFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback must not be null");
        Map<String, Object> metadata = metadata(context, "USER_FEEDBACK", feedback.decision().name());
        metadata.put("insightId", feedback.insightId());
        metadata.put("insightType", feedback.insightType().name());
        metadata.put("decision", feedback.decision().name());
        metadata.put("recordedAt", stringify(feedback.recordedAt()));

        return save(
                context,
                "user-feedback",
                "Human feedback recorded for learning threshold and recommendation tuning.",
                List.of("Update feedback statistics", "Review recommendation confidence"),
                metadata);
    }

    @Override
    public Promise<String> recordApprovalDecisionOutcome(
            @NotNull EvidenceContext context,
            @NotNull ApprovalRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, Object> metadata = metadata(context, "APPROVAL_DECISION", request.status().name());
        metadata.put("approvalRequestId", request.id());
        metadata.put("approvalType", request.approvalType().name());
        putIfPresent(metadata, "decidedBy", request.decidedBy());
        putIfPresent(metadata, "decidedAt", stringify(request.decidedAt()));
        putIfPresent(metadata, "createdAt", stringify(request.createdAt()));
        if (request.context() != null) {
            ApprovalRequest.ApprovalContext approvalContext = request.context();
            putIfPresent(metadata, "fromPhase", approvalContext.fromPhase());
            putIfPresent(metadata, "toPhase", approvalContext.toPhase());
            putIfPresent(metadata, "blockReason", approvalContext.blockReason());
            metadata.put("unmetCriteria", approvalContext.unmetCriteria());
            metadata.put("missingArtifacts", approvalContext.missingArtifacts());
            putIfPresent(metadata, "workflowId", approvalContext.workflowId());
            putIfPresent(metadata, "planId", approvalContext.planId());
            putIfPresent(metadata, "priorPlanId", approvalContext.priorPlanId());
            putIfPresent(metadata, "evolutionProposalId", approvalContext.evolutionProposalId());
        }

        boolean approved = request.status() == ApprovalRequest.ApprovalStatus.APPROVED;
        String summary = approved
                ? "Human approval accepted the lifecycle decision and produced learning evidence."
                : "Human approval rejected the lifecycle decision and produced learning evidence.";
        List<String> actions = approved
                ? List.of("Continue approved lifecycle path", "Track approval outcomes")
                : List.of("Review rejection reason", "Update future recommendations");
        return save(context, "approval-decision", summary, actions, metadata);
    }

    private Promise<String> save(
            EvidenceContext context,
            String sourceType,
            String summary,
            List<String> actionItems,
            Map<String, Object> metadata) {
        requireContext(context);
        Map<String, Object> safeMetadata = ServiceObservability.redactSensitiveFields(metadata);
        String evidenceId = "learn-" + sourceType + "-" + safeId(context.subjectId()) + "-" + UUID.randomUUID();
        Observation observation = Observation.builder()
                .id("obs-" + evidenceId)
                .runRef(context.projectId() + ":" + context.subjectId())
                .logs(List.of(LogEntry.builder()
                        .level("INFO")
                        .message(summary)
                        .context(Map.of(
                                "sourceType", sourceType,
                                "subjectId", context.subjectId()))
                        .build()))
                .traces(List.of())
                .build();
        Insights insights = Insights.builder()
                .id("insight-" + evidenceId)
                .observationRef(observation.id())
                .recommendations(List.of(Recommendation.builder()
                        .id("recommendation-" + evidenceId)
                        .type(sourceType)
                        .description(summary)
                        .priority(1)
                        .estimatedImpact(0.5)
                        .actionItems(actionItems)
                        .build()))
                .generatedAt(Instant.now())
                .build();

        List<String> provenance = new ArrayList<>();
        provenance.add(context.subjectId());
        provenance.add(observation.id());
        provenance.add(insights.id());
        Object evidenceIds = safeMetadata.get("evidenceIds");
        if (evidenceIds instanceof List<?> values) {
            values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(value -> !value.isBlank())
                    .forEach(provenance::add);
        }

        LearningEvidenceRepository.LearningEvidence evidence =
                new LearningEvidenceRepository.LearningEvidence(
                        evidenceId,
                        context.tenantId(),
                        context.projectId(),
                        context.projectId() + ":" + context.subjectId(),
                        observation,
                        insights,
                        List.copyOf(provenance),
                        Map.copyOf(safeMetadata),
                        Instant.now());

        return repository.save(evidence).map(ignored -> evidenceId);
    }

    private static Map<String, Object> metadata(EvidenceContext context, String sourceKind, String status) {
        requireContext(context);
        Map<String, Object> metadata = new LinkedHashMap<>(context.metadata());
        metadata.put("sourceKind", sourceKind);
        metadata.put("status", status);
        metadata.put("tenantId", context.tenantId());
        metadata.put("workspaceId", context.workspaceId());
        metadata.put("projectId", context.projectId());
        metadata.put("subjectId", context.subjectId());
        if (context.correlationId() != null && !context.correlationId().isBlank()) {
            metadata.put("correlationId", context.correlationId());
        }
        return metadata;
    }

    private static void requireContext(EvidenceContext context) {
        Objects.requireNonNull(context, "context must not be null");
        requireNonBlank(context.tenantId(), "tenantId");
        requireNonBlank(context.workspaceId(), "workspaceId");
        requireNonBlank(context.projectId(), "projectId");
        requireNonBlank(context.subjectId(), "subjectId");
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required for learning evidence");
        }
    }

    private static List<String> failedTaskIds(RunResult result) {
        if (result.taskResults() == null) {
            return List.of();
        }
        return result.taskResults().stream()
                .filter(task -> task.status() == RunStatus.FAILED)
                .map(TaskResult::taskId)
                .filter(taskId -> taskId != null && !taskId.isBlank())
                .toList();
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static String stringify(Instant value) {
        return value == null ? null : value.toString();
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private static String safeId(String value) {
        String normalized = value == null ? "unknown" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
