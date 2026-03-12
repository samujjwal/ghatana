/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent;

import com.ghatana.aep.domain.agent.registry.AgentExecutionContext;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Converts an AEP {@link AgentExecutionContext} into an agent-framework {@link AgentContext}.
 *
 * <p>The AEP domain uses a lightweight {@code AgentExecutionContext} (one method: {@code tenantId()})
 * while the agent framework requires a richer {@link AgentContext} that carries tracing,
 * session metadata, memory store, and budget information.
 *
 * <p>This bridge is the single point of translation between the two contexts so that
 * product code does not need to construct {@code AgentContext} instances manually.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AepContextBridge bridge = new AepContextBridge(memoryStore);
 * AgentContext ctx = bridge.toAgentContext(execCtx, "fraud-detector-agent", traceId);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Translates AEP AgentExecutionContext to agent-framework AgentContext
 * @doc.layer product
 * @doc.pattern Adapter
 * @doc.gaa.lifecycle perceive
 */
public final class AepContextBridge {

    private final MemoryStore memoryStore;

    /**
     * Creates an {@code AepContextBridge} with the supplied memory store.
     *
     * <p>The same {@code MemoryStore} instance is reused across all converted contexts
     * so that episodic and semantic memories are shared within the same agent scope.
     *
     * @param memoryStore agent memory store (e.g., {@link com.ghatana.agent.framework.memory.EventLogMemoryStore})
     */
    public AepContextBridge(@NotNull MemoryStore memoryStore) {
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore cannot be null");
    }

    /**
     * Converts an AEP execution context into an agent-framework context.
     *
     * @param execCtx  AEP execution context providing {@code tenantId}
     * @param agentId  ID of the agent that will execute in this context
     * @param traceId  distributed trace ID (may be {@code null})
     * @return a fully-constructed {@link AgentContext}
     */
    @NotNull
    public AgentContext toAgentContext(
            @NotNull AgentExecutionContext execCtx,
            @NotNull String agentId,
            @Nullable String traceId) {
        Objects.requireNonNull(execCtx,  "execCtx cannot be null");
        Objects.requireNonNull(agentId,  "agentId cannot be null");

        return AgentContext.builder()
                .turnId(UUID.randomUUID().toString())
                .agentId(agentId)
                .tenantId(execCtx.tenantId())
                .traceId(traceId)
                .startTime(Instant.now())
                .memoryStore(memoryStore)
                .logger(LoggerFactory.getLogger("agent." + agentId))
                .build();
    }

    /**
     * Convenience overload that generates a random trace ID.
     *
     * @param execCtx AEP execution context
     * @param agentId agent identifier
     * @return a fully-constructed {@link AgentContext}
     */
    @NotNull
    public AgentContext toAgentContext(
            @NotNull AgentExecutionContext execCtx,
            @NotNull String agentId) {
        return toAgentContext(execCtx, agentId, UUID.randomUUID().toString());
    }
}
