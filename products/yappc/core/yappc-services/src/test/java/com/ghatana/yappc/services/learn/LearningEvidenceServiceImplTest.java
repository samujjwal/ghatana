package com.ghatana.yappc.services.learn;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.generate.Artifact;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.run.TaskResult;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.domain.validate.ValidationIssue;
import com.ghatana.yappc.platform.ai.InsightFeedbackService;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.services.lifecycle.ApprovalRequest;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests lifecycle outcome learning evidence creation
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("LearningEvidenceServiceImpl")
class LearningEvidenceServiceImplTest extends EventloopTestBase {

    @Test
    @DisplayName("records validation, generation, run, and feedback outcomes with provenance metadata")
    void recordsLifecycleOutcomeEvidence() {
        AtomicReference<LearningEvidenceRepository.LearningEvidence> saved = new AtomicReference<>();
        LearningEvidenceRepository repository = evidence -> {
            saved.set(evidence);
            return Promise.complete();
        };
        LearningEvidenceService service = new LearningEvidenceServiceImpl(repository);
        LearningEvidenceService.EvidenceContext context = new LearningEvidenceService.EvidenceContext(
                "tenant-123",
                "workspace-123",
                "project-123",
                "subject-123",
                "corr-123",
                Map.of("evidenceIds", List.of("evidence-source-1")));

        LifecycleValidationResult validation = LifecycleValidationResult.builder()
                .passed(false)
                .issues(List.of(ValidationIssue.builder()
                        .id("shape-runtime")
                        .severity("error")
                        .category("runtime")
                        .message("Unsupported runtime")
                        .blocking(true)
                        .build()))
                .build();
        String validationEvidenceId = runPromise(() -> service.recordValidationOutcome(context, validation));
        assertThat(validationEvidenceId).startsWith("learn-validation-subject-123-");
        assertThat(saved.get().metadata()).containsEntry("sourceKind", "VALIDATION");
        assertThat(saved.get().metadata()).containsEntry("status", "FAILED");
        assertThat(saved.get().metadata()).containsEntry("blockingIssueCount", 1);
        assertThat(saved.get().provenance()).contains("subject-123", "evidence-source-1");

        GeneratedArtifacts artifacts = GeneratedArtifacts.builder()
                .id("generated-123")
                .specRef("shape-123")
                .artifacts(List.of(Artifact.builder().id("artifact-1").name("App").type("source").build()))
                .build();
        String generationEvidenceId = runPromise(() -> service.recordGenerationOutcome(context, artifacts));
        assertThat(generationEvidenceId).startsWith("learn-generation-subject-123-");
        assertThat(saved.get().metadata()).containsEntry("sourceKind", "GENERATION");
        assertThat(saved.get().metadata()).containsEntry("artifactCount", 1);

        RunResult failedRun = RunResult.builder()
                .id("run-123")
                .runSpecRef("run-spec-123")
                .status(RunStatus.FAILED)
                .taskResults(List.of(TaskResult.builder()
                        .taskId("task-test")
                        .status(RunStatus.FAILED)
                        .error("Tests failed")
                        .build()))
                .completedAt(Instant.parse("2026-05-26T20:30:00Z"))
                .build();
        String runEvidenceId = runPromise(() -> service.recordRunOutcome(context, failedRun));
        assertThat(runEvidenceId).startsWith("learn-run-subject-123-");
        assertThat(saved.get().metadata()).containsEntry("sourceKind", "RUN");
        assertThat(saved.get().metadata()).containsEntry("status", "FAILED");
        assertThat(saved.get().metadata()).containsEntry("failedTaskIds", List.of("task-test"));
        assertThat(saved.get().insights().recommendations()).singleElement().satisfies(recommendation -> {
            assertThat(recommendation.type()).isEqualTo("run");
            assertThat(recommendation.actionItems()).contains("Create regression coverage");
        });

        InsightFeedbackService.InsightFeedback feedback = new InsightFeedbackService.InsightFeedback(
                "tenant-123",
                "insight-123",
                AIInsight.InsightType.CODE_QUALITY,
                InsightFeedbackService.FeedbackDecision.APPROVED,
                Instant.parse("2026-05-26T20:45:00Z"));
        String feedbackEvidenceId = runPromise(() -> service.recordUserFeedbackOutcome(context, feedback));
        assertThat(feedbackEvidenceId).startsWith("learn-user-feedback-subject-123-");
        assertThat(saved.get().metadata()).containsEntry("sourceKind", "USER_FEEDBACK");
        assertThat(saved.get().metadata()).containsEntry("decision", "APPROVED");

        ApprovalRequest approvalRequest = ApprovalRequest.builder()
                .id("approval-123")
                .tenantId("tenant-123")
                .projectId("project-123")
                .requestingAgentId("agent-review")
                .approvalType(ApprovalRequest.ApprovalType.PHASE_ADVANCE)
                .context(new ApprovalRequest.ApprovalContext(
                        "learn",
                        "evolve",
                        "Approve evolution proposal",
                        List.of("human-review"),
                        List.of(),
                        "workspace-123",
                        "plan-123",
                        null,
                        "proposal-123"))
                .status(ApprovalRequest.ApprovalStatus.APPROVED)
                .decidedBy("reviewer-123")
                .decidedAt(Instant.parse("2026-05-26T21:00:00Z"))
                .build();
        String approvalEvidenceId = runPromise(() -> service.recordApprovalDecisionOutcome(context, approvalRequest));
        assertThat(approvalEvidenceId).startsWith("learn-approval-decision-subject-123-");
        assertThat(saved.get().metadata()).containsEntry("sourceKind", "APPROVAL_DECISION");
        assertThat(saved.get().metadata()).containsEntry("status", "APPROVED");
        assertThat(saved.get().metadata()).containsEntry("approvalRequestId", "approval-123");
        assertThat(saved.get().metadata()).containsEntry("evolutionProposalId", "proposal-123");
    }

    @Test
    @DisplayName("redacts prompt input and generated content from learning evidence metadata")
    void redactsSensitiveLearningEvidenceMetadata() {
        AtomicReference<LearningEvidenceRepository.LearningEvidence> saved = new AtomicReference<>();
        LearningEvidenceRepository repository = evidence -> {
            saved.set(evidence);
            return Promise.complete();
        };
        LearningEvidenceService service = new LearningEvidenceServiceImpl(repository);
        LearningEvidenceService.EvidenceContext context = new LearningEvidenceService.EvidenceContext(
                "tenant-123",
                "workspace-123",
                "project-123",
                "subject-123",
                "corr-123",
                Map.of(
                        "prompt", "Build a regulated payments app for a named customer",
                        "input", "customer transcript with private requirements",
                        "generatedContent", "export const secret = 'value';",
                        "inputTokens", 84,
                        "evidenceIds", List.of("evidence-source-1")));

        LifecycleValidationResult validation = LifecycleValidationResult.builder()
                .passed(true)
                .issues(List.of())
                .build();

        runPromise(() -> service.recordValidationOutcome(context, validation));

        assertThat(saved.get().metadata())
                .containsEntry("prompt", "[REDACTED]")
                .containsEntry("input", "[REDACTED]")
                .containsEntry("generatedContent", "[REDACTED]")
                .containsEntry("inputTokens", 84);
        assertThat(saved.get().provenance()).contains("evidence-source-1");
        assertThat(saved.get().metadata().toString())
                .doesNotContain("regulated payments", "customer transcript", "export const secret");
    }
}
