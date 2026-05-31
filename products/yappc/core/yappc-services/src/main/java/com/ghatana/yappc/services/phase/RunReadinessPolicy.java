package com.ghatana.yappc.services.phase;

public final class RunReadinessPolicy implements PhaseReadinessPolicy {
    @Override
    public String phase() {
        return "RUN";
    }

    @Override
    public boolean requiresRuntimeHealth() {
        return true;
    }
}
