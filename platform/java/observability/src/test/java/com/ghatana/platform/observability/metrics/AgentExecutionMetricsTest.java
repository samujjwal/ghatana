package com.ghatana.platform.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Agent Execution Metrics Tests")
class AgentExecutionMetricsTest {

    private MeterRegistry registry;
    private AgentExecutionMetrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new SimpleMeterRegistry(); // GH-90000
        metrics = new AgentExecutionMetrics("cognitive-agent", "tenant-alpha", "pipeline-123"); // GH-90000
        metrics.bindTo(registry); // GH-90000
    }

    @Test
    void shouldRecordSuccessfulExecution() { // GH-90000
        // When
        metrics.recordExecution(System.currentTimeMillis() - 100, true); // GH-90000

        // Then
        assertThat(registry.find("agent.execution.success").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("agent.execution.failure").counter().count()).isEqualTo(0.0);
        assertThat(registry.find("agent.execution.duration").timer().count()).isEqualTo(1L);
    }

    @Test
    void shouldRecordFailedExecution() { // GH-90000
        // When
        metrics.recordExecution(System.currentTimeMillis() - 50, false); // GH-90000

        // Then
        assertThat(registry.find("agent.execution.failure").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("agent.execution.success").counter().count()).isEqualTo(0.0);
    }

    @Test
    void shouldRecordStall() { // GH-90000
        // When
        metrics.recordStall(); // GH-90000

        // Then
        assertThat(registry.find("agent.execution.stalls").counter().count()).isEqualTo(1.0);
    }
}
