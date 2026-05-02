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

    public TestConfidenceAgent(String agentId, double confidence) { 
        this.agentId = agentId;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence)); 
    }

    @Override
    public @NotNull AgentDescriptor descriptor() { 
        return AgentDescriptor.builder() 
                .agentId(agentId) 
                .name("Test Confidence Agent")
                .type(AgentType.PROBABILISTIC) 
                .determinism(DeterminismGuarantee.NONE) 
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
        Duration elapsed = Duration.between(start, Instant.now()); 

        return Promise.of(AgentResult.successWithConfidence( 
                input,
                confidence,
                agentId,
                elapsed,
                "Test: confidence=" + confidence
        ));
    }
}
