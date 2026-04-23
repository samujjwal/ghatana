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

/**
 * Test agent that always fails with a provided exception.
 *
 * <p>Useful for verifying error handling and failure propagation.
 */
public class TestFailingAgent<T> implements TypedAgent<T, T> {

    private final String agentId;
    private final Exception exception;

    public TestFailingAgent(String agentId, Exception exception) { // GH-90000
        this.agentId = agentId;
        this.exception = exception;
    }

    @Override
    public @NotNull AgentDescriptor descriptor() { // GH-90000
        return AgentDescriptor.builder() // GH-90000
                .agentId(agentId) // GH-90000
                .name("Test Failing Agent")
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
        return Promise.of(HealthStatus.healthy("Agent is healthy"));
    }

    @Override
    public @NotNull Promise<AgentResult<T>> process( // GH-90000
            @NotNull AgentContext ctx,
            @NotNull T input) {

        Instant start = Instant.now(); // GH-90000
        Duration elapsed = Duration.between(start, Instant.now()); // GH-90000

        return Promise.of(AgentResult.failure(exception, agentId, elapsed)); // GH-90000
    }
}
