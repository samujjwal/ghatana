/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.safety;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Policy defining safety constraints for agent execution and evaluation.
 *
 * <p>A {@code SafetyPolicy} declares:
 * <ul>
 *   <li>Forbidden tools - tools that must never be used</li>
 *   <li>Forbidden patterns - regex patterns that must never appear in inputs/outputs</li>
 *   <li>Required flags - safety flags that must be present and true</li>
 *   <li>Side effect restrictions - whether side effects are allowed</li>
 *   <li>Prompt injection patterns - patterns that indicate prompt injection attempts</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose Policy defining safety constraints
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public record SafetyPolicy(
        @NotNull String policyId,
        @NotNull String tenantId,
        @NotNull String name,
        @NotNull String description,
        @NotNull Set<String> forbiddenTools,
        @NotNull List<String> forbiddenPatterns,
        @NotNull Map<String, String> requiredFlags,
        boolean allowSideEffects,
        @NotNull List<String> promptInjectionPatterns,
        boolean active
) {
    public SafetyPolicy {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(forbiddenTools, "forbiddenTools must not be null");
        Objects.requireNonNull(forbiddenPatterns, "forbiddenPatterns must not be null");
        Objects.requireNonNull(requiredFlags, "requiredFlags must not be null");
        Objects.requireNonNull(promptInjectionPatterns, "promptInjectionPatterns must not be null");
        forbiddenTools = Set.copyOf(forbiddenTools);
        forbiddenPatterns = List.copyOf(forbiddenPatterns);
        requiredFlags = Map.copyOf(requiredFlags);
        promptInjectionPatterns = List.copyOf(promptInjectionPatterns);
    }

    /**
     * Creates a default safety policy with common constraints.
     *
     * @param tenantId tenant ID
     * @return default safety policy
     */
    @NotNull
    public static SafetyPolicy defaultPolicy(@NotNull String tenantId) {
        return new SafetyPolicy(
                "default",
                tenantId,
                "Default Safety Policy",
                "Default safety constraints for agent execution",
                Set.of("exec", "eval", "system", "shell", "rm", "delete_file", "drop_table", "truncate_table"),
                List.of(),
                Map.of(),
                false,
                List.of(
                        "ignore previous instructions",
                        "ignore all previous",
                        "disregard all instructions",
                        "new system prompt",
                        "you are now",
                        "forget everything",
                        "act as if",
                        "jailbreak"
                ),
                true
        );
    }
}
