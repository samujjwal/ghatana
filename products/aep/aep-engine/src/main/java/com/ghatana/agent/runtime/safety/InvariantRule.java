/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * A single invariant rule that can be evaluated against execution context.
 *
 * @doc.type interface
 * @doc.purpose Individual invariant rule for runtime safety evaluation
 * @doc.layer agent-runtime
 * @doc.pattern Strategy
 */
public interface InvariantRule {

    /**
     * Unique identifier for this invariant.
     *
     * @return invariant ID
     */
    @NotNull
    String getId();

    /**
     * Human-readable description of what this invariant checks.
     *
     * @return description
     */
    @NotNull
    String getDescription();

    /**
     * The severity of a violation of this invariant.
     *
     * @return severity level
     */
    @NotNull
    InvariantViolation.Severity getSeverity();

    /**
     * Evaluates this invariant against the given context.
     *
     * @param context the current execution context
     * @return empty if the invariant holds, or a violation description if broken
     */
    @NotNull
    Optional<String> evaluate(@NotNull InvariantContext context);
}
