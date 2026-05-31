package com.ghatana.yappc.services.phase;

/**
 * Run phase readiness policy.
 *
 * @doc.type class
 * @doc.purpose Evaluate Run phase readiness
 * @doc.layer product
 * @doc.pattern PhaseReadinessPolicy
 */
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
