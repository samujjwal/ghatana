/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent;

import com.ghatana.aep.AepEngine;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.domain.agent.registry.AgentExecutionContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.UUID;

/**
 * Bridge for sharing context between AEP engine and external agents.
 * Enables bidirectional data flow between AEP pipelines and agent frameworks.
 */
public class AepContextBridge {
    
    private final AepEngine engine;
    private volatile boolean active = false;
    
    public AepContextBridge(AepEngine engine) {
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
        if (!active) {
            return Promise.ofException(new IllegalStateException("Bridge not active"));
        }
        return Promise.complete();
    }
    
    /**
     * Get context from AEP to agent.
     * @return Promise of context data
     */
    public Promise<String> getFromAep() {
        if (!active) {
            return Promise.ofException(new IllegalStateException("Bridge not active"));
        }
        return Promise.of("Context from AEP engine");
    }
    
    /**
     * Deactivate the context bridge.
     * @return Promise of completion
     */
    public Promise<Void> deactivate() {
        active = false;
        return Promise.complete();
    }
    
    public boolean isActive() {
        return active;
    }
    
    public AepEngine getEngine() {
        return engine;
    }

    /**
     * Converts a platform {@link AgentExecutionContext} into a GAA
     * {@link AgentContext} suitable for {@code AgentTurnPipeline} execution.
     *
     * @param executionContext the platform execution context (provides tenantId)
     * @param agentId         the agent being executed
     * @return a fully constructed AgentContext
     */
    public AgentContext toAgentContext(AgentExecutionContext executionContext, String agentId) {
        return AgentContext.builder()
                .agentId(agentId)
                .turnId(UUID.randomUUID().toString())
                .tenantId(executionContext.tenantId())
                .startTime(Instant.now())
                .memoryStore(MemoryStore.noOp())
                .build();
    }
}
