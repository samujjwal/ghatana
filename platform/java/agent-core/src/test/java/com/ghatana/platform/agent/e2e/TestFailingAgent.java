package com.ghatana.platform.agent.e2e;

import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.HealthStatus;
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

    public TestFailingAgent(String agentId, Exception exception) {
        this.agentId = agentId;
        this.exception = exception;
    }

    @Override
    public @NotNull AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
                .agentId(agentId)
                .name("Test Failing Agent")
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
        return Promise.of(HealthStatus.HEALTHY);
    }

    @Override
    public @NotNull Promise<AgentResult<T>> process(
            @NotNull AgentContext ctx,
            @NotNull T input) {

        Instant start = Instant.now();
        Duration elapsed = Duration.between(start, Instant.now());

        return Promise.of(AgentResult.failure(exception, agentId, elapsed));
    }
}
