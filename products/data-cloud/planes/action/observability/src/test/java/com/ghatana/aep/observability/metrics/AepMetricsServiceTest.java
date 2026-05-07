/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.observability.metrics;

import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AepMetricsService}.
 *
 * <p>Verifies that every public method delegates to the underlying
 * {@link MetricsCollector} with the correct metric name, count, and tags.
 */
@DisplayName("AepMetricsService")
@ExtendWith(MockitoExtension.class)
class AepMetricsServiceTest {

    @Mock
    private MetricsCollector metricsCollector;

    private AepMetricsService service;

    @BeforeEach
    void setUp() {
        service = new AepMetricsService(metricsCollector);
    }

    // ─── Null constructor guard ───────────────────────────────────────────────

    @Test
    @DisplayName("null MetricsCollector throws NullPointerException")
    void nullCollectorThrows() {
        assertThatThrownBy(() -> new AepMetricsService(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("metricsCollector");
    }

    // ─── Pipeline metrics ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pipeline deployment metrics")
    class PipelineMetrics {

        @Test
        @DisplayName("recordPipelineDeploymentStarted increments METRIC_PIPELINE_DEPLOYED")
        void startedIncrementsDeployedCounter() {
            service.recordPipelineDeploymentStarted("pipe-1", "tenant-x");

            verify(metricsCollector).incrementCounter(
                    AepMetricsService.METRIC_PIPELINE_DEPLOYED,
                    "pipeline_id", "pipe-1",
                    "tenant_id", "tenant-x");
        }

        @Test
        @DisplayName("recordPipelineDeploymentSucceeded increments METRIC_PIPELINE_SUCCEEDED")
        void succeededIncrementsSucceededCounter() {
            service.recordPipelineDeploymentSucceeded("pipe-1", "tenant-x");

            verify(metricsCollector).incrementCounter(
                    AepMetricsService.METRIC_PIPELINE_SUCCEEDED,
                    "pipeline_id", "pipe-1",
                    "tenant_id", "tenant-x");
        }

        @Test
        @DisplayName("recordPipelineDeploymentFailed increments METRIC_PIPELINE_FAILED and records error")
        void failedIncrementsFailed() {
            RuntimeException cause = new RuntimeException("deploy error");
            service.recordPipelineDeploymentFailed("pipe-1", "tenant-x", cause);

            verify(metricsCollector).incrementCounter(
                    AepMetricsService.METRIC_PIPELINE_FAILED,
                    "pipeline_id", "pipe-1",
                    "tenant_id", "tenant-x");
            verify(metricsCollector).recordError(
                    org.mockito.ArgumentMatchers.eq(AepMetricsService.METRIC_PIPELINE_FAILED),
                    org.mockito.ArgumentMatchers.eq(cause),
                    org.mockito.ArgumentMatchers.anyMap());
        }
    }

    // ─── Agent execution metrics ──────────────────────────────────────────────

    @Nested
    @DisplayName("Agent execution metrics")
    class AgentExecutionMetrics {

        @Test
        @DisplayName("recordAgentExecutionStarted increments METRIC_AGENT_EXECUTED")
        void startedIncrementsExecutedCounter() {
            service.recordAgentExecutionStarted("agent-42", "tenant-y");

            verify(metricsCollector).incrementCounter(
                    AepMetricsService.METRIC_AGENT_EXECUTED,
                    "agent_id", "agent-42",
                    "tenant_id", "tenant-y");
        }

        @Test
        @DisplayName("recordAgentExecutionSucceeded increments METRIC_AGENT_SUCCEEDED and records latency")
        void succeededIncrementsSucceededAndDuration() {
            service.recordAgentExecutionSucceeded("agent-42", "tenant-y", 150L);

            verify(metricsCollector).incrementCounter(
                    AepMetricsService.METRIC_AGENT_SUCCEEDED,
                    "agent_id", "agent-42",
                    "tenant_id", "tenant-y");
            verify(metricsCollector).increment(
                    org.mockito.ArgumentMatchers.eq(AepMetricsService.METRIC_AGENT_DURATION_MS),
                    org.mockito.ArgumentMatchers.eq(150.0d),
                    org.mockito.ArgumentMatchers.anyMap());
        }

        @Test
        @DisplayName("recordAgentExecutionFailed increments METRIC_AGENT_FAILED and records error")
        void failedIncrementsFailed() {
            RuntimeException cause = new RuntimeException("agent error");
            service.recordAgentExecutionFailed("agent-42", "tenant-y", cause);

            verify(metricsCollector).incrementCounter(
                    AepMetricsService.METRIC_AGENT_FAILED,
                    "agent_id", "agent-42",
                    "tenant_id", "tenant-y");
            verify(metricsCollector).recordError(
                    org.mockito.ArgumentMatchers.eq(AepMetricsService.METRIC_AGENT_FAILED),
                    org.mockito.ArgumentMatchers.eq(cause),
                    org.mockito.ArgumentMatchers.anyMap());
        }
    }

    // ─── Registry metrics ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Registry metrics")
    class RegistryMetrics {

        @Test
        @DisplayName("recordAgentRegistered increments METRIC_REGISTRY_REGISTERED")
        void registeredIncrementsCounter() {
            service.recordAgentRegistered("agent-1", "tenant-z");

            verify(metricsCollector).incrementCounter(
                    AepMetricsService.METRIC_REGISTRY_REGISTERED,
                    "agent_id", "agent-1",
                    "tenant_id", "tenant-z");
        }

        @Test
        @DisplayName("recordAgentUnregistered increments METRIC_REGISTRY_UNREGISTERED")
        void unregisteredIncrementsCounter() {
            service.recordAgentUnregistered("agent-1", "tenant-z");

            verify(metricsCollector).incrementCounter(
                    AepMetricsService.METRIC_REGISTRY_UNREGISTERED,
                    "agent_id", "agent-1",
                    "tenant_id", "tenant-z");
        }
    }
}
