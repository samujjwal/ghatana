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
 * Immutable message envelope exchanged between agents.
 *
 * <p>An {@code AgentMessage} carries a typed payload along with routing
 * metadata (sender, recipient, correlation ID). All fields except
 * {@code payload} are required.
 *
 * @param messageId     unique identifier for this message
 * @param correlationId correlation key linking request and reply messages
 * @param senderAgentId the agent that emitted this message
 * @param recipientAgentId the intended recipient agent; may be {@code null} for broadcasts
 * @param tenantId      tenant context
 * @param payload       application-defined payload content
 * @param sentAt        UTC timestamp of message creation
 * @param metadata      additional routing or diagnostic metadata (immutable)
 *
 * @doc.type class
 * @doc.purpose Immutable inter-agent message envelope
 * @doc.layer platform
 * @doc.pattern Record
 */
public record AgentMessage(
        @NotNull String messageId,
        @NotNull String correlationId,
        @NotNull String senderAgentId,
        @Nullable String recipientAgentId,
        @NotNull String tenantId,
        @Nullable Object payload,
        @NotNull Instant sentAt,
        @NotNull Map<String, String> metadata
) {
    /** Compact constructor — validates required fields and makes collections immutable. */
    public AgentMessage {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (senderAgentId == null || senderAgentId.isBlank()) {
            throw new IllegalArgumentException("senderAgentId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(sentAt, "sentAt");
        Objects.requireNonNull(metadata, "metadata");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Factory: creates a new point-to-point message with a random message ID and current timestamp.
     *
     * @param senderAgentId    sending agent
     * @param recipientAgentId receiving agent
     * @param correlationId    correlation key
     * @param tenantId         tenant scope
     * @param payload          message body
     */
    public static AgentMessage of(
            String senderAgentId, String recipientAgentId,
            String correlationId, String tenantId, @Nullable Object payload) {
        return new AgentMessage(
                UUID.randomUUID().toString(), correlationId,
                senderAgentId, recipientAgentId, tenantId,
                payload, Instant.now(), Map.of());
    }
}
