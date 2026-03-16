/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SPI for registering and resolving WAIT steps in durable workflows.
 *
 * <p>Wait steps pause workflow execution until an external signal arrives, a timer
 * fires, or manual approval is given. Implementations may use:
 * <ul>
 *   <li>PostgreSQL polling ({@code JdbcWorkflowWaitCoordinator} in {@code workflow-jdbc})</li>
 *   <li>AEP event subscriptions (for event-driven resume without polling)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose SPI for workflow WAIT step coordination (signals, timers, approvals)
 * @doc.layer core
 * @doc.pattern Coordinator
 */
public interface WorkflowWaitCoordinator {

    /**
     * Registers a wait condition for a workflow run.
     *
     * @param runId     the workflow run that is waiting
     * @param condition the condition that must be satisfied to resume
     * @return a promise that completes when the wait is registered
     */
    Promise<Void> registerWait(@NotNull String runId, @NotNull WaitCondition condition);

    /**
     * Signals a waiting workflow run to resume.
     *
     * @param runId      the workflow run to signal
     * @param signalName the name of the signal (must match the registered wait)
     * @param payload    optional data to inject into the workflow context on resume
     * @return a promise resolving to {@code true} if the signal was accepted
     */
    Promise<Boolean> signal(
            @NotNull String runId,
            @NotNull String signalName,
            @Nullable Map<String, Object> payload);

    /**
     * Finds all waits whose fire_at has passed (for timer-based resume).
     *
     * @param now the current time
     * @return a promise resolving to run IDs ready to fire
     */
    Promise<List<String>> findFirableWaits(@NotNull Instant now);

    /**
     * Cancels a registered wait (e.g. when the workflow is cancelled).
     *
     * @param runId the workflow run whose wait to cancel
     */
    void cancel(@NotNull String runId);

    // ── Wait Condition ──────────────────────────────────────────────────

    /**
     * Describes what a WAIT step is waiting for.
     *
     * @doc.type record
     * @doc.purpose Wait condition specification for WAIT workflow steps
     * @doc.layer core
     * @doc.pattern ValueObject
     */
    record WaitCondition(
            @NotNull WaitKind kind,
            @Nullable String eventType,
            @Nullable String correlationKey,
            @Nullable Duration timeout,
            @Nullable Instant fireAt
    ) {
        public WaitCondition {
            Objects.requireNonNull(kind, "kind");
        }

        /** Wait for an external event with a specific type and correlation key. */
        public static WaitCondition forEvent(@NotNull String eventType, @NotNull String correlationKey) {
            return new WaitCondition(WaitKind.EVENT, eventType, correlationKey, null, null);
        }

        /** Wait for a duration, then auto-resume. */
        public static WaitCondition forTimer(@NotNull Duration timeout) {
            return new WaitCondition(WaitKind.TIMER, null, null, timeout, Instant.now().plus(timeout));
        }

        /** Wait for explicit manual approval. */
        public static WaitCondition forManualApproval() {
            return new WaitCondition(WaitKind.MANUAL, null, null, null, null);
        }
    }

    /**
     * Classification of what a WAIT step is waiting for.
     */
    enum WaitKind {
        /** Waiting for an external event. */
        EVENT,
        /** Waiting for a timer to fire. */
        TIMER,
        /** Waiting for manual approval. */
        MANUAL
    }
}
