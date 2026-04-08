/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.handoff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of contextual state captured when a handoff is initiated.
 *
 * <p>The {@code metadata} map holds arbitrary key-value pairs (e.g. conversation history pointers,
 * active tool names, turn count) that the receiving agent may need to continue the task.
 *
 * @param agentId        the agent that produced this snapshot
 * @param tenantId       tenant scope
 * @param correlationId  correlation identifier for the originating request
 * @param conversationId conversation thread identifier, may be null for single-shot tasks
 * @param metadata       additional context, immutable copy provided at creation time
 *
 * @doc.type record
 * @doc.purpose Context snapshot transferred with an agent handoff
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentContextSnapshot(
        @NotNull String agentId,
        @NotNull String tenantId,
        @NotNull String correlationId,
        @Nullable String conversationId,
        @NotNull Map<String, String> metadata) {

    public AgentContextSnapshot {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(metadata, "metadata");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Convenience factory for the common case without a conversation thread.
     */
    @NotNull
    public static AgentContextSnapshot of(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String correlationId,
            @NotNull Map<String, String> metadata) {
        return new AgentContextSnapshot(agentId, tenantId, correlationId, null, metadata);
    }
}
