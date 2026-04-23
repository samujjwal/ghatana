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
 * Test agent that returns output with a specified confidence level.
 *
 * <p>Useful for testing probabilistic decision flows and confidence thresholds.
 */
public class TestConfidenceAgent<T> implements TypedAgent<T, T> {

    private final String agentId;
    private final double confidence;

    public TestConfidenceAgent(String agentId, double confidence) { // GH-90000
        this.agentId = agentId;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence)); // GH-90000
    }

    @Override
    public @NotNull AgentDescriptor descriptor() { // GH-90000
        return AgentDescriptor.builder() // GH-90000
                .agentId(agentId) // GH-90000
                .name("Test Confidence Agent")
                .type(AgentType.PROBABILISTIC) // GH-90000
                .determinism(DeterminismGuarantee.NONE) // GH-90000
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

        return Promise.of(AgentResult.successWithConfidence( // GH-90000
                input,
                confidence,
                agentId,
                elapsed,
                "Test: confidence=" + confidence
        ));
    }
}
