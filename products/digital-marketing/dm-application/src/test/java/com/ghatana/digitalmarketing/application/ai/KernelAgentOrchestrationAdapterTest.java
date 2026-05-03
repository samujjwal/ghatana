package com.ghatana.digitalmarketing.application.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for KernelAgentOrchestrationAdapter (DMOS-P1-018).
 *
 * @doc.type test
 * @doc.purpose Verify Kernel-backed agent orchestration adapter behavior
 * @doc.layer application
 */
@DisplayName("KernelAgentOrchestrationAdapter")
class KernelAgentOrchestrationAdapterTest {

    @Test
    @DisplayName("invokeAgent returns fallback when disabled")
    void invokeAgent_returnsFallbackWhenDisabled() {
        KernelAgentOrchestrationAdapter adapter = new KernelAgentOrchestrationAdapter("http://kernel:8080", false);

        DmAgentOrchestrationPort.AgentResponse response = adapter.invokeAgent(
            DmAgentOrchestrationPort.AgentType.STRATEGY_GENERATOR,
            "Generate a strategy",
            "gpt-4",
            Map.of(),
            Duration.ofSeconds(30)
        ).getResult();

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("disabled");
    }

    @Test
    @DisplayName("invokeAgent returns success when enabled")
    void invokeAgent_returnsSuccessWhenEnabled() {
        KernelAgentOrchestrationAdapter adapter = new KernelAgentOrchestrationAdapter("http://kernel:8080", true);

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
        assertThat(response.confidence()).isGreaterThan(0);
        assertThat(response.evidenceLocation()).isNotNull();
        assertThat(response.duration()).isNotNull();
    }

    @Test
    @DisplayName("isAvailable returns false when disabled")
    void isAvailable_returnsFalseWhenDisabled() {
        KernelAgentOrchestrationAdapter adapter = new KernelAgentOrchestrationAdapter("http://kernel:8080", false);

        Boolean available = adapter.isAvailable().getResult();

        assertThat(available).isFalse();
    }

    @Test
    @DisplayName("isAvailable returns true when enabled")
    void isAvailable_returnsTrueWhenEnabled() {
        KernelAgentOrchestrationAdapter adapter = new KernelAgentOrchestrationAdapter("http://kernel:8080", true);

        Boolean available = adapter.isAvailable().getResult();

        assertThat(available).isTrue();
    }

    @Test
    @DisplayName("getHealthStatus returns UNAVAILABLE when disabled")
    void getHealthStatus_returnsUnavailableWhenDisabled() {
        KernelAgentOrchestrationAdapter adapter = new KernelAgentOrchestrationAdapter("http://kernel:8080", false);

        DmAgentOrchestrationPort.AgentHealthStatus status = adapter.getHealthStatus().getResult();

        assertThat(status).isEqualTo(DmAgentOrchestrationPort.AgentHealthStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("getHealthStatus returns HEALTHY when enabled")
    void getHealthStatus_returnsHealthyWhenEnabled() {
        KernelAgentOrchestrationAdapter adapter = new KernelAgentOrchestrationAdapter("http://kernel:8080", true);

        DmAgentOrchestrationPort.AgentHealthStatus status = adapter.getHealthStatus().getResult();

        assertThat(status).isEqualTo(DmAgentOrchestrationPort.AgentHealthStatus.HEALTHY);
    }
}
