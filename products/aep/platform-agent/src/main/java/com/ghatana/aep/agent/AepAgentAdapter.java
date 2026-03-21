/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent;

import io.activej.promise.Promise;

/**
 * Adapter for integrating AEP with external agent systems.
 * Provides a bridge between AEP pipelines and agent frameworks.
 */
public class AepAgentAdapter {
    
    private final String agentId;
    private volatile boolean connected = false;
    
    public AepAgentAdapter(String agentId) {
        this.agentId = agentId;
    }
    
    /**
     * Initialize the agent adapter.
     * @return Promise of completion
     */
    public Promise<Void> initialize() {
        connected = true;
        return Promise.complete();
    }
    
    /**
     * Execute a task through the agent.
     * @param task the task to execute
     * @return Promise of result
     */
    public Promise<String> executeTask(String task) {
        if (!connected) {
            return Promise.ofException(new IllegalStateException("Adapter not initialized"));
        }
        return Promise.of("Task executed by agent: " + agentId);
    }
    
    /**
     * Disconnect the agent adapter.
     * @return Promise of completion
     */
    public Promise<Void> disconnect() {
        connected = false;
        return Promise.complete();
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public String getAgentId() {
        return agentId;
    }
}
