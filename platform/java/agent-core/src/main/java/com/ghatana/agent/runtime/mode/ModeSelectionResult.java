/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Result of mode selection process.
 *
 * <p>Carries the selected execution strategy, supervision mode, human-oversight flags,
 * and the reasoning that produced this decision. Both {@code requiresApproval} and
 * {@code requiresVerification} are preserved from the inner policy result so that
 * callers can gate execution appropriately.
 *
 * @doc.type record
 * @doc.purpose Result of mode selection process
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record ModeSelectionResult(
    @NotNull ExecutionStrategy strategy,
    @NotNull SupervisionMode supervision,
    @NotNull String reasoning,
    boolean requiresApproval,
    boolean requiresVerification
) {
    public ModeSelectionResult {
        Objects.requireNonNull(strategy, "strategy cannot be null");
        Objects.requireNonNull(supervision, "supervision cannot be null");
        Objects.requireNonNull(reasoning, "reasoning cannot be null");
    }

    /**
     * Fully autonomous execution with no approval or verification gates.
     *
     * @param strategy  execution strategy
     * @param reasoning selection reasoning
     * @return mode selection result
     */
    @NotNull
    public static ModeSelectionResult autonomous(@NotNull ExecutionStrategy strategy, @NotNull String reasoning) {
        return new ModeSelectionResult(strategy, SupervisionMode.AUTONOMOUS, reasoning, false, false);
    }

    /**
     * Supervised execution with verification required but no explicit approval gate.
     *
     * @param strategy  execution strategy
     * @param reasoning selection reasoning
     * @return mode selection result
     */
    @NotNull
    public static ModeSelectionResult supervised(@NotNull ExecutionStrategy strategy, @NotNull String reasoning) {
        return new ModeSelectionResult(strategy, SupervisionMode.SUPERVISED, reasoning, false, true);
    }

    /**
     * Human-gated execution requiring explicit approval before acting.
     *
     * @param strategy  execution strategy
     * @param reasoning selection reasoning
     * @return mode selection result
     */
    @NotNull
    public static ModeSelectionResult humanGated(@NotNull ExecutionStrategy strategy, @NotNull String reasoning) {
        return new ModeSelectionResult(strategy, SupervisionMode.HUMAN_GATED, reasoning, true, false);
    }

    /**
     * Blocked execution — agent must not proceed.
     *
     * @param reasoning selection reasoning
     * @return mode selection result
     */
    @NotNull
    public static ModeSelectionResult blocked(@NotNull String reasoning) {
        return new ModeSelectionResult(ExecutionStrategy.VERIFICATION_FIRST, SupervisionMode.BLOCKED, reasoning, false, false);
    }
}
