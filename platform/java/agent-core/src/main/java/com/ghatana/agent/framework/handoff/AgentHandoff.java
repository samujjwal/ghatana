/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.handoff;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a handoff request from one agent to another.
 *
 * @param handoffId          unique identifier for this handoff
 * @param sourceAgentId      agent initiating the handoff
 * @param targetAgentId      agent intended to take over; may be determined by the coordinator
 * @param reason             reason for the handoff
 * @param contextSnapshot    state captured from the handing-off agent
 * @param originalInput      the original task input; forwarded to the target agent
 * @param initiatedAt        timestamp when the handoff was created
 *
 * @doc.type record
 * @doc.purpose Transfer contract between agents during handoff protocol
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentHandoff(
        @NotNull String handoffId,
        @NotNull String sourceAgentId,
        @NotNull String targetAgentId,
        @NotNull HandoffReason reason,
        @NotNull AgentContextSnapshot contextSnapshot,
        @NotNull Object originalInput,
        @NotNull Instant initiatedAt) {

    public AgentHandoff {
        Objects.requireNonNull(handoffId, "handoffId");
        Objects.requireNonNull(sourceAgentId, "sourceAgentId");
        Objects.requireNonNull(targetAgentId, "targetAgentId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(contextSnapshot, "contextSnapshot");
        Objects.requireNonNull(originalInput, "originalInput");
        Objects.requireNonNull(initiatedAt, "initiatedAt");
    }

    /**
     * Factory that generates a new handoff ID and timestamps now.
     */
    @NotNull
    public static AgentHandoff of(
            @NotNull String sourceAgentId,
            @NotNull String targetAgentId,
            @NotNull HandoffReason reason,
            @NotNull AgentContextSnapshot contextSnapshot,
            @NotNull Object originalInput) {
        return new AgentHandoff(
                UUID.randomUUID().toString(),
                sourceAgentId,
                targetAgentId,
                reason,
                contextSnapshot,
                originalInput,
                Instant.now());
    }
}
