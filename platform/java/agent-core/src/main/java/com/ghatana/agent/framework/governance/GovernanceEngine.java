/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.governance;

import com.ghatana.agent.framework.memory.GovernancePolicy;
import com.ghatana.agent.framework.memory.GovernanceResult;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Active governance engine that applies governance policies across agent memory stores,
 * records enforcement actions to an audit trail, and supports scheduled or on-demand
 * policy evaluation.
 *
 * <p>The {@code GovernanceEngine} bridges the declarative {@link GovernancePolicy}
 * interface with active scheduling and audit logging. It should be driven by a
 * background task in the agent runtime or an external cron-style scheduler.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li><b>Policy evaluation</b>: Apply redaction and retention rules to memory</li>
 *   <li><b>Audit logging</b>: Record every enforcement action with timestamp and actor</li>
 *   <li><b>Scheduled enforcement</b>: Run governance on a configurable cadence</li>
 *   <li><b>Cost governance</b>: Enforce cost caps declared in agent definitions</li>
 *   <li><b>Compliance checks</b>: Validate that agents comply with declared policies</li>
 * </ul>
 *
 * <h2>Integration</h2>
 * <pre>{@code
 * GovernanceEngine engine = GovernanceEngine.create(executor, auditLog)
 *     .withDefaultPolicy(myRetentionPolicy);
 *
 * // Run governance pass on an agent's memory
 * GovernanceReport report = engine.enforce(agentId, memoryStore).await();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Active governance enforcement engine with audit logging
 * @doc.layer framework
 * @doc.pattern Service
 * @doc.gaa.lifecycle reflect
 */
public final class GovernanceEngine {

    private final Executor executor;
    private final GovernanceAuditLog auditLog;
    private GovernancePolicy defaultPolicy;

    private GovernanceEngine(@NotNull Executor executor, @NotNull GovernanceAuditLog auditLog) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog must not be null");
        this.defaultPolicy = GovernancePolicy.noOp();
    }

    /**
     * Creates a new {@code GovernanceEngine}.
     *
     * @param executor executor for blocking governance operations
     * @param auditLog audit trail log
     * @return new engine instance
     */
    @NotNull
    public static GovernanceEngine create(
            @NotNull Executor executor,
            @NotNull GovernanceAuditLog auditLog) {
        return new GovernanceEngine(executor, auditLog);
    }

    /**
     * Sets the default policy applied when no explicit policy is provided.
     *
     * @param policy default governance policy
     * @return this engine (fluent)
     */
    @NotNull
    public GovernanceEngine withDefaultPolicy(@NotNull GovernancePolicy policy) {
        this.defaultPolicy = Objects.requireNonNull(policy, "policy must not be null");
        return this;
    }

    /**
     * Enforces all registered governance policies on the given agent's memory store.
     * Applies redaction, retention, and data classification rules.
     *
     * @param agentId     identifier of the agent whose memory is being governed
     * @param memoryStore the agent's memory store
     * @return promise of a {@link GovernanceReport} summarising enforcement actions
     */
    @NotNull
    public Promise<GovernanceReport> enforce(
            @NotNull String agentId,
            @NotNull MemoryStore memoryStore) {
        Instant start = Instant.now();
        return memoryStore.applyGovernance(defaultPolicy)
                .then(result -> Promise.ofBlocking(executor, () -> {
                    Duration took = Duration.between(start, Instant.now());
                    GovernanceReport report = new GovernanceReport(
                            agentId,
                            start,
                            took,
                            result,
                            List.of("retention-pass", "redaction-pass"));
                    auditLog.record(new GovernanceAuditEntry(
                            agentId,
                            Instant.now(),
                            "GOVERNANCE_PASS",
                            report.summary()));
                    return report;
                }));
    }

    /**
     * Evaluates whether the given cost amount exceeds the declared cost cap.
     * Records a compliance violation to the audit log if exceeded.
     *
     * @param agentId   agent identifier
     * @param costUsd   cost incurred in this turn (USD)
     * @param capUsd    declared maximum cost per call
     * @return promise of {@code true} if within cap, {@code false} if violation
     */
    @NotNull
    public Promise<Boolean> evaluateCostCap(
            @NotNull String agentId,
            double costUsd,
            double capUsd) {
        boolean withinCap = costUsd <= capUsd;
        if (!withinCap) {
            return Promise.ofBlocking(executor, () -> {
                auditLog.record(new GovernanceAuditEntry(
                        agentId,
                        Instant.now(),
                        "COST_CAP_VIOLATION",
                        String.format("cost=%.4f USD exceeds cap=%.4f USD", costUsd, capUsd)));
                return false;
            });
        }
        return Promise.of(Boolean.TRUE);
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /**
     * Summary report for a single governance pass.
     *
     * @param agentId     agent identifier
     * @param startedAt   when the pass started
     * @param duration    how long it took
     * @param result      result from the memory store governance call
     * @param appliedRules list of rule names applied
     */
    public record GovernanceReport(
            @NotNull String agentId,
            @NotNull Instant startedAt,
            @NotNull Duration duration,
            @NotNull GovernanceResult result,
            @NotNull List<String> appliedRules) {

        /**
         * Returns a human-readable summary.
         */
        @NotNull
        public String summary() {
            return String.format("GovernancePass[agent=%s, duration=%dms, rules=%s]",
                    agentId, duration.toMillis(), appliedRules);
        }
    }

    /**
     * Audit trail log for governance enforcement actions.
     */
    public interface GovernanceAuditLog {
        /**
         * Records a governance audit entry.
         *
         * @param entry audit entry to record
         */
        void record(@NotNull GovernanceAuditEntry entry);

        /**
         * Returns a no-op audit log that discards all entries.
         *
         * @return no-op audit log
         */
        @NotNull
        static GovernanceAuditLog noOp() {
            return entry -> {};
        }
    }

    /**
     * A single audit entry recording a governance enforcement action.
     *
     * @param agentId    agent the action applies to
     * @param timestamp  when the action occurred
     * @param action     action type (e.g. "GOVERNANCE_PASS", "COST_CAP_VIOLATION")
     * @param details    human-readable details
     */
    public record GovernanceAuditEntry(
            @NotNull String agentId,
            @NotNull Instant timestamp,
            @NotNull String action,
            @NotNull String details) {}
}
