/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent;

import com.ghatana.aep.AepEngine;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Bridge for sharing context between AEP engine and external agents.
 * Enables bidirectional data flow between AEP pipelines and agent frameworks.
 *
 * @doc.type class
 * @doc.purpose Convert execution context into agent turns and optionally mirror shared context into AEP
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class AepContextBridge {

    public static final String CONTEXT_EVENT_TYPE = "aep.agent.context";

    @Nullable
    private final AepEngine engine;
    private final MemoryStore memoryStore;
    private final ConcurrentMap<String, String> latestContextByTenant = new ConcurrentHashMap<>();
    private volatile boolean active = false;

    public AepContextBridge(MemoryStore memoryStore) {
        this(memoryStore, null);
    }

    public AepContextBridge(MemoryStore memoryStore, @Nullable AepEngine engine) {
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore must not be null");
        this.engine = engine;
    }

    /**
     * Activate the context bridge.
     * @return Promise of completion
     */
    public Promise<Void> activate() {
        active = true;
        return Promise.complete();
    }

    /**
     * Share context from agent to AEP.
     * @param context the context data
     * @return Promise of completion
     */
    public Promise<Void> shareToAep(String context) {
        return shareToAep("default", context);
    }

    public Promise<Void> shareToAep(String tenantId, String context) {
        if (!active) {
            return Promise.ofException(new IllegalStateException("Bridge not active"));
        }
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(context, "context must not be null");

        latestContextByTenant.put(tenantId, context);
        if (engine != null && engine.eventCloud() != null) {
            engine.eventCloud().append(tenantId, CONTEXT_EVENT_TYPE, context.getBytes(StandardCharsets.UTF_8));
        }
        return Promise.complete();
    }

    /**
     * Get context from AEP to agent.
     * @return Promise of context data
     */
    public Promise<String> getFromAep() {
        return getFromAep("default");
    }

    public Promise<String> getFromAep(String tenantId) {
        if (!active) {
            return Promise.ofException(new IllegalStateException("Bridge not active"));
        }
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return Promise.of(latestContextByTenant.getOrDefault(tenantId, ""));
    }

    /**
     * Deactivate the context bridge.
     * @return Promise of completion
     */
    public Promise<Void> deactivate() {
        active = false;
        latestContextByTenant.clear();
        return Promise.complete();
    }

    public boolean isActive() {
        return active;
    }

    @Nullable
    public AepEngine getEngine() {
        return engine;
    }

    /**
     * Converts a product {@link com.ghatana.aep.domain.agent.registry.AgentExecutionContext} into a GAA
     * {@link AgentContext} suitable for {@code AgentTurnPipeline} execution.
     *
     * @param executionContext the product execution context
     * @param agentId the agent being executed
     * @return a fully constructed AgentContext
     */
    public AgentContext toAgentContext(com.ghatana.aep.domain.agent.registry.AgentExecutionContext executionContext,
                                       String agentId) {
        return toAgentContext(executionContext, agentId, executionContext != null ? executionContext.correlationId() : null);
    }

    /**
     * Converts a product {@link com.ghatana.aep.domain.agent.registry.AgentExecutionContext} into a GAA
     * {@link AgentContext} with an explicit trace identifier.
     *
     * @param executionContext the product execution context
     * @param agentId the agent being executed
     * @param traceId trace identifier to propagate
     * @return a fully constructed AgentContext
     */
    public AgentContext toAgentContext(com.ghatana.aep.domain.agent.registry.AgentExecutionContext executionContext,
                                       String agentId,
                                       @Nullable String traceId) {
        Objects.requireNonNull(executionContext, "executionContext must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        return buildAgentContext(
            executionContext.tenantId(),
            agentId,
            traceId,
            executionContext.userId(),
            executionContext.metadata() != null ? executionContext.metadata() : Map.of()
        );
    }

    /**
     * Converts a platform {@link com.ghatana.platform.domain.agent.registry.AgentExecutionContext} into a GAA
     * {@link AgentContext} suitable for {@code AgentTurnPipeline} execution.
     *
     * @param executionContext the platform execution context (provides tenantId)
     * @param agentId the agent being executed
     * @return a fully constructed AgentContext
     */
    public AgentContext toAgentContext(com.ghatana.platform.domain.agent.registry.AgentExecutionContext executionContext,
                                       String agentId) {
        Objects.requireNonNull(executionContext, "executionContext must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        return buildAgentContext(executionContext.tenantId(), agentId, UUID.randomUUID().toString(), null, Map.of());
    }

    private AgentContext buildAgentContext(String tenantId,
                                           String agentId,
                                           @Nullable String traceId,
                                           @Nullable String userId,
                                           Map<String, Object> metadata) {
        return AgentContext.builder()
                .agentId(agentId)
                .turnId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .userId(userId)
                .startTime(Instant.now())
                .memoryStore(memoryStore)
                .logger(LoggerFactory.getLogger("agent." + agentId))
                .traceId(traceId)
                .metadata(metadata)
                .build();
    }
}
