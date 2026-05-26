package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.learn.LearningEvidenceService;
import com.ghatana.yappc.storage.InMemoryEventPublisher;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies approval decisions trigger lifecycle outcome integrations
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("HumanApprovalService decision outcomes")
class HumanApprovalServiceDecisionOutcomeTest extends EventloopTestBase {

    @Test
    @DisplayName("approve changes approval state and records learning/evolve outcome")
    void approveChangesStateAndRecordsOutcome() {
        LearningEvidenceService learningEvidenceService = mock(LearningEvidenceService.class);
        EvolutionService evolutionService = mock(EvolutionService.class);
        when(learningEvidenceService.recordApprovalDecisionOutcome(any(), any()))
                .thenReturn(Promise.of("learn-approval-1"));
        when(evolutionService.approveProposal(eq("proposal-123"), eq("reviewer-1"), any()))
                .thenReturn(Promise.of(new EvolutionService.EvolutionDecision(
                        "proposal-123",
                        "tenant-123",
                        "project-123",
                        "APPROVED",
                        true,
                        List.of("validate", "generate", "run"),
                        "pui-123",
                        java.util.Map.of())));

        HumanApprovalService service = new HumanApprovalService(
                new InMemoryEventPublisher(),
                null,
                null,
                null,
                new ApprovalDecisionOutcomeService(learningEvidenceService, evolutionService));
        ApprovalRequest created = runPromise(() -> service.requestApproval(
                "tenant-123",
                "project-123",
                "agent-123",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                new ApprovalRequest.ApprovalContext(
                        "learn",
                        "evolve",
                        "Approve evolution proposal",
                        List.of("human-review"),
                        List.of(),
                        "workflow-123",
                        "plan-123",
                        null,
                        "proposal-123")));

        ApprovalRequest approved = runPromise(() -> service.approve("tenant-123", created.id(), "reviewer-1"));

        assertThat(approved.status()).isEqualTo(ApprovalRequest.ApprovalStatus.APPROVED);
        assertThat(service.findById("tenant-123", created.id()))
                .get()
                .extracting(ApprovalRequest::status)
                .isEqualTo(ApprovalRequest.ApprovalStatus.APPROVED);
        verify(learningEvidenceService).recordApprovalDecisionOutcome(any(), eq(approved));
        verify(evolutionService).approveProposal(eq("proposal-123"), eq("reviewer-1"), any());
    }
}
