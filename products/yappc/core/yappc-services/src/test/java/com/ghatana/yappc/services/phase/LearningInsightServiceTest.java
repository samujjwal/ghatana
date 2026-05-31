package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LearningInsightService}.
 *
 * @doc.type class
 * @doc.purpose Verifies learning insight panel uses durable learning workflow state
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("LearningInsightService")
class LearningInsightServiceTest {

    @Test
    @DisplayName("build maps source event and approval requirement from workflow state")
    void build_mapsSourceEventAndApprovalRequirementFromWorkflowState() {
        LearningInsightService service = new LearningInsightService();
        LearningWorkflowState workflowState = new LearningWorkflowState(
                "Flaky integration tests in phase run",
                "aep.platform.run.failed",
                0.82,
                "Stabilize retry policy and raise timeout ceiling",
                "PENDING",
                "Rollback to previous stable generation",
                List.of("evidence-1"));

        PhasePacket.LearningInsightPanel panel = service.build(
                workflowState,
                PhasePacket.AgentGovernanceHealth.unknown());

        assertThat(panel.learnedSignal()).isEqualTo("Flaky integration tests in phase run");
        assertThat(panel.sourceEvent()).isEqualTo("aep.platform.run.failed");
        assertThat(panel.confidence()).isEqualTo(0.82);
        assertThat(panel.approvalRequired()).isTrue();
        assertThat(panel.rollbackPath()).isEqualTo("Rollback to previous stable generation");
    }

    @Test
    @DisplayName("build clears approval requirement when workflow state is approved")
    void build_clearsApprovalRequirementWhenApproved() {
        LearningInsightService service = new LearningInsightService();
        LearningWorkflowState workflowState = new LearningWorkflowState(
                "Regression signal cleared",
                "aep.platform.run.completed",
                0.94,
                "Continue rollout",
                "APPROVED",
                "No rollback needed",
                List.of("evidence-2"));

        PhasePacket.LearningInsightPanel panel = service.build(
                workflowState,
                new PhasePacket.AgentGovernanceHealth(true, "healthy", "approved", "none", List.of(), List.of()));

        assertThat(panel.approvalRequired()).isFalse();
    }
}
