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

    public TestTransformAgent(String agentId, Function<I, O> transform) { // GH-90000
        this.agentId = agentId;
        this.transform = transform;
    }

    @Override
    public @NotNull AgentDescriptor descriptor() { // GH-90000
        return AgentDescriptor.builder() // GH-90000
                .agentId(agentId) // GH-90000
                .name("Test Transform Agent [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .determinism(DeterminismGuarantee.FULL) // GH-90000
                .build(); // GH-90000
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) { // GH-90000
        return Promise.complete(); // GH-90000
    }

    @Override
    public @NotNull Promise<Void> shutdown() { // GH-90000
        return Promise.complete(); // GH-90000
    }

    @Override
    public @NotNull Promise<HealthStatus> healthCheck() { // GH-90000
        return Promise.of(HealthStatus.healthy("Agent is healthy [GH-90000]"));
    }

    @Override
    public @NotNull Promise<AgentResult<O>> process( // GH-90000
            @NotNull AgentContext ctx,
            @NotNull I input) {

        Instant start = Instant.now(); // GH-90000
        try {
            O output = transform.apply(input); // GH-90000
            Duration elapsed = Duration.between(start, Instant.now()); // GH-90000
            return Promise.of(AgentResult.success(output, agentId, elapsed)); // GH-90000
        } catch (Exception e) { // GH-90000
            Duration elapsed = Duration.between(start, Instant.now()); // GH-90000
            return Promise.of(AgentResult.failure(e, agentId, elapsed)); // GH-90000
        }
    }
}
