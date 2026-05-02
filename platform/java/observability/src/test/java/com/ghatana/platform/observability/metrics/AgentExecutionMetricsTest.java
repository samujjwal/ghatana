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
    void setUp() { 
        registry = new SimpleMeterRegistry(); 
        metrics = new AgentExecutionMetrics("cognitive-agent", "tenant-alpha", "pipeline-123"); 
        metrics.bindTo(registry); 
    }

    @Test
    void shouldRecordSuccessfulExecution() { 
        // When
        metrics.recordExecution(System.currentTimeMillis() - 100, true); 

        // Then
        assertThat(registry.find("agent.execution.success").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("agent.execution.failure").counter().count()).isEqualTo(0.0);
        assertThat(registry.find("agent.execution.duration").timer().count()).isEqualTo(1L);
    }

    @Test
    void shouldRecordFailedExecution() { 
        // When
        metrics.recordExecution(System.currentTimeMillis() - 50, false); 

        // Then
        assertThat(registry.find("agent.execution.failure").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("agent.execution.success").counter().count()).isEqualTo(0.0);
    }

    @Test
    void shouldRecordStall() { 
        // When
        metrics.recordStall(); 

        // Then
        assertThat(registry.find("agent.execution.stalls").counter().count()).isEqualTo(1.0);
    }
}
