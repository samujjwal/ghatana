/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import java.util.List;

/**
 * Immutable result of an {@link AdvancePhaseUseCase} execution.
 *
 * <p>Use the {@link #isSuccess()} predicate to branch on outcome. On failure, inspect
 * {@link #blockReason()} and {@link #missingArtifacts()} for diagnostics.
 *
 * @param status           overall outcome — {@code "SUCCESS"} or {@code "BLOCKED"}.
 * @param toPhase          the phase that was transitioned to (only set on SUCCESS)
 * @param blockReason      human-readable reason for the block (only set on BLOCKED)
 * @param blockCode        machine-readable block code (e.g., {@code "INVALID_TRANSITION"})
 * @param missingArtifacts list of artifact IDs that are required but absent
 *
 * @doc.type class
 * @doc.purpose Value object representing the result of a lifecycle phase transition
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TransitionResult(
    String status,
    String toPhase,
    String blockReason,
    String blockCode,
    List<String> missingArtifacts
) {

    /** Creates a successful transition result. */
    public static TransitionResult success(String toPhase) {
        return new TransitionResult("SUCCESS", toPhase, null, null, List.of());
    }

    /** Creates a blocked result with a code and human reason. */
    public static TransitionResult blocked(String blockCode, String blockReason) {
        return new TransitionResult("BLOCKED", null, blockReason, blockCode, List.of());
    }

    /** Creates a blocked result due to missing artifacts. */
    public static TransitionResult missingArtifacts(List<String> artifactIds) {
        return new TransitionResult(
            "BLOCKED", null,
            "Required artifacts not present: " + artifactIds,
            "MISSING_ARTIFACT",
            List.copyOf(artifactIds));
    }

    /** {@code true} iff the transition was accepted and applied. */
    public boolean isSuccess() { return "SUCCESS".equals(status); }
}
