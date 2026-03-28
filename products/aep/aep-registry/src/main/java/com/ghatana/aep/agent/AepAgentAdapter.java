/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.promise.Promise;

import java.util.Objects;

/**
 * Adapter for integrating AEP with external agent systems.
 * Provides a bridge between AEP pipelines and agent frameworks.
 *
 * @doc.type class
 * @doc.purpose Bridge AEP agent registrations to executable agent-turn generators
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class AepAgentAdapter {

    private final AgentDefinition definition;
    private final OutputGenerator<String, String> outputGenerator;
    private volatile boolean connected = false;

    public AepAgentAdapter(String agentId) {
        this(
            AgentDefinition.builder()
                .id(Objects.requireNonNull(agentId, "agentId must not be null"))
                .version("1.0.0")
                .name(agentId)
                .type(AgentType.DETERMINISTIC)
                .build(),
            (task, context) -> Promise.of("Task executed by agent: " + agentId)
        );
    }

    public AepAgentAdapter(AgentDefinition definition, OutputGenerator<String, String> outputGenerator) {
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.outputGenerator = Objects.requireNonNull(outputGenerator, "outputGenerator must not be null");
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
        return executeTurn(task, AgentContext.builder()
            .turnId("task-" + System.nanoTime())
            .agentId(definition.getId())
            .tenantId("default")
            .memoryStore(MemoryStore.noOp())
            .build());
    }

    /**
     * Execute a single agent turn against the supplied execution context.
     *
     * @param input turn input payload
     * @param context execution context for this turn
     * @return promise of generated output
     */
    public Promise<String> executeTurn(String input, AgentContext context) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        context.setMetadata("aep.adapter.agentId", definition.getId());
        context.setMetadata("aep.adapter.input", input);
        context.recordMetric("aep.agent.turn.started", 1.0);
        context.addTraceTag("aep.agent.id", definition.getId());

        return outputGenerator.generate(input, context)
            .whenResult(output -> {
                context.setMetadata("aep.adapter.output", output);
                context.recordMetric("aep.agent.turn.succeeded", 1.0);
            })
            .whenException(error -> {
                context.recordMetric("aep.agent.turn.failed", 1.0);
                context.addTraceTag("aep.agent.error", error.getClass().getSimpleName());
            });
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
        return definition.getId();
    }

    public AgentDefinition getDefinition() {
        return definition;
    }
}
