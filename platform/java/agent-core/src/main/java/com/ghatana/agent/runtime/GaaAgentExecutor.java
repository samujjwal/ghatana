/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.framework.runtime.AgentTurnPipeline;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Governed executor that wraps every {@link TypedAgent#process(AgentContext, Object)}
 * call in the uniform GAA lifecycle pipeline.
 */
public final class GaaAgentExecutor implements TypedAgentExecutor {

    @Override
    @NotNull
    public <I, O> Promise<AgentResult<O>> execute(
            @NotNull TypedAgent<I, O> agent,
            @NotNull AgentContext context,
            @NotNull I input) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(input, "input must not be null");

        return AgentTurnPipeline.<I, O>builder(agent.descriptor().getAgentId())
                .reasonResult((perceived, ctx) -> agent.process(ctx, perceived))
                .build()
                .executeResult(input, context);
    }

    /**
     * Executes with an AgentDefinition-derived spec digest in context when present.
     */
    @NotNull
    public <I, O> Promise<AgentResult<O>> execute(
            @NotNull AgentDefinition definition,
            @NotNull TypedAgent<I, O> agent,
            @NotNull AgentContext context,
            @NotNull I input) {
        Objects.requireNonNull(definition, "definition must not be null");
        AgentContext enriched = context.toBuilder()
                .addConfig("specDigest", definition.canonicalDigest())
                .build();
        return execute(agent, enriched, input)
                .map(result -> result.toBuilder()
                        .agentVersion(definition.getVersion())
                        .specDigest(definition.canonicalDigest())
                        .build());
    }
}
