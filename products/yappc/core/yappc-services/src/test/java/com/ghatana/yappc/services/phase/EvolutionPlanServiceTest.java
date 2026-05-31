package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EvolutionPlanService}.
 *
 * @doc.type class
 * @doc.purpose Verifies evolution panel maps durable evolution proposal and approval fields
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("EvolutionPlanService")
class EvolutionPlanServiceTest {

    @Test
    @DisplayName("build maps proposal impact diff and approval state")
    void build_mapsProposalImpactDiffAndApprovalState() {
        EvolutionPlanService service = new EvolutionPlanService();
        EvolutionWorkflowState workflowState = new EvolutionWorkflowState(
                "proposal-1",
                "Adopt cached governance checks for run transition",
                "Reduces policy evaluation latency",
                "Adds cache layer for immutable rule snapshots",
                List.of("human-approval", "smoke-test-run"),
                "PENDING_APPROVAL",
                "Rollback cache adapter and restore direct checks",
                "run-retry-target");

        PhasePacket.EvolutionPlanPanel panel = service.build(workflowState);

        assertThat(panel.proposal()).isEqualTo("Adopt cached governance checks for run transition");
        assertThat(panel.impactSummary()).isEqualTo("Reduces policy evaluation latency");
        assertThat(panel.diffSummary()).isEqualTo("Adds cache layer for immutable rule snapshots");
        assertThat(panel.validationRequirements()).isEqualTo("human-approval; smoke-test-run");
        assertThat(panel.approvalState()).isEqualTo("PENDING_APPROVAL");
        assertThat(panel.rollbackPath()).isEqualTo("Rollback cache adapter and restore direct checks");
        assertThat(panel.rerunTarget()).isEqualTo("run-retry-target");
    }
}
