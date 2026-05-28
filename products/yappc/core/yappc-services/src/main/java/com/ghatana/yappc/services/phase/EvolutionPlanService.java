package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.List;

final class EvolutionPlanService {

    PhasePacket.EvolutionPlanPanel build(
            PhasePacket.PhaseReadiness readiness,
            List<PhasePacket.PhaseBlocker> blockers,
            List<PhasePacket.ActivityFeedEntry> activityFeed,
            List<PhasePacket.PhaseEvidence> evidence,
            List<PhasePacket.GovernanceRecord> governance
    ) {
        String proposal = activityFeed.isEmpty()
                ? "No evolution proposal is available yet."
                : "Proposal derived from latest lifecycle activity.";
        String diffSummary = activityFeed.isEmpty()
                ? "No diff summary available"
                : activityFeed.get(0).summary();
        String validationRequirements = blockers.isEmpty()
                ? "No additional validation blockers."
                : "Resolve lifecycle blockers before approval.";
        String approvalState = blockers.isEmpty() ? "READY_FOR_REVIEW" : "PENDING_REMEDIATION";
        String rerunTarget = readiness.nextPhase() == null ? "observe" : readiness.nextPhase();

        return new PhasePacket.EvolutionPlanPanel(
                proposal,
                "Evidence: " + evidence.size() + ", Governance: " + governance.size(),
                diffSummary,
                validationRequirements,
                approvalState,
                "Use run rollback controls and regenerate proposal from latest learn signals.",
                rerunTarget
        );
    }
}
