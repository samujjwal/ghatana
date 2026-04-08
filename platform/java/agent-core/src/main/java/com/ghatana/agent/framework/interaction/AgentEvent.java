/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.interaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable event emitted by an agent to notify other agents or the platform.
 *
 * <p>Unlike a point-to-point {@link AgentMessage}, an {@code AgentEvent} is
 * typically broadcast to all interested subscribers. The platform's event bus
 * is responsible for routing and fan-out.
 *
 * @param eventId       unique identifier for this event
 * @param eventType     domain-meaningful event type (e.g., "analysis.completed")
 * @param sourceAgentId agent that emitted the event
 * @param tenantId      tenant scope
 * @param payload       event-specific data; may be null for notification-only events
 * @param emittedAt     UTC timestamp of emission
 * @param metadata      additional key/value annotations (immutable)
 *
 * @doc.type class
 * @doc.purpose Immutable inter-agent event for broadcast notification
 * @doc.layer platform
 * @doc.pattern Record
 */
public record AgentEvent(
        @NotNull String eventId,
        @NotNull String eventType,
        @NotNull String sourceAgentId,
        @NotNull String tenantId,
        @Nullable Object payload,
        @NotNull Instant emittedAt,
        @NotNull Map<String, String> metadata
) {
    /** Compact constructor — validates required fields and makes collections immutable. */
    public AgentEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (sourceAgentId == null || sourceAgentId.isBlank()) {
            throw new IllegalArgumentException("sourceAgentId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(emittedAt, "emittedAt");
        Objects.requireNonNull(metadata, "metadata");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Factory: creates an event with a random ID and the current timestamp.
     *
     * @param eventType     semantic event type
     * @param sourceAgentId emitting agent ID
     * @param tenantId      tenant scope
     * @param payload       event payload (may be null)
     */
    public static AgentEvent of(
            String eventType, String sourceAgentId,
            String tenantId, @Nullable Object payload) {
        return new AgentEvent(
                UUID.randomUUID().toString(), eventType, sourceAgentId,
                tenantId, payload, Instant.now(), Map.of());
    }
}
