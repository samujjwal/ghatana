/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.registry;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.core.operator.agent.AgentOperator;
import com.ghatana.core.operator.agent.AgentOperatorKind;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Phase 6: Adapter that wraps a TypedAgent into the AgentOperator interface.
 *
 * <p>This adapter enables the migration from direct agent execution to the
 * AgentOperator pattern while maintaining backward compatibility with existing
 * agent implementations.
 *
 * @doc.type class
 * @doc.purpose Adapts TypedAgent to AgentOperator interface
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class AgentOperatorAdapter implements AgentOperator {

    private final TypedAgent<Map<String, Object>, Map<String, Object>> agent;
    private final String agentRef;
    private final AgentOperatorKind kind;

    public AgentOperatorAdapter(
            @NotNull TypedAgent<Map<String, Object>, Map<String, Object>> agent,
            @NotNull String agentRef,
            @NotNull AgentOperatorKind kind) {
        this.agent = agent;
        this.agentRef = agentRef;
        this.kind = kind;
    }

    @Override
    @NotNull
    public Promise<Map<String, Object>> process(@NotNull Map<String, Object> input) {
        // Convert from operator input to agent execution
        AgentContext ctx = new AgentContext(agentRef, Map.of());
        return agent.execute(ctx, input)
                .map(AgentResult::getOutput);
    }

    @Override
    @NotNull
    public String agentRef() {
        return agentRef;
    }

    @Override
    @NotNull
    public AgentOperatorKind agentOperatorKind() {
        return kind;
    }

    @Override
    @NotNull
    public AgentSideEffectProfile sideEffectProfile() {
        // Default profile - can be customized per agent
        return AgentSideEffectProfile.READ_WRITE;
    }

    @Override
    @NotNull
    public String inputSchema() {
        // Default schema reference - should be configured per agent
        return "agent-input-default";
    }

    @Override
    @NotNull
    public String outputSchema() {
        // Default schema reference - should be configured per agent
        return "agent-output-default";
    }

    @Override
    @NotNull
    public Map<String, Object> modelPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> toolPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> memoryPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> retrievalPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> guardrailPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> replayPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> uncertaintyPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> humanReviewPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> observabilityPolicy() {
        return Map.of();
    }
}
