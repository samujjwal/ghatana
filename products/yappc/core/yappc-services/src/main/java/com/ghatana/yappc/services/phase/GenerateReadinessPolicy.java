package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Generate phase readiness policy.
 *
 * @doc.type class
 * @doc.purpose Evaluate Generate phase readiness
 * @doc.layer product
 * @doc.pattern PhaseReadinessPolicy
 */
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
