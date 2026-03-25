/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default {@link InvariantMonitor} implementation with built-in safety invariants.
 *
 * <p>Ships with four foundational invariants:
 * <ul>
 *   <li><b>Budget cap</b>: accumulated cost &le; declared cap</li>
 *   <li><b>Delegation depth</b>: depth &le; max allowed</li>
 *   <li><b>Action budget</b>: actions executed &le; max per turn</li>
 *   <li><b>Turn timeout</b>: elapsed time &le; max duration</li>
 * </ul>
 *
 * <p>Additional invariants can be registered via {@link #register(InvariantRule)}.
 *
 * @doc.type class
 * @doc.purpose Default invariant monitor with built-in safety checks
 * @doc.layer agent-runtime
 * @doc.pattern Composite, Strategy
 */
public class DefaultInvariantMonitor implements InvariantMonitor {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvariantMonitor.class);

    private final List<InvariantRule> rules = new CopyOnWriteArrayList<>();

    /**
     * Creates a monitor pre-loaded with built-in safety invariants.
     */
    public DefaultInvariantMonitor() {
        rules.add(new BudgetCapInvariant());
        rules.add(new DelegationDepthInvariant());
        rules.add(new ActionBudgetInvariant());
        rules.add(new TurnTimeoutInvariant());
    }

    @Override
    @NotNull
    public List<InvariantViolation> evaluate(@NotNull InvariantContext context) {
        List<InvariantViolation> violations = new ArrayList<>();

        for (InvariantRule rule : rules) {
            Optional<String> result = rule.evaluate(context);
            if (result.isPresent()) {
                InvariantViolation violation = new InvariantViolation(
                        UUID.randomUUID().toString(),
                        rule.getId(),
                        context.agentId(),
                        context.tenantId(),
                        rule.getSeverity(),
                        result.get(),
                        determineResponse(rule.getSeverity()),
                        Map.of(
                                "traceId", context.traceId(),
                                "invariantDescription", rule.getDescription()),
                        Instant.now());
                violations.add(violation);
                log.warn("Invariant violated: {} — {} (severity={})",
                        rule.getId(), result.get(), rule.getSeverity());
            }
        }

        return List.copyOf(violations);
    }

    @Override
    public void register(@NotNull InvariantRule rule) {
        Objects.requireNonNull(rule, "rule");
        rules.add(rule);
        log.info("Registered invariant rule: {} (severity={})", rule.getId(), rule.getSeverity());
    }

    @Override
    @NotNull
    public List<InvariantRule> getRules() {
        return List.copyOf(rules);
    }

    private InvariantViolation.ResponseAction determineResponse(InvariantViolation.Severity severity) {
        return switch (severity) {
            case INFO -> InvariantViolation.ResponseAction.LOGGED;
            case WARNING -> InvariantViolation.ResponseAction.LOGGED;
            case CRITICAL -> InvariantViolation.ResponseAction.TURN_TERMINATED;
            case FATAL -> InvariantViolation.ResponseAction.KILL_SWITCH_ACTIVATED;
        };
    }

    // =========================================================================
    // Built-in invariant rules
    // =========================================================================

    private static final class BudgetCapInvariant implements InvariantRule {
        @Override public @NotNull String getId() { return "safety.budget-cap"; }
        @Override public @NotNull String getDescription() { return "Accumulated cost must not exceed cost cap"; }
        @Override public @NotNull InvariantViolation.Severity getSeverity() { return InvariantViolation.Severity.CRITICAL; }

        @Override
        public @NotNull Optional<String> evaluate(@NotNull InvariantContext ctx) {
            if (ctx.costCapUsd() > 0 && ctx.accumulatedCostUsd() > ctx.costCapUsd()) {
                return Optional.of(String.format(
                        "Budget exceeded: $%.4f > cap $%.4f",
                        ctx.accumulatedCostUsd(), ctx.costCapUsd()));
            }
            return Optional.empty();
        }
    }

    private static final class DelegationDepthInvariant implements InvariantRule {
        @Override public @NotNull String getId() { return "safety.delegation-depth"; }
        @Override public @NotNull String getDescription() { return "Delegation chain depth must not exceed maximum"; }
        @Override public @NotNull InvariantViolation.Severity getSeverity() { return InvariantViolation.Severity.CRITICAL; }

        @Override
        public @NotNull Optional<String> evaluate(@NotNull InvariantContext ctx) {
            if (ctx.maxDelegationDepth() > 0 && ctx.delegationDepth() > ctx.maxDelegationDepth()) {
                return Optional.of(String.format(
                        "Delegation depth %d exceeds max %d",
                        ctx.delegationDepth(), ctx.maxDelegationDepth()));
            }
            return Optional.empty();
        }
    }

    private static final class ActionBudgetInvariant implements InvariantRule {
        @Override public @NotNull String getId() { return "safety.action-budget"; }
        @Override public @NotNull String getDescription() { return "Actions per turn must not exceed budget"; }
        @Override public @NotNull InvariantViolation.Severity getSeverity() { return InvariantViolation.Severity.WARNING; }

        @Override
        public @NotNull Optional<String> evaluate(@NotNull InvariantContext ctx) {
            if (ctx.maxActionsPerTurn() > 0 && ctx.actionsExecuted() > ctx.maxActionsPerTurn()) {
                return Optional.of(String.format(
                        "Action count %d exceeds budget %d",
                        ctx.actionsExecuted(), ctx.maxActionsPerTurn()));
            }
            return Optional.empty();
        }
    }

    private static final class TurnTimeoutInvariant implements InvariantRule {
        @Override public @NotNull String getId() { return "safety.turn-timeout"; }
        @Override public @NotNull String getDescription() { return "Agent turn duration must not exceed timeout"; }
        @Override public @NotNull InvariantViolation.Severity getSeverity() { return InvariantViolation.Severity.CRITICAL; }

        @Override
        public @NotNull Optional<String> evaluate(@NotNull InvariantContext ctx) {
            if (ctx.maxTurnDurationSeconds() > 0) {
                long elapsed = Duration.between(ctx.turnStartedAt(), Instant.now()).getSeconds();
                if (elapsed > ctx.maxTurnDurationSeconds()) {
                    return Optional.of(String.format(
                            "Turn duration %ds exceeds max %ds",
                            elapsed, ctx.maxTurnDurationSeconds()));
                }
            }
            return Optional.empty();
        }
    }
}
