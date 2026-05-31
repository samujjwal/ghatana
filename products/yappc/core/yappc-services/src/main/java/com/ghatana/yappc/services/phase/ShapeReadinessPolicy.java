package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Shape phase readiness policy.
 *
 * @doc.type class
 * @doc.purpose Evaluate Shape phase readiness
 * @doc.layer product
 * @doc.pattern PhaseReadinessPolicy
 */
public final class ShapeReadinessPolicy implements PhaseReadinessPolicy {
    @Override
    public String phase() {
        return "SHAPE";
    }

    @Override
    public boolean requiresRuntimeHealth() {
        return false;
    }

    @Override
    public void appendMissingPrerequisites(PhaseReadinessInput input, List<String> missingPrerequisites) {
        boolean outOfSync = input.projectState().getOrDefault("canvasSyncStatus", "in-sync") instanceof String status
                && "out-of-sync".equalsIgnoreCase(status);
        if (outOfSync) {
            missingPrerequisites.add("Canvas and architecture model must be in sync");
        }
        if (input.activityFeed().isEmpty()) {
            missingPrerequisites.add("Selected shape surfaces");
        }
    }
}
