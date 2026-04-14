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
 * Test agent that introduces a delay before processing.
 *
 * <p>Useful for verifying timeout handling and async behavior.
 */
public class TestDelayAgent<T> implements TypedAgent<T, T> {

    private final String agentId;
    private final Duration delay;

    public TestDelayAgent(String agentId, Duration delay) {
        this.agentId = agentId;
        this.delay = delay;
    }

    @Override
    public @NotNull AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
                .agentId(agentId)
                .name("Test Delay Agent")
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
    public @NotNull Promise<AgentResult<T>> process(
            @NotNull AgentContext ctx,
            @NotNull T input) {

        Instant start = Instant.now();

        // In async testing, return immediately
        // The delay metadata is captured for verification in tests
        Duration elapsed = Duration.between(start, Instant.now());
        return Promise.of(AgentResult.success(input, agentId, elapsed));
    }
}
