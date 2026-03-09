/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor;

import com.ghatana.orchestrator.executor.model.AgentStepResult;
import com.ghatana.orchestrator.executor.model.AgentStepResult.ExecutionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Day 40: Unit tests for AgentStepResult model and behavior.
 */
class AgentStepResultTest {

    @Test
    @DisplayName("Should create successful result with all fields")
    void shouldCreateSuccessfulResult() {
        Instant startTime = Instant.now().minusSeconds(10);
        Instant endTime = Instant.now();
        Object result = "test result";
        Map<String, Object> metrics = Map.of("key1", "value1", "key2", 42);
        Map<String, String> context = Map.of("tenant", "test-tenant", "pipeline", "test-pipeline");

        AgentStepResult stepResult = AgentStepResult.builder()
            .stepId("step-1")
            .agentId("agent-1")
            .status(ExecutionStatus.SUCCESS)
            .result(result)
            .startTime(startTime)
            .endTime(endTime)
            .attemptNumber(1)
            .totalAttempts(3)
            .metrics(metrics)
            .context(context)
            .build();

    assertEquals("step-1", stepResult.getStepId());
    assertEquals("agent-1", stepResult.getId());
        assertEquals(ExecutionStatus.SUCCESS, stepResult.getStatus());
        assertEquals(result, stepResult.getResult());
        assertEquals(startTime, stepResult.getStartTime());
        assertEquals(endTime, stepResult.getEndTime());
        assertEquals(1, stepResult.getAttemptNumber());
        assertEquals(3, stepResult.getTotalAttempts());
        assertEquals(metrics, stepResult.getMetrics());
        assertEquals(context, stepResult.getContext());
    }

    @Test
    @DisplayName("Should create failed result with error")
    void shouldCreateFailedResultWithError() {
        Instant startTime = Instant.now().minusSeconds(5);
        Instant endTime = Instant.now();
        Exception error = new RuntimeException("Test error");

        AgentStepResult stepResult = AgentStepResult.builder()
            .stepId("step-2")
            .agentId("agent-2")
            .status(ExecutionStatus.FAILED)
            .error(error)
            .startTime(startTime)
            .endTime(endTime)
            .attemptNumber(2)
            .totalAttempts(3)
            .build();

    assertEquals("step-2", stepResult.getStepId());
    assertEquals("agent-2", stepResult.getId());
        assertEquals(ExecutionStatus.FAILED, stepResult.getStatus());
        assertEquals(error, stepResult.getError());
        assertNull(stepResult.getResult());
        assertTrue(stepResult.isFailed());
        assertFalse(stepResult.isSuccess());
        assertFalse(stepResult.shouldRetry());
    }

    @Test
    @DisplayName("Should calculate duration correctly")
    void shouldCalculateDurationCorrectly() {
        Instant startTime = Instant.ofEpochMilli(1000);
        Instant endTime = Instant.ofEpochMilli(3500);

        AgentStepResult stepResult = AgentStepResult.builder()
            .stepId("step-3")
            .agentId("agent-3")
            .status(ExecutionStatus.SUCCESS)
            .startTime(startTime)
            .endTime(endTime)
            .build();

        assertEquals(2500, stepResult.getDurationMs());
    }

    @Test
    @DisplayName("Should return -1 duration when end time is null")
    void shouldReturnNegativeDurationWhenEndTimeIsNull() {
        AgentStepResult stepResult = AgentStepResult.builder()
            .stepId("step-4")
            .agentId("agent-4")
            .status(ExecutionStatus.RETRY)
            .startTime(Instant.now())
            .build();

        assertEquals(-1, stepResult.getDurationMs());
    }

    @Test
    @DisplayName("Should identify success status correctly")
    void shouldIdentifySuccessStatusCorrectly() {
        AgentStepResult successResult = AgentStepResult.builder()
            .stepId("step-5")
            .agentId("agent-5")
            .status(ExecutionStatus.SUCCESS)
            .startTime(Instant.now())
            .build();

        AgentStepResult failedResult = AgentStepResult.builder()
            .stepId("step-6")
            .agentId("agent-6")
            .status(ExecutionStatus.FAILED)
            .startTime(Instant.now())
            .build();

        assertTrue(successResult.isSuccess());
        assertFalse(failedResult.isSuccess());
    }

    @Test
    @DisplayName("Should identify retry status correctly")
    void shouldIdentifyRetryStatusCorrectly() {
        AgentStepResult retryResult = AgentStepResult.builder()
            .stepId("step-7")
            .agentId("agent-7")
            .status(ExecutionStatus.RETRY)
            .startTime(Instant.now())
            .build();

        AgentStepResult successResult = AgentStepResult.builder()
            .stepId("step-8")
            .agentId("agent-8")
            .status(ExecutionStatus.SUCCESS)
            .startTime(Instant.now())
            .build();

        assertTrue(retryResult.shouldRetry());
        assertFalse(successResult.shouldRetry());
    }

    @Test
    @DisplayName("Should identify failed status correctly")
    void shouldIdentifyFailedStatusCorrectly() {
        AgentStepResult failedResult = AgentStepResult.builder()
            .stepId("step-9")
            .agentId("agent-9")
            .status(ExecutionStatus.FAILED)
            .startTime(Instant.now())
            .build();

        AgentStepResult timeoutResult = AgentStepResult.builder()
            .stepId("step-10")
            .agentId("agent-10")
            .status(ExecutionStatus.TIMEOUT)
            .startTime(Instant.now())
            .build();

        assertTrue(failedResult.isFailed());
        assertFalse(timeoutResult.isFailed());
    }

    @Test
    @DisplayName("Should get metric value by key")
    void shouldGetMetricValueByKey() {
        Map<String, Object> metrics = Map.of(
            "duration", 1000L,
            "retries", 2,
            "policy", "default"
        );

        AgentStepResult stepResult = AgentStepResult.builder()
            .stepId("step-11")
            .agentId("agent-11")
            .status(ExecutionStatus.SUCCESS)
            .startTime(Instant.now())
            .metrics(metrics)
            .build();

        assertEquals(1000L, stepResult.getMetric("duration"));
        assertEquals(2, stepResult.getMetric("retries"));
        assertEquals("default", stepResult.getMetric("policy"));
        assertNull(stepResult.getMetric("nonexistent"));
    }

    @Test
    @DisplayName("Should get context value by key")
    void shouldGetContextValueByKey() {
        Map<String, String> context = Map.of(
            "tenant", "test-tenant",
            "pipeline", "test-pipeline",
            "version", "1.0"
        );

        AgentStepResult stepResult = AgentStepResult.builder()
            .stepId("step-12")
            .agentId("agent-12")
            .status(ExecutionStatus.SUCCESS)
            .startTime(Instant.now())
            .context(context)
            .build();

        assertEquals("test-tenant", stepResult.getContextValue("tenant"));
        assertEquals("test-pipeline", stepResult.getContextValue("pipeline"));
        assertEquals("1.0", stepResult.getContextValue("version"));
        assertNull(stepResult.getContextValue("nonexistent"));
    }

    @Test
    @DisplayName("Should require non-null required fields")
    void shouldRequireNonNullRequiredFields() {
        AgentStepResult.Builder builder = AgentStepResult.builder();

        assertThrows(NullPointerException.class, () -> 
            builder.build(), "Should throw when stepId is null");

        assertThrows(NullPointerException.class, () -> 
            builder.stepId("step-1").build(), "Should throw when agentId is null");

        assertThrows(NullPointerException.class, () -> 
            builder.stepId("step-1").agentId("agent-1").build(), "Should throw when status is null");

        assertThrows(NullPointerException.class, () -> 
            builder.stepId("step-1").agentId("agent-1").status(ExecutionStatus.SUCCESS).build(), 
            "Should throw when startTime is null");
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToString() {
        AgentStepResult stepResult = AgentStepResult.builder()
            .stepId("step-13")
            .agentId("agent-13")
            .status(ExecutionStatus.SUCCESS)
            .startTime(Instant.now().minusSeconds(2))
            .endTime(Instant.now())
            .attemptNumber(1)
            .totalAttempts(3)
            .build();

        String toString = stepResult.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("AgentStepResult"));
        assertTrue(toString.contains("step-13"));
        assertTrue(toString.contains("agent-13"));
        assertTrue(toString.contains("SUCCESS"));
        assertTrue(toString.contains("1/3"));
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(5);

        AgentStepResult result1 = AgentStepResult.builder()
            .stepId("step-14")
            .agentId("agent-14")
            .status(ExecutionStatus.SUCCESS)
            .startTime(startTime)
            .endTime(endTime)
            .attemptNumber(1)
            .totalAttempts(3)
            .build();

        AgentStepResult result2 = AgentStepResult.builder()
            .stepId("step-14")
            .agentId("agent-14")
            .status(ExecutionStatus.SUCCESS)
            .startTime(startTime)
            .endTime(endTime)
            .attemptNumber(1)
            .totalAttempts(3)
            .build();

        AgentStepResult result3 = AgentStepResult.builder()
            .stepId("step-15")  // Different stepId
            .agentId("agent-14")
            .status(ExecutionStatus.SUCCESS)
            .startTime(startTime)
            .endTime(endTime)
            .attemptNumber(1)
            .totalAttempts(3)
            .build();

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1, result3);
        assertNotEquals(result1.hashCode(), result3.hashCode());
    }

    @Test
    @DisplayName("Should handle empty metrics and context maps")
    void shouldHandleEmptyMapsAndContext() {
        AgentStepResult stepResult = AgentStepResult.builder()
            .stepId("step-16")
            .agentId("agent-16")
            .status(ExecutionStatus.SUCCESS)
            .startTime(Instant.now())
            .build();

        assertNotNull(stepResult.getMetrics());
        assertNotNull(stepResult.getContext());
        assertTrue(stepResult.getMetrics().isEmpty());
        assertTrue(stepResult.getContext().isEmpty());
    }
}