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
 * <p>Includes governance requirements:
 * <ul>
 *   <li><b>requiresApproval</b>: Whether the selected mode requires human approval before execution</li>
 *   <li><b>requiresVerification</b>: Whether the selected mode requires verification proof after execution</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose Result of mode selection process
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record ModeSelectionResult(
    @NotNull ExecutionMode selectedMode,
    @NotNull String reasoning,
    boolean requiresApproval,
    boolean requiresVerification
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

    /**
     * Returns true if the selected mode requires human approval before execution.
     *
     * @return true if approval is required
     */
    public boolean requiresApproval() {
        return requiresApproval;
    }

    /**
     * Returns true if the selected mode requires verification proof after execution.
     *
     * @return true if verification is required
     */
    public boolean requiresVerification() {
        return requiresVerification;
    }
}
