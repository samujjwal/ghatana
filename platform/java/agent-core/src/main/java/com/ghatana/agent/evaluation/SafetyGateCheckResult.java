/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of safety gate check during evaluation.
 *
 * @doc.type record
 * @doc.purpose Safety gate check result
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record SafetyGateCheckResult(
        boolean passed,
        @NotNull List<String> failedGates,
        @NotNull String reason,
        @NotNull Map<String, String> metadata
) {
    public SafetyGateCheckResult {
        Objects.requireNonNull(failedGates, "failedGates must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        failedGates = List.copyOf(failedGates);
        metadata = Map.copyOf(metadata);
    }
}
