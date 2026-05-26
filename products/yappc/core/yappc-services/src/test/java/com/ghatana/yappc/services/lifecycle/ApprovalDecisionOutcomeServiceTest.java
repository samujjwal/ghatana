package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.learn.LearningEvidenceService;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies human approval decisions feed Learn evidence and Evolve state
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("ApprovalDecisionOutcomeService")
class ApprovalDecisionOutcomeServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("records learning evidence and approves explicit evolution proposal")
    void recordsLearningEvidenceAndApprovesEvolutionProposal() {
        LearningEvidenceService learningEvidenceService = mock(LearningEvidenceService.class);
        EvolutionService evolutionService = mock(EvolutionService.class);
        when(learningEvidenceService.recordApprovalDecisionOutcome(any(), any()))
                .thenReturn(Promise.of("learn-approval-1"));
        when(evolutionService.approveProposal(eq("proposal-123"), eq("reviewer-1"), any()))
                .thenReturn(Promise.of(decision("APPROVED", true)));

        ApprovalDecisionOutcomeService service =
                new ApprovalDecisionOutcomeService(learningEvidenceService, evolutionService);

        ApprovalRequest approved = request(ApprovalRequest.ApprovalStatus.APPROVED);
        runPromise(() -> service.recordDecision(approved));

        ArgumentCaptor<LearningEvidenceService.EvidenceContext> contextCaptor =
                ArgumentCaptor.forClass(LearningEvidenceService.EvidenceContext.class);
        verify(learningEvidenceService).recordApprovalDecisionOutcome(contextCaptor.capture(), eq(approved));
        assertThat(contextCaptor.getValue().tenantId()).isEqualTo("tenant-123");
        assertThat(contextCaptor.getValue().workspaceId()).isEqualTo("workflow-123");
        assertThat(contextCaptor.getValue().metadata()).containsEntry("evolutionProposalId", "proposal-123");
        verify(evolutionService).approveProposal(
                eq("proposal-123"),
                eq("reviewer-1"),
                org.mockito.ArgumentMatchers.contains("learningEvidenceId=learn-approval-1"));
    }

    @Test
    @DisplayName("records learning evidence and rejects explicit evolution proposal")
    void recordsLearningEvidenceAndRejectsEvolutionProposal() {
        LearningEvidenceService learningEvidenceService = mock(LearningEvidenceService.class);
        EvolutionService evolutionService = mock(EvolutionService.class);
        when(learningEvidenceService.recordApprovalDecisionOutcome(any(), any()))
                .thenReturn(Promise.of("learn-approval-2"));
        when(evolutionService.rejectProposal(eq("proposal-123"), eq("reviewer-1"), any()))
                .thenReturn(Promise.of(decision("REJECTED", false)));

        ApprovalDecisionOutcomeService service =
                new ApprovalDecisionOutcomeService(learningEvidenceService, evolutionService);

        ApprovalRequest rejected = request(ApprovalRequest.ApprovalStatus.REJECTED);
        runPromise(() -> service.recordDecision(rejected));

        verify(learningEvidenceService).recordApprovalDecisionOutcome(any(), eq(rejected));
        verify(evolutionService).rejectProposal(
                eq("proposal-123"),
                eq("reviewer-1"),
                org.mockito.ArgumentMatchers.contains("learningEvidenceId=learn-approval-2"));
    }

    @Test
    @DisplayName("does not call evolve when approval is not bound to a proposal")
    void skipsEvolutionWhenNoProposalIsBound() {
        LearningEvidenceService learningEvidenceService = mock(LearningEvidenceService.class);
        EvolutionService evolutionService = mock(EvolutionService.class);
        when(learningEvidenceService.recordApprovalDecisionOutcome(any(), any()))
                .thenReturn(Promise.of("learn-approval-3"));

        ApprovalDecisionOutcomeService service =
                new ApprovalDecisionOutcomeService(learningEvidenceService, evolutionService);

        ApprovalRequest request = ApprovalRequest.builder()
                .id("approval-123")
                .tenantId("tenant-123")
                .projectId("project-123")
                .approvalType(ApprovalRequest.ApprovalType.PHASE_ADVANCE)
                .context(new ApprovalRequest.ApprovalContext(
                        "validate",
                        "generate",
                        "Manual phase approval",
                        List.of(),
                        List.of(),
                        "workflow-123",
                        "plan-123",
                        null,
                        null))
                .status(ApprovalRequest.ApprovalStatus.APPROVED)
                .decidedBy("reviewer-1")
                .build();
        runPromise(() -> service.recordDecision(request));

        verify(learningEvidenceService).recordApprovalDecisionOutcome(any(), eq(request));
        verifyNoInteractions(evolutionService);
    }

    private static ApprovalRequest request(ApprovalRequest.ApprovalStatus status) {
        return ApprovalRequest.builder()
                .id("approval-123")
                .tenantId("tenant-123")
                .projectId("project-123")
                .requestingAgentId("agent-123")
                .approvalType(ApprovalRequest.ApprovalType.PHASE_ADVANCE)
                .context(new ApprovalRequest.ApprovalContext(
                        "learn",
                        "evolve",
                        "Approve evolution proposal",
                        List.of("human-review"),
                        List.of(),
                        "workflow-123",
                        "plan-123",
                        null,
                        "proposal-123"))
                .status(status)
                .decidedBy("reviewer-1")
                .build();
    }

    private static EvolutionService.EvolutionDecision decision(String status, boolean shouldExecute) {
        return new EvolutionService.EvolutionDecision(
                "proposal-123",
                "tenant-123",
                "project-123",
                status,
                shouldExecute,
                shouldExecute ? List.of("validate", "generate", "run") : List.of(),
                shouldExecute ? "pui-123" : null,
                java.util.Map.of());
    }
}
