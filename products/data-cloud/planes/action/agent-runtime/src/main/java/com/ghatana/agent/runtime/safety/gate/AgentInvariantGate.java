/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import java.util.Map;
import java.util.Objects;

/**
 * Gate that evaluates pre-dispatch invariants.
 *
 * <p>This gate checks system invariants before allowing agent dispatch,
 * ensuring that the system is in a valid state for execution.
 *
 * @doc.type class
 * @doc.purpose Evaluates pre-dispatch invariants before agent dispatch
 * @doc.layer product
 * @doc.pattern Gate
 */
public final class AgentInvariantGate implements AgentDispatchGate {

    private final InvariantMonitor invariantMonitor;

    public AgentInvariantGate(InvariantMonitor invariantMonitor) {
        this.invariantMonitor = Objects.requireNonNull(invariantMonitor, "invariantMonitor must not be null");
    }

    @Override
    public GateResult evaluate(DispatchContext context) {
        Objects.requireNonNull(context, "context must not be null");

        Map<String, Object> invariantState = invariantMonitor.checkInvariants();

        if (invariantState.containsKey("violations")) {
            @SuppressWarnings("unchecked")
            java.util.List<String> violations = (java.util.List<String>) invariantState.get("violations");
            if (!violations.isEmpty()) {
                return GateResult.failure(
                    "Invariant violations detected: " + String.join(", ", violations));
            }
        }

        if (Boolean.FALSE.equals(invariantState.get("allPassed"))) {
            return GateResult.failure("System invariants not satisfied");
        }

        return GateResult.success();
    }

    /**
     * Interface for monitoring system invariants.
     *
     * <p>This is a placeholder for the actual invariant monitoring logic.
     * A production implementation would query the InvariantMonitor service.
     */
    public interface InvariantMonitor {
        /**
         * Checks all system invariants.
         *
         * @return map containing invariant check results
         */
        Map<String, Object> checkInvariants();
    }
}
