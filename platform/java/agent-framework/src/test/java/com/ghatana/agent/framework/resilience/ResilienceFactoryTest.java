/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.resilience;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.FailureMode;
import com.ghatana.platform.resilience.CircuitBreaker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
@DisplayName("ResilienceFactory – Config-Driven Resilience Stack Assembly")
class ResilienceFactoryTest {

    @Test
    @Order(1)
    @DisplayName("1. Default config produces circuit breaker only (no retry, no DLQ)")
    void defaultConfigProducesMinimalStack() {
        AgentConfig config = AgentConfig.builder()
                .agentId("test-agent")
                .type(AgentType.DETERMINISTIC)
                .build();

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config);

        assertThat(stack.getAgentId()).isEqualTo("test-agent");
        assertThat(stack.getCircuitBreaker()).isNotNull();
        assertThat(stack.getCircuitBreaker().getName()).isEqualTo("test-agent");
        assertThat(stack.getCircuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(stack.hasRetry()).isFalse();
        assertThat(stack.hasDlq()).isFalse();
        assertThat(stack.isPassThrough()).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("2. Config with retries produces retry policy")
    void configWithRetriesProducesRetryPolicy() {
        AgentConfig config = AgentConfig.builder()
                .agentId("retry-agent")
                .type(AgentType.PROBABILISTIC)
                .maxRetries(3)
                .retryBackoff(Duration.ofMillis(200))
                .maxRetryBackoff(Duration.ofSeconds(10))
                .build();

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config);

        assertThat(stack.hasRetry()).isTrue();
        assertThat(stack.getRetryPolicy()).isNotNull();
        assertThat(stack.isPassThrough()).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("3. DEAD_LETTER failure mode produces DLQ with defaults")
    void deadLetterModeProducesDlq() {
        AgentConfig config = AgentConfig.builder()
                .agentId("dlq-agent")
                .type(AgentType.REACTIVE)
                .failureMode(FailureMode.DEAD_LETTER)
                .build();

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config);

        assertThat(stack.hasDlq()).isTrue();
        assertThat(stack.getDeadLetterQueue()).isNotNull();
        assertThat(stack.getDeadLetterQueue().isReplayEnabled()).isTrue();
        assertThat(stack.isPassThrough()).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("4. DEAD_LETTER with custom DLQ properties from agent config")
    void customDlqProperties() {
        AgentConfig config = AgentConfig.builder()
                .agentId("custom-dlq-agent")
                .type(AgentType.DETERMINISTIC)
                .failureMode(FailureMode.DEAD_LETTER)
                .properties(Map.of(
                        "dlq.maxSize", "5000",
                        "dlq.ttl", "PT24H",
                        "dlq.enableReplay", "false"
                ))
                .build();

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config);

        assertThat(stack.hasDlq()).isTrue();
        assertThat(stack.getDeadLetterQueue().isReplayEnabled()).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("5. Non-DEAD_LETTER failure modes do not produce DLQ")
    void nonDlqModes() {
        for (FailureMode mode : new FailureMode[]{
                FailureMode.FAIL_FAST, FailureMode.RETRY,
                FailureMode.FALLBACK, FailureMode.SKIP, FailureMode.CIRCUIT_BREAKER}) {

            AgentConfig config = AgentConfig.builder()
                    .agentId("agent-" + mode.name())
                    .type(AgentType.DETERMINISTIC)
                    .failureMode(mode)
                    .build();

            ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config);
            assertThat(stack.hasDlq())
                    .as("FailureMode.%s should not produce DLQ", mode)
                    .isFalse();
        }
    }

    @Test
    @Order(6)
    @DisplayName("6. Circuit breaker threshold and reset from config")
    void circuitBreakerConfigured() {
        AgentConfig config = AgentConfig.builder()
                .agentId("cb-agent")
                .type(AgentType.HYBRID)
                .circuitBreakerThreshold(10)
                .circuitBreakerReset(Duration.ofMinutes(2))
                .build();

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config);

        assertThat(stack.getCircuitBreaker()).isNotNull();
        assertThat(stack.getCircuitBreaker().getName()).isEqualTo("cb-agent");
    }

    @Test
    @Order(7)
    @DisplayName("7. Full resilience stack with retries + DLQ + circuit breaker")
    void fullResilienceStack() {
        AgentConfig config = AgentConfig.builder()
                .agentId("full-resilience")
                .type(AgentType.COMPOSITE)
                .maxRetries(5)
                .retryBackoff(Duration.ofMillis(500))
                .failureMode(FailureMode.DEAD_LETTER)
                .circuitBreakerThreshold(3)
                .build();

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config);

        assertThat(stack.getCircuitBreaker()).isNotNull();
        assertThat(stack.hasRetry()).isTrue();
        assertThat(stack.hasDlq()).isTrue();
        assertThat(stack.isPassThrough()).isFalse();
    }

    @Test
    @Order(8)
    @DisplayName("8. Null config throws NullPointerException")
    void nullConfigThrows() {
        assertThatThrownBy(() -> ResilienceFactory.fromConfig(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("AgentConfig");
    }

    @Test
    @Order(9)
    @DisplayName("9. Zero retries produces no retry policy")
    void zeroRetriesNoRetryPolicy() {
        AgentConfig config = AgentConfig.builder()
                .agentId("no-retry")
                .type(AgentType.DETERMINISTIC)
                .maxRetries(0)
                .build();

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config);
        assertThat(stack.hasRetry()).isFalse();
        assertThat(stack.getRetryPolicy()).isNull();
    }
}
