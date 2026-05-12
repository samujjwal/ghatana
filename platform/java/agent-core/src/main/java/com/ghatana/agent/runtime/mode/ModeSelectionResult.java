/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import com.ghatana.agent.runtime.mode.ExecutionMode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Result of mode selection process.
 *
 * @doc.type record
 * @doc.purpose Result of mode selection process
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record ModeSelectionResult(
    @NotNull ExecutionMode selectedMode,
    @NotNull String reasoning
) {
    public ModeSelectionResult {
        Objects.requireNonNull(selectedMode, "Selected mode cannot be null");
        Objects.requireNonNull(reasoning, "Reasoning cannot be null");
    }

    @NotNull
    public ExecutionMode selectedMode() {
        return selectedMode;
    }

    @NotNull
    public String reasoning() {
        return reasoning;
    }
}
