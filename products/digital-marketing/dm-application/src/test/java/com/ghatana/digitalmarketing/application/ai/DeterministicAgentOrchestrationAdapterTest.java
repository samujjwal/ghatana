package com.ghatana.digitalmarketing.application.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DeterministicAgentOrchestrationAdapter (DMOS-P1-018).
 *
 * @doc.type test
 * @doc.purpose Verify deterministic fallback adapter behavior
 * @doc.layer application
 */
@DisplayName("DeterministicAgentOrchestrationAdapter")
class DeterministicAgentOrchestrationAdapterTest {

    @Test
    @DisplayName("invokeAgent returns deterministic output for same input")
    void invokeAgent_returnsDeterministicOutputForSameInput() {
        DeterministicAgentOrchestrationAdapter adapter = new DeterministicAgentOrchestrationAdapter();

        DmAgentOrchestrationPort.AgentResponse response1 = adapter.invokeAgent(
            DmAgentOrchestrationPort.AgentType.STRATEGY_GENERATOR,
            "Generate a strategy",
            "gpt-4",
            Map.of(),
            Duration.ofSeconds(30)
        ).getResult();

        DmAgentOrchestrationPort.AgentResponse response2 = adapter.invokeAgent(
            DmAgentOrchestrationPort.AgentType.STRATEGY_GENERATOR,
            "Generate a strategy",
            "gpt-4",
            Map.of(),
            Duration.ofSeconds(30)
        ).getResult();

        assertThat(response1.output()).isEqualTo(response2.output());
    }

    @Test
    @DisplayName("invokeAgent returns different output for different inputs")
    void invokeAgent_returnsDifferentOutputForDifferentInputs() {
        DeterministicAgentOrchestrationAdapter adapter = new DeterministicAgentOrchestrationAdapter();

        DmAgentOrchestrationPort.AgentResponse response1 = adapter.invokeAgent(
            DmAgentOrchestrationPort.AgentType.STRATEGY_GENERATOR,
            "Generate a strategy",
            "gpt-4",
            Map.of(),
            Duration.ofSeconds(30)
        ).getResult();

        DmAgentOrchestrationPort.AgentResponse response2 = adapter.invokeAgent(
            DmAgentOrchestrationPort.AgentType.STRATEGY_GENERATOR,
            "Generate a different strategy",
            "gpt-4",
            Map.of(),
            Duration.ofSeconds(30)
        ).getResult();

        assertThat(response1.output()).isNotEqualTo(response2.output());
    }

    @Test
    @DisplayName("invokeAgent returns success with all fields populated")
    void invokeAgent_returnsSuccessWithAllFieldsPopulated() {
        DeterministicAgentOrchestrationAdapter adapter = new DeterministicAgentOrchestrationAdapter();

        DmAgentOrchestrationPort.AgentResponse response = adapter.invokeAgent(
            DmAgentOrchestrationPort.AgentType.STRATEGY_GENERATOR,
            "Generate a strategy",
            "gpt-4",
            Map.of(),
            Duration.ofSeconds(30)
        ).getResult();

        assertThat(response.success()).isTrue();
        assertThat(response.output()).isNotEmpty();
        assertThat(response.model()).isEqualTo("gpt-4");
        assertThat(response.confidence()).isEqualTo(0.85);
        assertThat(response.evidenceLocation()).isNotNull();
        assertThat(response.duration()).isNotNull();
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    @DisplayName("invokeAgent uses default model when model is null")
    void invokeAgent_usesDefaultModelWhenModelIsNull() {
        DeterministicAgentOrchestrationAdapter adapter = new DeterministicAgentOrchestrationAdapter();

        DmAgentOrchestrationPort.AgentResponse response = adapter.invokeAgent(
            DmAgentOrchestrationPort.AgentType.STRATEGY_GENERATOR,
            "Generate a strategy",
            null,
            Map.of(),
            Duration.ofSeconds(30)
        ).getResult();

        assertThat(response.model()).isEqualTo("deterministic-model");
    }

    @Test
    @DisplayName("isAvailable always returns true")
    void isAvailable_alwaysReturnsTrue() {
        DeterministicAgentOrchestrationAdapter adapter = new DeterministicAgentOrchestrationAdapter();

        Boolean available = adapter.isAvailable().getResult();

        assertThat(available).isTrue();
    }

    @Test
    @DisplayName("getHealthStatus always returns HEALTHY")
    void getHealthStatus_alwaysReturnsHealthy() {
        DeterministicAgentOrchestrationAdapter adapter = new DeterministicAgentOrchestrationAdapter();

        DmAgentOrchestrationPort.AgentHealthStatus status = adapter.getHealthStatus().getResult();

        assertThat(status).isEqualTo(DmAgentOrchestrationPort.AgentHealthStatus.HEALTHY);
    }
}
