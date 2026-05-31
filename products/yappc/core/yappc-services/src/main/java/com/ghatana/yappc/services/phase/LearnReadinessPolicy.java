package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Learn phase readiness policy.
 *
 * @doc.type class
 * @doc.purpose Evaluate Learn phase readiness
 * @doc.layer product
 * @doc.pattern PhaseReadinessPolicy
 */
public final class LearnReadinessPolicy implements PhaseReadinessPolicy {
    @Override
    public String phase() {
        return "LEARN";
    }

    @Override
    public boolean requiresRuntimeHealth() {
        return false;
    }

    @Override
    public void appendMissingPrerequisites(PhaseReadinessInput input, List<String> missingPrerequisites) {
        if (input.healthSignals().agentGovernance().evidenceIds().isEmpty()) {
            missingPrerequisites.add("Learning signal evidence");
        }
    }
}
