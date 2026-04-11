/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable trace event recorded in the agent evidence plane.
 *
 * <p>Each event captures a single observable action, decision, or state change in
 * the agent lifecycle. Events are chained via {@link #previousHash} to form a
 * tamper-evident append-only ledger.
 *
 * <p>Fields are intentionally flat (no nested domain objects) so events can be
 * serialized to JSON/Avro for long-term archival and regulatory compliance.
 *
 * @doc.type record
 * @doc.purpose Immutable trace event for tamper-evident agent evidence plane
 * @doc.layer agent-runtime
 * @doc.pattern Event, ValueObject
 */
public record TraceEvent(
        /** Unique event identifier (UUID). */
        @NotNull String eventId,

        /** Globally unique trace (correlation) identifier for the full agent turn. */
        @NotNull String traceId,

        /** Monotonically increasing sequence within a trace. */
        long sequenceNumber,

        /** Event classification. */
        @NotNull TraceEventType eventType,

        /** Agent that produced this event. */
        @NotNull String agentId,

        /** Tenant scope. */
        @NotNull String tenantId,

        /** SHA-256 hash of the previous event in this ledger partition (empty string for genesis). */
        @NotNull String previousHash,

        /** SHA-256 hash of this event's canonical payload (eventId + traceId + seq + type + agentId + tenantId + payload + timestamp). */
        @NotNull String eventHash,

        /** Human-readable summary of the event. */
        @NotNull String summary,

        /** Structured payload (serializable key-value pairs). */
        @NotNull Map<String, String> payload,

        /** When this event occurred. */
        @NotNull Instant timestamp
) {

    /**
     * Compact constructor with validation and defensive copy.
     */
    public TraceEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(previousHash, "previousHash");
        Objects.requireNonNull(eventHash, "eventHash");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(timestamp, "timestamp");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
