package com.ghatana.yappc.services.phase;

import java.util.List;

public final class GenerateReadinessPolicy implements PhaseReadinessPolicy {
    @Override
    public String phase() {
        return "GENERATE";
    }

    @Override
    public boolean requiresRuntimeHealth() {
        return false;
    }

    @Override
    public void appendMissingPrerequisites(PhaseReadinessInput input, List<String> missingPrerequisites) {
        boolean hasCritical = input.blockers().stream().anyMatch(blocker -> "CRITICAL".equals(blocker.severity()));
        if (hasCritical) {
            missingPrerequisites.add("Generated artifact blockers must be resolved");
        }
    }
}
