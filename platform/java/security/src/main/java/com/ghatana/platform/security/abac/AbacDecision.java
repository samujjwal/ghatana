package com.ghatana.platform.security.abac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of an ABAC authorization evaluation.
 *
 * @doc.type record
 * @doc.purpose ABAC authorization decision
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record AbacDecision(
    /** Whether access is permitted */
    boolean permitted,
    /** Human-readable reason for the decision */
    @NotNull String reason,
    /** The policy that made the decision (null if no matching policy) */
    @Nullable String matchedPolicyId
) {
    public static AbacDecision permit(@NotNull String reason, @NotNull String policyId) {
        return new AbacDecision(true, reason, policyId);
    }

    public static AbacDecision deny(@NotNull String reason, @Nullable String policyId) {
        return new AbacDecision(false, reason, policyId);
    }

    public static AbacDecision deny(@NotNull String reason) {
        return new AbacDecision(false, reason, null);
    }
}
