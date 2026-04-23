/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

@TestMethodOrder(OrderAnnotation.class) // GH-90000
@DisplayName("ResilienceFactory – Config-Driven Resilience Stack Assembly")
class ResilienceFactoryTest {

    @Test
    @Order(1) // GH-90000
    @DisplayName("1. Default config produces circuit breaker only (no retry, no DLQ)")
    void defaultConfigProducesMinimalStack() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("test-agent")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .build(); // GH-90000

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config); // GH-90000

        assertThat(stack.getAgentId()).isEqualTo("test-agent");
        assertThat(stack.getCircuitBreaker()).isNotNull(); // GH-90000
        assertThat(stack.getCircuitBreaker().getName()).isEqualTo("test-agent");
        assertThat(stack.getCircuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        assertThat(stack.hasRetry()).isFalse(); // GH-90000
        assertThat(stack.hasDlq()).isFalse(); // GH-90000
        assertThat(stack.isPassThrough()).isTrue(); // GH-90000
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("2. Config with retries produces retry policy")
    void configWithRetriesProducesRetryPolicy() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("retry-agent")
                .type(AgentType.PROBABILISTIC) // GH-90000
                .maxRetries(3) // GH-90000
                .retryBackoff(Duration.ofMillis(200)) // GH-90000
                .maxRetryBackoff(Duration.ofSeconds(10)) // GH-90000
                .build(); // GH-90000

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config); // GH-90000

        assertThat(stack.hasRetry()).isTrue(); // GH-90000
        assertThat(stack.getRetryPolicy()).isNotNull(); // GH-90000
        assertThat(stack.isPassThrough()).isFalse(); // GH-90000
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("3. DEAD_LETTER failure mode produces DLQ with defaults")
    void deadLetterModeProducesDlq() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("dlq-agent")
                .type(AgentType.REACTIVE) // GH-90000
                .failureMode(FailureMode.DEAD_LETTER) // GH-90000
                .build(); // GH-90000

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config); // GH-90000

        assertThat(stack.hasDlq()).isTrue(); // GH-90000
        assertThat(stack.getDeadLetterQueue()).isNotNull(); // GH-90000
        assertThat(stack.getDeadLetterQueue().isReplayEnabled()).isTrue(); // GH-90000
        assertThat(stack.isPassThrough()).isFalse(); // GH-90000
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("4. DEAD_LETTER with custom DLQ properties from agent config")
    void customDlqProperties() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("custom-dlq-agent")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .failureMode(FailureMode.DEAD_LETTER) // GH-90000
                .properties(Map.of( // GH-90000
                        "dlq.maxSize", "5000",
                        "dlq.ttl", "PT24H",
                        "dlq.enableReplay", "false"
                ))
                .build(); // GH-90000

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config); // GH-90000

        assertThat(stack.hasDlq()).isTrue(); // GH-90000
        assertThat(stack.getDeadLetterQueue().isReplayEnabled()).isFalse(); // GH-90000
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("5. Non-DEAD_LETTER failure modes do not produce DLQ")
    void nonDlqModes() { // GH-90000
        for (FailureMode mode : new FailureMode[]{ // GH-90000
                FailureMode.FAIL_FAST, FailureMode.RETRY,
                FailureMode.FALLBACK, FailureMode.SKIP, FailureMode.CIRCUIT_BREAKER}) {

            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("agent-" + mode.name()) // GH-90000
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .failureMode(mode) // GH-90000
                    .build(); // GH-90000

            ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config); // GH-90000
            assertThat(stack.hasDlq()) // GH-90000
                    .as("FailureMode.%s should not produce DLQ", mode) // GH-90000
                    .isFalse(); // GH-90000
        }
    }

    @Test
    @Order(6) // GH-90000
    @DisplayName("6. Circuit breaker threshold and reset from config")
    void circuitBreakerConfigured() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("cb-agent")
                .type(AgentType.HYBRID) // GH-90000
                .circuitBreakerThreshold(10) // GH-90000
                .circuitBreakerReset(Duration.ofMinutes(2)) // GH-90000
                .build(); // GH-90000

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config); // GH-90000

        assertThat(stack.getCircuitBreaker()).isNotNull(); // GH-90000
        assertThat(stack.getCircuitBreaker().getName()).isEqualTo("cb-agent");
    }

    @Test
    @Order(7) // GH-90000
    @DisplayName("7. Full resilience stack with retries + DLQ + circuit breaker")
    void fullResilienceStack() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("full-resilience")
                .type(AgentType.COMPOSITE) // GH-90000
                .maxRetries(5) // GH-90000
                .retryBackoff(Duration.ofMillis(500)) // GH-90000
                .failureMode(FailureMode.DEAD_LETTER) // GH-90000
                .circuitBreakerThreshold(3) // GH-90000
                .build(); // GH-90000

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config); // GH-90000

        assertThat(stack.getCircuitBreaker()).isNotNull(); // GH-90000
        assertThat(stack.hasRetry()).isTrue(); // GH-90000
        assertThat(stack.hasDlq()).isTrue(); // GH-90000
        assertThat(stack.isPassThrough()).isFalse(); // GH-90000
    }

    @Test
    @Order(8) // GH-90000
    @DisplayName("8. Null config throws NullPointerException")
    void nullConfigThrows() { // GH-90000
        assertThatThrownBy(() -> ResilienceFactory.fromConfig(null)) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("AgentConfig");
    }

    @Test
    @Order(9) // GH-90000
    @DisplayName("9. Zero retries produces no retry policy")
    void zeroRetriesNoRetryPolicy() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("no-retry")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .maxRetries(0) // GH-90000
                .build(); // GH-90000

        ResilienceFactory.ResilienceStack stack = ResilienceFactory.fromConfig(config); // GH-90000
        assertThat(stack.hasRetry()).isFalse(); // GH-90000
        assertThat(stack.getRetryPolicy()).isNull(); // GH-90000
    }
}
