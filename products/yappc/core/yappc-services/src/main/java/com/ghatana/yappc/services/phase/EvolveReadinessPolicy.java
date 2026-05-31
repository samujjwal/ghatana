package com.ghatana.yappc.services.phase;

import java.util.List;

public final class EvolveReadinessPolicy implements PhaseReadinessPolicy {
    @Override
    public String phase() {
        return "EVOLVE";
    }

    @Override
    public boolean requiresRuntimeHealth() {
        return false;
    }

    @Override
    public void appendMissingPrerequisites(PhaseReadinessInput input, List<String> missingPrerequisites) {
        if (input.governance().isEmpty()) {
            missingPrerequisites.add("Evolution approval decision");
        }
    }
}
