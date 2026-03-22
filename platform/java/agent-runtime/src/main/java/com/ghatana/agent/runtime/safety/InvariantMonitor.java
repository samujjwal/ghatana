/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Monitor that evaluates runtime invariants before and after agent actions.
 *
 * <p>Invariants are conditions that must remain true throughout an agent's
 * lifecycle. Examples include:
 * <ul>
 *   <li>Budget constraints: total cost must not exceed $X per turn</li>
 *   <li>Delegation depth: delegation chain length &le; N</li>
 *   <li>Output constraints: no PII in responses for certain classifications</li>
 *   <li>Temporal constraints: agent turn duration &le; T seconds</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Runtime invariant monitoring SPI
 * @doc.layer agent-runtime
 * @doc.pattern Strategy
 */
public interface InvariantMonitor {

    /**
     * Evaluates all registered invariants against the current execution context.
     *
     * @param context the current execution context to evaluate
     * @return list of violations (empty if all invariants hold)
     */
    @NotNull
    List<InvariantViolation> evaluate(@NotNull InvariantContext context);

    /**
     * Registers a new invariant rule.
     *
     * @param rule the invariant rule to register
     */
    void register(@NotNull InvariantRule rule);

    /**
     * Returns all registered invariant rules.
     *
     * @return unmodifiable view of registered rules
     */
    @NotNull
    List<InvariantRule> getRules();
}
