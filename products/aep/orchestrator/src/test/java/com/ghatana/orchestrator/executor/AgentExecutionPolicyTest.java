/*
 * Copyright (c) 2024 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Day 40: Unit tests for AgentExecutionPolicy configuration and behavior.
 */
class AgentExecutionPolicyTest {

    private AgentExecutionPolicy.Builder builder;

    @BeforeEach
    void setUp() { // GH-90000
        builder = AgentExecutionPolicy.builder(); // GH-90000
    }

    @Test
    @DisplayName("Should create policy with default values [GH-90000]")
    void shouldCreatePolicyWithDefaults() { // GH-90000
        AgentExecutionPolicy policy = AgentExecutionPolicy.defaultPolicy(); // GH-90000

        assertEquals(Duration.ofMinutes(10), policy.getStepTimeout()); // GH-90000
        assertEquals(3, policy.getMaxRetries()); // GH-90000
        assertEquals(Duration.ofSeconds(1), policy.getInitialBackoff()); // GH-90000
        assertEquals(Duration.ofMinutes(5), policy.getMaxBackoff()); // GH-90000
        assertEquals(2.0, policy.getBackoffMultiplier()); // GH-90000
        assertTrue(policy.isJitterEnabled()); // GH-90000
        assertEquals(Duration.ofMinutes(30), policy.getDeadLetterDelay()); // GH-90000
        assertTrue(policy.isEmitResultEvents()); // GH-90000
        assertEquals(5, policy.getConcurrentExecutions()); // GH-90000
    }

    @Test
    @DisplayName("Should create fast policy with appropriate values [GH-90000]")
    void shouldCreateFastPolicy() { // GH-90000
        AgentExecutionPolicy policy = AgentExecutionPolicy.fastPolicy(); // GH-90000

        assertEquals(Duration.ofMinutes(2), policy.getStepTimeout()); // GH-90000
        assertEquals(5, policy.getMaxRetries()); // GH-90000
        assertEquals(Duration.ofSeconds(1), policy.getInitialBackoff()); // GH-90000
        assertEquals(Duration.ofMinutes(1), policy.getMaxBackoff()); // GH-90000
        assertEquals(1.5, policy.getBackoffMultiplier()); // GH-90000
        assertEquals(10, policy.getConcurrentExecutions()); // GH-90000
    }

    @Test
    @DisplayName("Should create long-running policy with appropriate values [GH-90000]")
    void shouldCreateLongRunningPolicy() { // GH-90000
        AgentExecutionPolicy policy = AgentExecutionPolicy.longRunningPolicy(); // GH-90000

        assertEquals(Duration.ofHours(1), policy.getStepTimeout()); // GH-90000
        assertEquals(2, policy.getMaxRetries()); // GH-90000
        assertEquals(Duration.ofMinutes(1), policy.getInitialBackoff()); // GH-90000
        assertEquals(Duration.ofMinutes(15), policy.getMaxBackoff()); // GH-90000
        assertEquals(1.5, policy.getBackoffMultiplier()); // GH-90000
        assertEquals(2, policy.getConcurrentExecutions()); // GH-90000
    }

    @Test
    @DisplayName("Should build custom policy with specified values [GH-90000]")
    void shouldBuildCustomPolicy() { // GH-90000
        AgentExecutionPolicy policy = builder.stepTimeout(Duration.ofMinutes(15)) // GH-90000
                .maxRetries(2) // GH-90000
                .initialBackoff(Duration.ofSeconds(2)) // GH-90000
                .maxBackoff(Duration.ofMinutes(10)) // GH-90000
                .backoffMultiplier(3.0) // GH-90000
                .jitterEnabled(false) // GH-90000
                .deadLetterDelay(Duration.ofHours(1)) // GH-90000
                .emitResultEvents(false) // GH-90000
                .concurrentExecutions(8) // GH-90000
                .build(); // GH-90000

        assertEquals(Duration.ofMinutes(15), policy.getStepTimeout()); // GH-90000
        assertEquals(2, policy.getMaxRetries()); // GH-90000
        assertEquals(Duration.ofSeconds(2), policy.getInitialBackoff()); // GH-90000
        assertEquals(Duration.ofMinutes(10), policy.getMaxBackoff()); // GH-90000
        assertEquals(3.0, policy.getBackoffMultiplier()); // GH-90000
        assertFalse(policy.isJitterEnabled()); // GH-90000
        assertEquals(Duration.ofHours(1), policy.getDeadLetterDelay()); // GH-90000
        assertFalse(policy.isEmitResultEvents()); // GH-90000
        assertEquals(8, policy.getConcurrentExecutions()); // GH-90000
    }

    @Test
    @DisplayName("Should calculate exponential backoff correctly [GH-90000]")
    void shouldCalculateExponentialBackoff() { // GH-90000
        AgentExecutionPolicy policy = builder.initialBackoff(Duration.ofSeconds(1)) // GH-90000
                .maxBackoff(Duration.ofMinutes(5)) // GH-90000
                .backoffMultiplier(2.0) // GH-90000
                .jitterEnabled(false) // GH-90000
                .build(); // GH-90000

        // First retry (attempt 1) // GH-90000
        Duration backoff1 = policy.calculateBackoff(1); // GH-90000
        assertEquals(Duration.ofSeconds(1), backoff1); // GH-90000

        // Second retry (attempt 2) // GH-90000
        Duration backoff2 = policy.calculateBackoff(2); // GH-90000
        assertEquals(Duration.ofSeconds(2), backoff2); // GH-90000

        // Third retry (attempt 3) // GH-90000
        Duration backoff3 = policy.calculateBackoff(3); // GH-90000
        assertEquals(Duration.ofSeconds(4), backoff3); // GH-90000
    }

    @Test
    @DisplayName("Should respect maximum backoff limit [GH-90000]")
    void shouldRespectMaxBackoffLimit() { // GH-90000
        AgentExecutionPolicy policy = builder.initialBackoff(Duration.ofSeconds(30)) // GH-90000
                .maxBackoff(Duration.ofMinutes(1)) // GH-90000
                .backoffMultiplier(4.0) // GH-90000
                .jitterEnabled(false) // GH-90000
                .build(); // GH-90000

        // Large retry attempt should be capped at max backoff
        Duration backoff = policy.calculateBackoff(10); // GH-90000
        assertEquals(Duration.ofMinutes(1), backoff); // GH-90000
    }

    @Test
    @DisplayName("Should apply jitter when enabled [GH-90000]")
    void shouldApplyJitterWhenEnabled() { // GH-90000
        AgentExecutionPolicy policy = builder.initialBackoff(Duration.ofSeconds(2)) // GH-90000
                .backoffMultiplier(2.0) // GH-90000
                .jitterEnabled(true) // GH-90000
                .build(); // GH-90000

        Duration baseBackoff = Duration.ofSeconds(2); // attempt 1 // GH-90000
        Duration jitteredBackoff = policy.calculateBackoff(1); // GH-90000

        // Jitter should be between 50% and 100% of base backoff
        long minExpected = (long) (baseBackoff.toMillis() * 0.5); // GH-90000
        long maxExpected = baseBackoff.toMillis(); // GH-90000

        assertTrue(jitteredBackoff.toMillis() >= minExpected, "Jittered backoff should be at least 50% of base"); // GH-90000
        assertTrue(jitteredBackoff.toMillis() <= maxExpected, "Jittered backoff should be at most 100% of base"); // GH-90000
    }

    @Test
    @DisplayName("Should determine retry eligibility correctly [GH-90000]")
    void shouldDetermineRetryEligibility() { // GH-90000
        AgentExecutionPolicy policy = builder.maxRetries(3).build(); // GH-90000

        assertTrue(policy.shouldRetry(1), "Should retry on attempt 1"); // GH-90000
        assertTrue(policy.shouldRetry(2), "Should retry on attempt 2"); // GH-90000
        assertTrue(policy.shouldRetry(3), "Should retry on attempt 3"); // GH-90000
        assertFalse(policy.shouldRetry(4), "Should not retry after max attempts"); // GH-90000
    }

    @Test
    @DisplayName("Should handle zero retry attempts [GH-90000]")
    void shouldHandleZeroRetryAttempts() { // GH-90000
        AgentExecutionPolicy policy = builder.maxRetries(0).build(); // GH-90000

        assertFalse(policy.shouldRetry(1), "Should not retry when max retries is 0"); // GH-90000
    }

    @Test
    @DisplayName("Should calculate backoff for attempt 0 [GH-90000]")
    void shouldCalculateBackoffForAttemptZero() { // GH-90000
        AgentExecutionPolicy policy =
                builder.initialBackoff(Duration.ofSeconds(5)).build(); // GH-90000

        Duration backoff = policy.calculateBackoff(0); // GH-90000
        assertEquals(Duration.ofSeconds(5), backoff, "Attempt 0 should return initial backoff"); // GH-90000
    }

    @Test
    @DisplayName("Should have meaningful toString representation [GH-90000]")
    void shouldHaveMeaningfulToString() { // GH-90000
        AgentExecutionPolicy policy = AgentExecutionPolicy.defaultPolicy(); // GH-90000
        String toString = policy.toString(); // GH-90000

        assertNotNull(toString); // GH-90000
        assertTrue(toString.contains("AgentExecutionPolicy [GH-90000]"));
        assertTrue(toString.contains("timeout [GH-90000]"));
        assertTrue(toString.contains("maxRetries [GH-90000]"));
        assertTrue(toString.contains("initialBackoff [GH-90000]"));
    }
}
