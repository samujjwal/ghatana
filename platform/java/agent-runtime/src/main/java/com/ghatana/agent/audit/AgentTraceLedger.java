/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Append-only agent trace ledger providing tamper-evident evidence recording.
 *
 * <p>All significant agent actions, decisions, approvals, and state mutations
 * are appended to this ledger. Each entry is hash-chained to the previous
 * entry to detect tampering.
 *
 * <p>Implementations must guarantee:
 * <ul>
 *   <li><b>Append-only</b>: No update or delete operations.</li>
 *   <li><b>Hash-chain integrity</b>: Each event references the hash of the previous event.</li>
 *   <li><b>Tenant isolation</b>: Queries are always scoped to a tenant.</li>
 *   <li><b>Durability</b>: Events must be persisted before returning.</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Append-only tamper-evident trace ledger SPI
 * @doc.layer agent-runtime
 * @doc.pattern Repository
 */
public interface AgentTraceLedger {

    /**
     * Appends a trace event to the ledger.
     *
     * <p>The implementation must verify that {@code event.previousHash()} matches
     * the hash of the last event in the partition (or is empty for the first event).
     *
     * @param event the event to append
     * @return promise completing when the event has been durably persisted
     * @throws IllegalStateException if the hash chain is broken
     */
    @NotNull
    Promise<Void> append(@NotNull TraceEvent event);

    /**
     * Queries events for a specific trace (agent turn).
     *
     * @param traceId  the trace identifier
     * @param tenantId tenant scope
     * @return promise of ordered events for the trace
     */
    @NotNull
    Promise<List<TraceEvent>> getByTrace(@NotNull String traceId, @NotNull String tenantId);

    /**
     * Queries events for a specific agent within a time window.
     *
     * @param agentId  the agent identifier
     * @param tenantId tenant scope
     * @param from     inclusive start time (null for unbounded)
     * @param to       exclusive end time (null for unbounded)
     * @param limit    maximum number of events to return
     * @return promise of matching events ordered by sequence
     */
    @NotNull
    Promise<List<TraceEvent>> getByAgent(
            @NotNull String agentId,
            @NotNull String tenantId,
            @Nullable Instant from,
            @Nullable Instant to,
            int limit);

    /**
     * Queries events by type within a time window.
     *
     * @param eventType event type to filter by
     * @param tenantId  tenant scope
     * @param from      inclusive start time (null for unbounded)
     * @param to        exclusive end time (null for unbounded)
     * @param limit     maximum number of events to return
     * @return promise of matching events
     */
    @NotNull
    Promise<List<TraceEvent>> getByType(
            @NotNull TraceEventType eventType,
            @NotNull String tenantId,
            @Nullable Instant from,
            @Nullable Instant to,
            int limit);

    /**
     * Verifies hash-chain integrity for a range of events.
     *
     * @param events ordered list of events to verify
     * @return true if the hash chain is valid, false if any link is broken
     */
    boolean verifyChain(@NotNull List<TraceEvent> events);
}
