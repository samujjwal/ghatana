/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builder for constructing hash-chained {@link TraceEvent} instances.
 *
 * <p>Manages sequence numbering and hash chain linking automatically.
 * Each builder instance is scoped to a single trace (agent turn).
 *
 * <p>Usage:
 * <pre>{@code
 * TraceEventBuilder builder = new TraceEventBuilder(traceId, agentId, tenantId, ledger);
 * TraceEvent event = builder.build(TraceEventType.ACTION_EXECUTED, "Called weather API",
 *     Map.of("toolId", "weather-lookup", "targetType", "external-api"));
 * ledger.append(event);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Builder for hash-chained trace events within a single trace
 * @doc.layer agent-runtime
 * @doc.pattern Builder
 */
public class TraceEventBuilder {

    private final String traceId;
    private final String agentId;
    private final String tenantId;
    private final AtomicLong sequenceCounter;
    private volatile String lastHash;

    /**
     * Creates a new builder for a specific trace context.
     *
     * @param traceId       the trace identifier for this agent turn
     * @param agentId       the agent producing events
     * @param tenantId      the tenant scope
     * @param previousHash  the hash of the last event in the ledger partition
     *                      (use {@link HashChainedTraceAppender#getLastHash})
     */
    public TraceEventBuilder(
            @NotNull String traceId,
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String previousHash) {
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.lastHash = Objects.requireNonNull(previousHash, "previousHash");
        this.sequenceCounter = new AtomicLong(0);
    }

    /**
     * Builds a new trace event with automatic sequencing and hash chaining.
     *
     * @param eventType the type of event
     * @param summary   human-readable summary
     * @param payload   structured key-value payload
     * @return a fully populated, hash-chained trace event
     */
    @NotNull
    public TraceEvent build(
            @NotNull TraceEventType eventType,
            @NotNull String summary,
            @NotNull Map<String, String> payload) {
        String eventId = UUID.randomUUID().toString();
        long seq = sequenceCounter.getAndIncrement();
        Instant now = Instant.now();

        // Build the event without hash first for computation
        TraceEvent partial = new TraceEvent(
                eventId, traceId, seq, eventType, agentId, tenantId,
                lastHash, "", // placeholder hash
                summary, payload, now);

        // Compute the canonical hash
        String hash = HashChainedTraceAppender.computeHash(partial);

        // Re-create with the real hash
        TraceEvent event = new TraceEvent(
                eventId, traceId, seq, eventType, agentId, tenantId,
                lastHash, hash,
                summary, payload, now);

        // Advance chain
        lastHash = hash;
        return event;
    }

    /**
     * Builds a trace event with no payload.
     */
    @NotNull
    public TraceEvent build(@NotNull TraceEventType eventType, @NotNull String summary) {
        return build(eventType, summary, Map.of());
    }
}
