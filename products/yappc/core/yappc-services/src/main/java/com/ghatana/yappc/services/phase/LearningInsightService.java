package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.List;

final class LearningInsightService {

    PhasePacket.LearningInsightPanel build(
            PhasePacket.AgentGovernanceHealth governanceHealth,
            List<PhasePacket.ActivityFeedEntry> activityFeed,
            double confidence
    ) {
        String sourceEvent = activityFeed.isEmpty()
                ? "No source event available"
                : activityFeed.get(0).summary();
        boolean approvalRequired = !"approved".equalsIgnoreCase(governanceHealth.governanceState());

        return new PhasePacket.LearningInsightPanel(
                governanceHealth.governanceState(),
                sourceEvent,
                confidence,
                "Approve learning recommendation only when governance evidence is healthy.",
                approvalRequired,
                "Revert to previous approved learning baseline and re-run observe checks."
        );
    }
}
