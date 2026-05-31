package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Strategy contract for phase-native readiness semantics.
 *
 * @doc.type interface
 * @doc.purpose Define phase readiness policy contract
 * @doc.layer product
 * @doc.pattern PhaseReadinessPolicy
 */
public interface PhaseReadinessPolicy {

    String phase();

    boolean requiresRuntimeHealth();

    default void appendMissingPrerequisites(PhaseReadinessInput input, List<String> missingPrerequisites) {
        // Default policy keeps generic readiness only.
    }
}
