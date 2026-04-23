/*
 * Copyright (c) 2024 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ghatana.orchestrator.executor.model.AgentStepResult;
import com.ghatana.orchestrator.executor.model.AgentStepResult.ExecutionStatus;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Day 40: Unit tests for AgentStepResult model and behavior.
 */
class AgentStepResultTest {

    @Test
    @DisplayName("Should create successful result with all fields")
    void shouldCreateSuccessfulResult() { // GH-90000
        Instant startTime = Instant.now().minusSeconds(10); // GH-90000
        Instant endTime = Instant.now(); // GH-90000
        Object result = "test result";
        Map<String, Object> metrics = Map.of("key1", "value1", "key2", 42); // GH-90000
        Map<String, String> context = Map.of("tenant", "test-tenant", "pipeline", "test-pipeline"); // GH-90000

        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-1")
                .agentId("agent-1")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .result(result) // GH-90000
                .startTime(startTime) // GH-90000
                .endTime(endTime) // GH-90000
                .attemptNumber(1) // GH-90000
                .totalAttempts(3) // GH-90000
                .metrics(metrics) // GH-90000
                .context(context) // GH-90000
                .build(); // GH-90000

        assertEquals("step-1", stepResult.getStepId()); // GH-90000
        assertEquals("agent-1", stepResult.getId()); // GH-90000
        assertEquals(ExecutionStatus.SUCCESS, stepResult.getStatus()); // GH-90000
        assertEquals(result, stepResult.getResult()); // GH-90000
        assertEquals(startTime, stepResult.getStartTime()); // GH-90000
        assertEquals(endTime, stepResult.getEndTime()); // GH-90000
        assertEquals(1, stepResult.getAttemptNumber()); // GH-90000
        assertEquals(3, stepResult.getTotalAttempts()); // GH-90000
        assertEquals(metrics, stepResult.getMetrics()); // GH-90000
        assertEquals(context, stepResult.getContext()); // GH-90000
    }

    @Test
    @DisplayName("Should create failed result with error")
    void shouldCreateFailedResultWithError() { // GH-90000
        Instant startTime = Instant.now().minusSeconds(5); // GH-90000
        Instant endTime = Instant.now(); // GH-90000
        Exception error = new RuntimeException("Test error");

        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-2")
                .agentId("agent-2")
                .status(ExecutionStatus.FAILED) // GH-90000
                .error(error) // GH-90000
                .startTime(startTime) // GH-90000
                .endTime(endTime) // GH-90000
                .attemptNumber(2) // GH-90000
                .totalAttempts(3) // GH-90000
                .build(); // GH-90000

        assertEquals("step-2", stepResult.getStepId()); // GH-90000
        assertEquals("agent-2", stepResult.getId()); // GH-90000
        assertEquals(ExecutionStatus.FAILED, stepResult.getStatus()); // GH-90000
        assertEquals(error, stepResult.getError()); // GH-90000
        assertNull(stepResult.getResult()); // GH-90000
        assertTrue(stepResult.isFailed()); // GH-90000
        assertFalse(stepResult.isSuccess()); // GH-90000
        assertFalse(stepResult.shouldRetry()); // GH-90000
    }

    @Test
    @DisplayName("Should calculate duration correctly")
    void shouldCalculateDurationCorrectly() { // GH-90000
        Instant startTime = Instant.ofEpochMilli(1000); // GH-90000
        Instant endTime = Instant.ofEpochMilli(3500); // GH-90000

        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-3")
                .agentId("agent-3")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(startTime) // GH-90000
                .endTime(endTime) // GH-90000
                .build(); // GH-90000

        assertEquals(2500, stepResult.getDurationMs()); // GH-90000
    }

    @Test
    @DisplayName("Should return -1 duration when end time is null")
    void shouldReturnNegativeDurationWhenEndTimeIsNull() { // GH-90000
        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-4")
                .agentId("agent-4")
                .status(ExecutionStatus.RETRY) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        assertEquals(-1, stepResult.getDurationMs()); // GH-90000
    }

    @Test
    @DisplayName("Should identify success status correctly")
    void shouldIdentifySuccessStatusCorrectly() { // GH-90000
        AgentStepResult successResult = AgentStepResult.builder() // GH-90000
                .stepId("step-5")
                .agentId("agent-5")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        AgentStepResult failedResult = AgentStepResult.builder() // GH-90000
                .stepId("step-6")
                .agentId("agent-6")
                .status(ExecutionStatus.FAILED) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        assertTrue(successResult.isSuccess()); // GH-90000
        assertFalse(failedResult.isSuccess()); // GH-90000
    }

    @Test
    @DisplayName("Should identify retry status correctly")
    void shouldIdentifyRetryStatusCorrectly() { // GH-90000
        AgentStepResult retryResult = AgentStepResult.builder() // GH-90000
                .stepId("step-7")
                .agentId("agent-7")
                .status(ExecutionStatus.RETRY) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        AgentStepResult successResult = AgentStepResult.builder() // GH-90000
                .stepId("step-8")
                .agentId("agent-8")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        assertTrue(retryResult.shouldRetry()); // GH-90000
        assertFalse(successResult.shouldRetry()); // GH-90000
    }

    @Test
    @DisplayName("Should identify failed status correctly")
    void shouldIdentifyFailedStatusCorrectly() { // GH-90000
        AgentStepResult failedResult = AgentStepResult.builder() // GH-90000
                .stepId("step-9")
                .agentId("agent-9")
                .status(ExecutionStatus.FAILED) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        AgentStepResult timeoutResult = AgentStepResult.builder() // GH-90000
                .stepId("step-10")
                .agentId("agent-10")
                .status(ExecutionStatus.TIMEOUT) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        assertTrue(failedResult.isFailed()); // GH-90000
        assertFalse(timeoutResult.isFailed()); // GH-90000
    }

    @Test
    @DisplayName("Should get metric value by key")
    void shouldGetMetricValueByKey() { // GH-90000
        Map<String, Object> metrics = Map.of("duration", 1000L, "retries", 2, "policy", "default"); // GH-90000

        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-11")
                .agentId("agent-11")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .metrics(metrics) // GH-90000
                .build(); // GH-90000

        assertEquals(1000L, stepResult.getMetric("duration"));
        assertEquals(2, stepResult.getMetric("retries"));
        assertEquals("default", stepResult.getMetric("policy"));
        assertNull(stepResult.getMetric("nonexistent"));
    }

    @Test
    @DisplayName("Should get context value by key")
    void shouldGetContextValueByKey() { // GH-90000
        Map<String, String> context = Map.of( // GH-90000
                "tenant", "test-tenant",
                "pipeline", "test-pipeline",
                "version", "1.0");

        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-12")
                .agentId("agent-12")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .context(context) // GH-90000
                .build(); // GH-90000

        assertEquals("test-tenant", stepResult.getContextValue("tenant"));
        assertEquals("test-pipeline", stepResult.getContextValue("pipeline"));
        assertEquals("1.0", stepResult.getContextValue("version"));
        assertNull(stepResult.getContextValue("nonexistent"));
    }

    @Test
    @DisplayName("Should require non-null required fields")
    void shouldRequireNonNullRequiredFields() { // GH-90000
        AgentStepResult.Builder builder = AgentStepResult.builder(); // GH-90000

        assertThrows(NullPointerException.class, () -> builder.build(), "Should throw when stepId is null"); // GH-90000

        assertThrows( // GH-90000
                NullPointerException.class,
                () -> builder.stepId("step-1").build(),
                "Should throw when agentId is null");

        assertThrows( // GH-90000
                NullPointerException.class,
                () -> builder.stepId("step-1").agentId("agent-1").build(),
                "Should throw when status is null");

        assertThrows( // GH-90000
                NullPointerException.class,
                () -> builder.stepId("step-1")
                        .agentId("agent-1")
                        .status(ExecutionStatus.SUCCESS) // GH-90000
                        .build(), // GH-90000
                "Should throw when startTime is null");
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToString() { // GH-90000
        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-13")
                .agentId("agent-13")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now().minusSeconds(2)) // GH-90000
                .endTime(Instant.now()) // GH-90000
                .attemptNumber(1) // GH-90000
                .totalAttempts(3) // GH-90000
                .build(); // GH-90000

        String toString = stepResult.toString(); // GH-90000

        assertNotNull(toString); // GH-90000
        assertTrue(toString.contains("AgentStepResult"));
        assertTrue(toString.contains("step-13"));
        assertTrue(toString.contains("agent-13"));
        assertTrue(toString.contains("SUCCESS"));
        assertTrue(toString.contains("1/3"));
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() { // GH-90000
        Instant startTime = Instant.now(); // GH-90000
        Instant endTime = startTime.plusSeconds(5); // GH-90000

        AgentStepResult result1 = AgentStepResult.builder() // GH-90000
                .stepId("step-14")
                .agentId("agent-14")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(startTime) // GH-90000
                .endTime(endTime) // GH-90000
                .attemptNumber(1) // GH-90000
                .totalAttempts(3) // GH-90000
                .build(); // GH-90000

        AgentStepResult result2 = AgentStepResult.builder() // GH-90000
                .stepId("step-14")
                .agentId("agent-14")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(startTime) // GH-90000
                .endTime(endTime) // GH-90000
                .attemptNumber(1) // GH-90000
                .totalAttempts(3) // GH-90000
                .build(); // GH-90000

        AgentStepResult result3 = AgentStepResult.builder() // GH-90000
                .stepId("step-15") // Different stepId
                .agentId("agent-14")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(startTime) // GH-90000
                .endTime(endTime) // GH-90000
                .attemptNumber(1) // GH-90000
                .totalAttempts(3) // GH-90000
                .build(); // GH-90000

        assertEquals(result1, result2); // GH-90000
        assertEquals(result1.hashCode(), result2.hashCode()); // GH-90000
        assertNotEquals(result1, result3); // GH-90000
        assertNotEquals(result1.hashCode(), result3.hashCode()); // GH-90000
    }

    @Test
    @DisplayName("Should handle empty metrics and context maps")
    void shouldHandleEmptyMapsAndContext() { // GH-90000
        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-16")
                .agentId("agent-16")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        assertNotNull(stepResult.getMetrics()); // GH-90000
        assertNotNull(stepResult.getContext()); // GH-90000
        assertTrue(stepResult.getMetrics().isEmpty()); // GH-90000
        assertTrue(stepResult.getContext().isEmpty()); // GH-90000
    }
}
