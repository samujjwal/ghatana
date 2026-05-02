package com.ghatana.platform.agent.e2e;

import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

/**
 * Test agent that transforms input using a provided function.
 *
 * <p>Useful for verifying data transformation flows.
 */
public class TestTransformAgent<I, O> implements TypedAgent<I, O> {

    private final String agentId;
    private final Function<I, O> transform;

    public TestTransformAgent(String agentId, Function<I, O> transform) { 
        this.agentId = agentId;
        this.transform = transform;
    }

    @Override
    public @NotNull AgentDescriptor descriptor() { 
        return AgentDescriptor.builder() 
                .agentId(agentId) 
                .name("Test Transform Agent")
                .type(AgentType.DETERMINISTIC) 
                .determinism(DeterminismGuarantee.FULL) 
                .build(); 
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) { 
        return Promise.complete(); 
    }

    @Override
    public @NotNull Promise<Void> shutdown() { 
        return Promise.complete(); 
    }

    @Override
    public @NotNull Promise<HealthStatus> healthCheck() { 
        return Promise.of(HealthStatus.healthy("Agent is healthy"));
    }

    @Override
    public @NotNull Promise<AgentResult<O>> process( 
            @NotNull AgentContext ctx,
            @NotNull I input) {

        Instant start = Instant.now(); 
        try {
            O output = transform.apply(input); 
            Duration elapsed = Duration.between(start, Instant.now()); 
            return Promise.of(AgentResult.success(output, agentId, elapsed)); 
        } catch (Exception e) { 
            Duration elapsed = Duration.between(start, Instant.now()); 
            return Promise.of(AgentResult.failure(e, agentId, elapsed)); 
        }
    }
}
