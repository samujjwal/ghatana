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
 * input → process → output (unchanged) // GH-90000
 */
public class TestEchoAgent implements TypedAgent<String, String> {

    private final String agentId;

    public TestEchoAgent(String agentId) { // GH-90000
        this.agentId = agentId;
    }

    @Override
    public @NotNull AgentDescriptor descriptor() { // GH-90000
        return AgentDescriptor.builder() // GH-90000
                .agentId(agentId) // GH-90000
                .name("Test Echo Agent [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .determinism(DeterminismGuarantee.FULL) // GH-90000
                .build(); // GH-90000
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) { // GH-90000
        // No initialization needed for test
        return Promise.complete(); // GH-90000
    }

    @Override
    public @NotNull Promise<Void> shutdown() { // GH-90000
        // No cleanup needed for test
        return Promise.complete(); // GH-90000
    }

    @Override
    public @NotNull Promise<HealthStatus> healthCheck() { // GH-90000
        return Promise.of(HealthStatus.healthy("Agent is healthy [GH-90000]"));
    }

    @Override
    public @NotNull Promise<AgentResult<String>> process( // GH-90000
            @NotNull AgentContext ctx,
            @NotNull String input) {

        Instant start = Instant.now(); // GH-90000
        String output = input; // Echo
        Duration elapsed = Duration.between(start, Instant.now()); // GH-90000

        return Promise.of(AgentResult.success(output, agentId, elapsed)); // GH-90000
    }
}
