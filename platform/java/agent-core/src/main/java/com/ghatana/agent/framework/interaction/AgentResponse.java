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

/**
 * Immutable response returned when an agent finishes processing a message.
 *
 * @param requestMessageId  the message ID of the triggering {@link AgentMessage}
 * @param respondingAgentId the agent that produced this response
 * @param status            terminal status of processing
 * @param payload           result payload; null for non-SUCCESS statuses
 * @param errorMessage      human-readable error; null on SUCCESS
 * @param respondedAt       UTC timestamp of response creation
 * @param metadata          additional diagnostic metadata (immutable)
 *
 * @doc.type class
 * @doc.purpose Immutable inter-agent response
 * @doc.layer platform
 * @doc.pattern Record
 */
public record AgentResponse(
        @NotNull String requestMessageId,
        @NotNull String respondingAgentId,
        @NotNull AgentResponseStatus status,
        @Nullable Object payload,
        @Nullable String errorMessage,
        @NotNull Instant respondedAt,
        @NotNull Map<String, String> metadata
) {
    /** Compact constructor — validates required fields and makes collections immutable. */
    public AgentResponse {
        if (requestMessageId == null || requestMessageId.isBlank()) {
            throw new IllegalArgumentException("requestMessageId must not be blank");
        }
        if (respondingAgentId == null || respondingAgentId.isBlank()) {
            throw new IllegalArgumentException("respondingAgentId must not be blank");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(respondedAt, "respondedAt");
        Objects.requireNonNull(metadata, "metadata");
        metadata = Map.copyOf(metadata);
    }

    /** Returns {@code true} if the response indicates successful processing. */
    public boolean isSuccess() {
        return status == AgentResponseStatus.SUCCESS;
    }

    /**
     * Factory: successful response with a payload.
     */
    public static AgentResponse success(String requestMessageId, String respondingAgentId, @Nullable Object payload) {
        return new AgentResponse(requestMessageId, respondingAgentId,
                AgentResponseStatus.SUCCESS, payload, null, Instant.now(), Map.of());
    }

    /**
     * Factory: failure response with an error message.
     */
    public static AgentResponse error(String requestMessageId, String respondingAgentId, String errorMessage) {
        return new AgentResponse(requestMessageId, respondingAgentId,
                AgentResponseStatus.ERROR, null, errorMessage, Instant.now(), Map.of());
    }

    /**
     * Factory: rejection with a reason.
     */
    public static AgentResponse rejected(String requestMessageId, String respondingAgentId, String reason) {
        return new AgentResponse(requestMessageId, respondingAgentId,
                AgentResponseStatus.REJECTED, null, reason, Instant.now(), Map.of());
    }
}
