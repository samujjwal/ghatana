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
 * Simple test agent that echoes input as-is.
 *
 * <p>Useful for verifying basic E2E flow:
 * input → process → output (unchanged)
 */
public class TestEchoAgent implements TypedAgent<String, String> {

    private final String agentId;

    public TestEchoAgent(String agentId) {
        this.agentId = agentId;
    }

    @Override
    public @NotNull AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
                .agentId(agentId)
                .name("Test Echo Agent")
                .type(AgentType.DETERMINISTIC)
                .determinism(DeterminismGuarantee.FULL)
                .build();
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) {
        // No initialization needed for test
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Void> shutdown() {
        // No cleanup needed for test
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<HealthStatus> healthCheck() {
        return Promise.of(HealthStatus.healthy("Agent is healthy"));
    }

    @Override
    public @NotNull Promise<AgentResult<String>> process(
            @NotNull AgentContext ctx,
            @NotNull String input) {

        Instant start = Instant.now();
        String output = input; // Echo
        Duration elapsed = Duration.between(start, Instant.now());

        return Promise.of(AgentResult.success(output, agentId, elapsed));
    }
}
