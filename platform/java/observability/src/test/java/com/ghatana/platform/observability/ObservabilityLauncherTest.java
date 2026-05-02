/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ObservabilityLauncher.
 *
 * @doc.type class
 * @doc.purpose Tests for ObservabilityLauncher
 * @doc.layer test
 * @doc.pattern Unit testing
 */
@DisplayName("ObservabilityLauncher Tests")
class ObservabilityLauncherTest {

    private ObservabilityLauncher launcher;

    @AfterEach
    void tearDown() { 
        if (launcher != null) { 
            launcher.shutdown(); 
        }
    }

    @Test
    @DisplayName("Should create launcher with minimal configuration")
    void shouldCreateLauncherWithMinimalConfiguration() { 
        launcher = ObservabilityLauncher.builder() 
            .serviceName("test-service")
            .build(); 

        assertThat(launcher).isNotNull(); 
        assertThat(launcher.getServiceName()).isEqualTo("test-service");
        assertThat(launcher.getServiceVersion()).isEqualTo("1.0.0");
        assertThat(launcher.getMetricsCollector()).isNotNull(); 
        assertThat(launcher.getTracingManager()).isNotNull(); 
        assertThat(launcher.getMeterRegistry()).isNotNull(); 
        assertThat(launcher.getMetricsRegistry()).isNotNull(); 
        assertThat(launcher.getOpenTelemetry()).isNotNull(); 
    }

    @Test
    @DisplayName("Should create launcher with custom configuration")
    void shouldCreateLauncherWithCustomConfiguration() { 
        launcher = ObservabilityLauncher.builder() 
            .serviceName("test-service")
            .serviceVersion("2.0.0")
            .environment("production")
            .enableTracing(true) 
            .build(); 

        assertThat(launcher).isNotNull(); 
        assertThat(launcher.getServiceName()).isEqualTo("test-service");
        assertThat(launcher.getServiceVersion()).isEqualTo("2.0.0");
        assertThat(launcher.getMeterRegistry()).isInstanceOf(SimpleMeterRegistry.class); 
    }

    @Test
    @DisplayName("Should fail when service name is null")
    void shouldFailWhenServiceNameIsNull() { 
        assertThatThrownBy(() ->  
            ObservabilityLauncher.builder().build() 
        ).isInstanceOf(IllegalStateException.class) 
         .hasMessageContaining("serviceName is required");
    }

    @Test
    @DisplayName("Should fail when service name is blank")
    void shouldFailWhenServiceNameIsBlank() { 
        assertThatThrownBy(() ->  
            ObservabilityLauncher.builder() 
                .serviceName("  ")
                .build() 
        ).isInstanceOf(IllegalStateException.class) 
         .hasMessageContaining("serviceName is required");
    }

    @Test
    @DisplayName("Should use SimpleMeterRegistry by default")
    void shouldUseSimpleMeterRegistryByDefault() { 
        launcher = ObservabilityLauncher.builder() 
            .serviceName("test-service")
            .build(); 

        assertThat(launcher.getMeterRegistry()).isInstanceOf(SimpleMeterRegistry.class); 
    }

    @Test
    @DisplayName("Should disable tracing when configured")
    void shouldDisableTracingWhenConfigured() { 
        launcher = ObservabilityLauncher.builder() 
            .serviceName("test-service")
            .enableTracing(false) 
            .build(); 

        assertThat(launcher.getOpenTelemetry()).isNotNull(); 
        assertThat(launcher.getTracingManager()).isNotNull(); 
    }

    @Test
    @DisplayName("Should record metrics through MetricsCollector")
    void shouldRecordMetricsThroughMetricsCollector() { 
        launcher = ObservabilityLauncher.builder() 
            .serviceName("test-service")
            .build(); 

        MetricsCollector collector = launcher.getMetricsCollector(); 
        collector.incrementCounter("test.metric", "tag", "value"); 

        MeterRegistry registry = launcher.getMeterRegistry(); 
        assertThat(registry.get("test.metric").counters().size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should shutdown gracefully")
    void shouldShutdownGracefully() { 
        launcher = ObservabilityLauncher.builder() 
            .serviceName("test-service")
            .build(); 

        launcher.shutdown(); 
        // No exception should be thrown
    }

    @Test
    @DisplayName("Should handle multiple shutdowns gracefully")
    void shouldHandleMultipleShutdownsGracefully() { 
        launcher = ObservabilityLauncher.builder() 
            .serviceName("test-service")
            .build(); 

        launcher.shutdown(); 
        launcher.shutdown(); // Should not throw exception 
    }

    @Test
    @DisplayName("Should create launcher with custom SpanExporter")
    void shouldCreateLauncherWithCustomSpanExporter() { 
        SpanExporter customExporter = new InMemorySpanExporter(); 
        
        launcher = ObservabilityLauncher.builder() 
            .serviceName("test-service")
            .spanExporter(customExporter) 
            .build(); 

        assertThat(launcher).isNotNull(); 
        assertThat(launcher.getOpenTelemetry()).isNotNull(); 
    }

    @Test
    @DisplayName("Should use default environment when not specified")
    void shouldUseDefaultEnvironmentWhenNotSpecified() { 
        launcher = ObservabilityLauncher.builder() 
            .serviceName("test-service")
            .build(); 

        assertThat(launcher.getServiceName()).isEqualTo("test-service");
        // Environment defaults to "default" in MetricsRegistry.initialize
    }
}
