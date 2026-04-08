/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.planning;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown when a {@link PlanCompiler} cannot produce a valid {@link com.ghatana.agent.planning.PlanGraph}
 * from the provided inputs.
 *
 * <p>The exception captures the offending action ID (if known) and a human-readable reason
 * so that callers can surface actionable feedback to operators or end users.
 *
 * @doc.type class
 * @doc.purpose Signals that plan compilation failed due to invalid or unsafe input
 * @doc.layer platform
 * @doc.pattern Exception
 */
public final class PlanCompilationException extends RuntimeException {

    private final String offendingActionId;

    /**
     * Constructs a {@code PlanCompilationException} with a reason and optional offending action.
     *
     * @param reason            human-readable explanation of why compilation failed; must not be null
     * @param offendingActionId action that triggered the failure, or {@code null} if unknown
     */
    public PlanCompilationException(@NotNull String reason, String offendingActionId) {
        super(buildMessage(reason, offendingActionId));
        this.offendingActionId = offendingActionId;
    }

    /**
     * Constructs a {@code PlanCompilationException} without an offending action.
     *
     * @param reason human-readable explanation; must not be null
     */
    public PlanCompilationException(@NotNull String reason) {
        this(reason, null);
    }

    /**
     * Returns the action ID that caused the failure, or {@code null} if not applicable.
     */
    public String offendingActionId() {
        return offendingActionId;
    }

    private static String buildMessage(String reason, String actionId) {
        return actionId == null
                ? "Plan compilation failed: " + reason
                : "Plan compilation failed for action '" + actionId + "': " + reason;
    }
}
