/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Day 40: Unit tests for AgentExecutionPolicy configuration and behavior.
 */
class AgentExecutionPolicyTest {

    private AgentExecutionPolicy.Builder builder;

    @BeforeEach
    void setUp() {
        builder = AgentExecutionPolicy.builder();
    }

    @Test
    @DisplayName("Should create policy with default values")
    void shouldCreatePolicyWithDefaults() {
        AgentExecutionPolicy policy = AgentExecutionPolicy.defaultPolicy();

        assertEquals(Duration.ofMinutes(10), policy.getStepTimeout());
        assertEquals(3, policy.getMaxRetries());
        assertEquals(Duration.ofSeconds(1), policy.getInitialBackoff());
        assertEquals(Duration.ofMinutes(5), policy.getMaxBackoff());
        assertEquals(2.0, policy.getBackoffMultiplier());
        assertTrue(policy.isJitterEnabled());
        assertEquals(Duration.ofMinutes(30), policy.getDeadLetterDelay());
        assertTrue(policy.isEmitResultEvents());
        assertEquals(5, policy.getConcurrentExecutions());
    }

    @Test
    @DisplayName("Should create fast policy with appropriate values")
    void shouldCreateFastPolicy() {
        AgentExecutionPolicy policy = AgentExecutionPolicy.fastPolicy();

        assertEquals(Duration.ofMinutes(2), policy.getStepTimeout());
        assertEquals(5, policy.getMaxRetries());
        assertEquals(Duration.ofSeconds(1), policy.getInitialBackoff());
        assertEquals(Duration.ofMinutes(1), policy.getMaxBackoff());
        assertEquals(1.5, policy.getBackoffMultiplier());
        assertEquals(10, policy.getConcurrentExecutions());
    }

    @Test
    @DisplayName("Should create long-running policy with appropriate values")
    void shouldCreateLongRunningPolicy() {
        AgentExecutionPolicy policy = AgentExecutionPolicy.longRunningPolicy();

        assertEquals(Duration.ofHours(1), policy.getStepTimeout());
        assertEquals(2, policy.getMaxRetries());
        assertEquals(Duration.ofMinutes(1), policy.getInitialBackoff());
        assertEquals(Duration.ofMinutes(15), policy.getMaxBackoff());
        assertEquals(1.5, policy.getBackoffMultiplier());
        assertEquals(2, policy.getConcurrentExecutions());
    }

    @Test
    @DisplayName("Should build custom policy with specified values")
    void shouldBuildCustomPolicy() {
        AgentExecutionPolicy policy = builder
            .stepTimeout(Duration.ofMinutes(15))
            .maxRetries(2)
            .initialBackoff(Duration.ofSeconds(2))
            .maxBackoff(Duration.ofMinutes(10))
            .backoffMultiplier(3.0)
            .jitterEnabled(false)
            .deadLetterDelay(Duration.ofHours(1))
            .emitResultEvents(false)
            .concurrentExecutions(8)
            .build();

        assertEquals(Duration.ofMinutes(15), policy.getStepTimeout());
        assertEquals(2, policy.getMaxRetries());
        assertEquals(Duration.ofSeconds(2), policy.getInitialBackoff());
        assertEquals(Duration.ofMinutes(10), policy.getMaxBackoff());
        assertEquals(3.0, policy.getBackoffMultiplier());
        assertFalse(policy.isJitterEnabled());
        assertEquals(Duration.ofHours(1), policy.getDeadLetterDelay());
        assertFalse(policy.isEmitResultEvents());
        assertEquals(8, policy.getConcurrentExecutions());
    }

    @Test
    @DisplayName("Should calculate exponential backoff correctly")
    void shouldCalculateExponentialBackoff() {
        AgentExecutionPolicy policy = builder
            .initialBackoff(Duration.ofSeconds(1))
            .maxBackoff(Duration.ofMinutes(5))
            .backoffMultiplier(2.0)
            .jitterEnabled(false)
            .build();

        // First retry (attempt 1)
        Duration backoff1 = policy.calculateBackoff(1);
        assertEquals(Duration.ofSeconds(1), backoff1);

        // Second retry (attempt 2)
        Duration backoff2 = policy.calculateBackoff(2);
        assertEquals(Duration.ofSeconds(2), backoff2);

        // Third retry (attempt 3)
        Duration backoff3 = policy.calculateBackoff(3);
        assertEquals(Duration.ofSeconds(4), backoff3);
    }

    @Test
    @DisplayName("Should respect maximum backoff limit")
    void shouldRespectMaxBackoffLimit() {
        AgentExecutionPolicy policy = builder
            .initialBackoff(Duration.ofSeconds(30))
            .maxBackoff(Duration.ofMinutes(1))
            .backoffMultiplier(4.0)
            .jitterEnabled(false)
            .build();

        // Large retry attempt should be capped at max backoff
        Duration backoff = policy.calculateBackoff(10);
        assertEquals(Duration.ofMinutes(1), backoff);
    }

    @Test
    @DisplayName("Should apply jitter when enabled")
    void shouldApplyJitterWhenEnabled() {
        AgentExecutionPolicy policy = builder
            .initialBackoff(Duration.ofSeconds(2))
            .backoffMultiplier(2.0)
            .jitterEnabled(true)
            .build();

        Duration baseBackoff = Duration.ofSeconds(2);  // attempt 1
        Duration jitteredBackoff = policy.calculateBackoff(1);

        // Jitter should be between 50% and 100% of base backoff
        long minExpected = (long) (baseBackoff.toMillis() * 0.5);
        long maxExpected = baseBackoff.toMillis();

        assertTrue(jitteredBackoff.toMillis() >= minExpected,
            "Jittered backoff should be at least 50% of base");
        assertTrue(jitteredBackoff.toMillis() <= maxExpected,
            "Jittered backoff should be at most 100% of base");
    }

    @Test
    @DisplayName("Should determine retry eligibility correctly")
    void shouldDetermineRetryEligibility() {
        AgentExecutionPolicy policy = builder
            .maxRetries(3)
            .build();

        assertTrue(policy.shouldRetry(1), "Should retry on attempt 1");
        assertTrue(policy.shouldRetry(2), "Should retry on attempt 2");
        assertTrue(policy.shouldRetry(3), "Should retry on attempt 3");
        assertFalse(policy.shouldRetry(4), "Should not retry after max attempts");
    }

    @Test
    @DisplayName("Should handle zero retry attempts")
    void shouldHandleZeroRetryAttempts() {
        AgentExecutionPolicy policy = builder
            .maxRetries(0)
            .build();

        assertFalse(policy.shouldRetry(1), "Should not retry when max retries is 0");
    }

    @Test
    @DisplayName("Should calculate backoff for attempt 0")
    void shouldCalculateBackoffForAttemptZero() {
        AgentExecutionPolicy policy = builder
            .initialBackoff(Duration.ofSeconds(5))
            .build();

        Duration backoff = policy.calculateBackoff(0);
        assertEquals(Duration.ofSeconds(5), backoff, "Attempt 0 should return initial backoff");
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToString() {
        AgentExecutionPolicy policy = AgentExecutionPolicy.defaultPolicy();
        String toString = policy.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("AgentExecutionPolicy"));
        assertTrue(toString.contains("timeout"));
        assertTrue(toString.contains("maxRetries"));
        assertTrue(toString.contains("initialBackoff"));
    }
}