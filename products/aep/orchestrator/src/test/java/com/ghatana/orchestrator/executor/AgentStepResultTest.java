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
    @DisplayName("Should create successful result with all fields [GH-90000]")
    void shouldCreateSuccessfulResult() { // GH-90000
        Instant startTime = Instant.now().minusSeconds(10); // GH-90000
        Instant endTime = Instant.now(); // GH-90000
        Object result = "test result";
        Map<String, Object> metrics = Map.of("key1", "value1", "key2", 42); // GH-90000
        Map<String, String> context = Map.of("tenant", "test-tenant", "pipeline", "test-pipeline"); // GH-90000

        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-1 [GH-90000]")
                .agentId("agent-1 [GH-90000]")
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
    @DisplayName("Should create failed result with error [GH-90000]")
    void shouldCreateFailedResultWithError() { // GH-90000
        Instant startTime = Instant.now().minusSeconds(5); // GH-90000
        Instant endTime = Instant.now(); // GH-90000
        Exception error = new RuntimeException("Test error [GH-90000]");

        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-2 [GH-90000]")
                .agentId("agent-2 [GH-90000]")
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
    @DisplayName("Should calculate duration correctly [GH-90000]")
    void shouldCalculateDurationCorrectly() { // GH-90000
        Instant startTime = Instant.ofEpochMilli(1000); // GH-90000
        Instant endTime = Instant.ofEpochMilli(3500); // GH-90000

        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-3 [GH-90000]")
                .agentId("agent-3 [GH-90000]")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(startTime) // GH-90000
                .endTime(endTime) // GH-90000
                .build(); // GH-90000

        assertEquals(2500, stepResult.getDurationMs()); // GH-90000
    }

    @Test
    @DisplayName("Should return -1 duration when end time is null [GH-90000]")
    void shouldReturnNegativeDurationWhenEndTimeIsNull() { // GH-90000
        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-4 [GH-90000]")
                .agentId("agent-4 [GH-90000]")
                .status(ExecutionStatus.RETRY) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        assertEquals(-1, stepResult.getDurationMs()); // GH-90000
    }

    @Test
    @DisplayName("Should identify success status correctly [GH-90000]")
    void shouldIdentifySuccessStatusCorrectly() { // GH-90000
        AgentStepResult successResult = AgentStepResult.builder() // GH-90000
                .stepId("step-5 [GH-90000]")
                .agentId("agent-5 [GH-90000]")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        AgentStepResult failedResult = AgentStepResult.builder() // GH-90000
                .stepId("step-6 [GH-90000]")
                .agentId("agent-6 [GH-90000]")
                .status(ExecutionStatus.FAILED) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        assertTrue(successResult.isSuccess()); // GH-90000
        assertFalse(failedResult.isSuccess()); // GH-90000
    }

    @Test
    @DisplayName("Should identify retry status correctly [GH-90000]")
    void shouldIdentifyRetryStatusCorrectly() { // GH-90000
        AgentStepResult retryResult = AgentStepResult.builder() // GH-90000
                .stepId("step-7 [GH-90000]")
                .agentId("agent-7 [GH-90000]")
                .status(ExecutionStatus.RETRY) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        AgentStepResult successResult = AgentStepResult.builder() // GH-90000
                .stepId("step-8 [GH-90000]")
                .agentId("agent-8 [GH-90000]")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        assertTrue(retryResult.shouldRetry()); // GH-90000
        assertFalse(successResult.shouldRetry()); // GH-90000
    }

    @Test
    @DisplayName("Should identify failed status correctly [GH-90000]")
    void shouldIdentifyFailedStatusCorrectly() { // GH-90000
        AgentStepResult failedResult = AgentStepResult.builder() // GH-90000
                .stepId("step-9 [GH-90000]")
                .agentId("agent-9 [GH-90000]")
                .status(ExecutionStatus.FAILED) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        AgentStepResult timeoutResult = AgentStepResult.builder() // GH-90000
                .stepId("step-10 [GH-90000]")
                .agentId("agent-10 [GH-90000]")
                .status(ExecutionStatus.TIMEOUT) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        assertTrue(failedResult.isFailed()); // GH-90000
        assertFalse(timeoutResult.isFailed()); // GH-90000
    }

    @Test
    @DisplayName("Should get metric value by key [GH-90000]")
    void shouldGetMetricValueByKey() { // GH-90000
        Map<String, Object> metrics = Map.of("duration", 1000L, "retries", 2, "policy", "default"); // GH-90000

        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-11 [GH-90000]")
                .agentId("agent-11 [GH-90000]")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .metrics(metrics) // GH-90000
                .build(); // GH-90000

        assertEquals(1000L, stepResult.getMetric("duration [GH-90000]"));
        assertEquals(2, stepResult.getMetric("retries [GH-90000]"));
        assertEquals("default", stepResult.getMetric("policy [GH-90000]"));
        assertNull(stepResult.getMetric("nonexistent [GH-90000]"));
    }

    @Test
    @DisplayName("Should get context value by key [GH-90000]")
    void shouldGetContextValueByKey() { // GH-90000
        Map<String, String> context = Map.of( // GH-90000
                "tenant", "test-tenant",
                "pipeline", "test-pipeline",
                "version", "1.0");

        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-12 [GH-90000]")
                .agentId("agent-12 [GH-90000]")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .context(context) // GH-90000
                .build(); // GH-90000

        assertEquals("test-tenant", stepResult.getContextValue("tenant [GH-90000]"));
        assertEquals("test-pipeline", stepResult.getContextValue("pipeline [GH-90000]"));
        assertEquals("1.0", stepResult.getContextValue("version [GH-90000]"));
        assertNull(stepResult.getContextValue("nonexistent [GH-90000]"));
    }

    @Test
    @DisplayName("Should require non-null required fields [GH-90000]")
    void shouldRequireNonNullRequiredFields() { // GH-90000
        AgentStepResult.Builder builder = AgentStepResult.builder(); // GH-90000

        assertThrows(NullPointerException.class, () -> builder.build(), "Should throw when stepId is null"); // GH-90000

        assertThrows( // GH-90000
                NullPointerException.class,
                () -> builder.stepId("step-1 [GH-90000]").build(),
                "Should throw when agentId is null");

        assertThrows( // GH-90000
                NullPointerException.class,
                () -> builder.stepId("step-1 [GH-90000]").agentId("agent-1 [GH-90000]").build(),
                "Should throw when status is null");

        assertThrows( // GH-90000
                NullPointerException.class,
                () -> builder.stepId("step-1 [GH-90000]")
                        .agentId("agent-1 [GH-90000]")
                        .status(ExecutionStatus.SUCCESS) // GH-90000
                        .build(), // GH-90000
                "Should throw when startTime is null");
    }

    @Test
    @DisplayName("Should have meaningful toString representation [GH-90000]")
    void shouldHaveMeaningfulToString() { // GH-90000
        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-13 [GH-90000]")
                .agentId("agent-13 [GH-90000]")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now().minusSeconds(2)) // GH-90000
                .endTime(Instant.now()) // GH-90000
                .attemptNumber(1) // GH-90000
                .totalAttempts(3) // GH-90000
                .build(); // GH-90000

        String toString = stepResult.toString(); // GH-90000

        assertNotNull(toString); // GH-90000
        assertTrue(toString.contains("AgentStepResult [GH-90000]"));
        assertTrue(toString.contains("step-13 [GH-90000]"));
        assertTrue(toString.contains("agent-13 [GH-90000]"));
        assertTrue(toString.contains("SUCCESS [GH-90000]"));
        assertTrue(toString.contains("1/3 [GH-90000]"));
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly [GH-90000]")
    void shouldImplementEqualsAndHashCodeCorrectly() { // GH-90000
        Instant startTime = Instant.now(); // GH-90000
        Instant endTime = startTime.plusSeconds(5); // GH-90000

        AgentStepResult result1 = AgentStepResult.builder() // GH-90000
                .stepId("step-14 [GH-90000]")
                .agentId("agent-14 [GH-90000]")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(startTime) // GH-90000
                .endTime(endTime) // GH-90000
                .attemptNumber(1) // GH-90000
                .totalAttempts(3) // GH-90000
                .build(); // GH-90000

        AgentStepResult result2 = AgentStepResult.builder() // GH-90000
                .stepId("step-14 [GH-90000]")
                .agentId("agent-14 [GH-90000]")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(startTime) // GH-90000
                .endTime(endTime) // GH-90000
                .attemptNumber(1) // GH-90000
                .totalAttempts(3) // GH-90000
                .build(); // GH-90000

        AgentStepResult result3 = AgentStepResult.builder() // GH-90000
                .stepId("step-15 [GH-90000]") // Different stepId
                .agentId("agent-14 [GH-90000]")
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
    @DisplayName("Should handle empty metrics and context maps [GH-90000]")
    void shouldHandleEmptyMapsAndContext() { // GH-90000
        AgentStepResult stepResult = AgentStepResult.builder() // GH-90000
                .stepId("step-16 [GH-90000]")
                .agentId("agent-16 [GH-90000]")
                .status(ExecutionStatus.SUCCESS) // GH-90000
                .startTime(Instant.now()) // GH-90000
                .build(); // GH-90000

        assertNotNull(stepResult.getMetrics()); // GH-90000
        assertNotNull(stepResult.getContext()); // GH-90000
        assertTrue(stepResult.getMetrics().isEmpty()); // GH-90000
        assertTrue(stepResult.getContext().isEmpty()); // GH-90000
    }
}
