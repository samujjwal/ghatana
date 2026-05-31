package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Intent phase readiness policy.
 *
 * @doc.type class
 * @doc.purpose Evaluate Intent phase readiness
 * @doc.layer product
 * @doc.pattern PhaseReadinessPolicy
 */
public final class IntentReadinessPolicy implements PhaseReadinessPolicy {
    @Override
    public String phase() {
        return "INTENT";
    }

    @Override
    public boolean requiresRuntimeHealth() {
        return false;
    }

    @Override
    public void appendMissingPrerequisites(PhaseReadinessInput input, List<String> missingPrerequisites) {
        if (input.evidence().isEmpty()) {
            missingPrerequisites.add("Intent goals and success criteria");
        }
        if (input.blockers().stream().anyMatch(blocker -> "persona".equalsIgnoreCase(blocker.type()))) {
            missingPrerequisites.add("Intent personas and journeys");
        }
    }
}
