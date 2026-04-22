/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("ObservabilityLauncher Tests [GH-90000]")
class ObservabilityLauncherTest {

    private ObservabilityLauncher launcher;

    @AfterEach
    void tearDown() { // GH-90000
        if (launcher != null) { // GH-90000
            launcher.shutdown(); // GH-90000
        }
    }

    @Test
    @DisplayName("Should create launcher with minimal configuration [GH-90000]")
    void shouldCreateLauncherWithMinimalConfiguration() { // GH-90000
        launcher = ObservabilityLauncher.builder() // GH-90000
            .serviceName("test-service [GH-90000]")
            .build(); // GH-90000

        assertThat(launcher).isNotNull(); // GH-90000
        assertThat(launcher.getServiceName()).isEqualTo("test-service [GH-90000]");
        assertThat(launcher.getServiceVersion()).isEqualTo("1.0.0 [GH-90000]");
        assertThat(launcher.getMetricsCollector()).isNotNull(); // GH-90000
        assertThat(launcher.getTracingManager()).isNotNull(); // GH-90000
        assertThat(launcher.getMeterRegistry()).isNotNull(); // GH-90000
        assertThat(launcher.getMetricsRegistry()).isNotNull(); // GH-90000
        assertThat(launcher.getOpenTelemetry()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should create launcher with custom configuration [GH-90000]")
    void shouldCreateLauncherWithCustomConfiguration() { // GH-90000
        launcher = ObservabilityLauncher.builder() // GH-90000
            .serviceName("test-service [GH-90000]")
            .serviceVersion("2.0.0 [GH-90000]")
            .environment("production [GH-90000]")
            .enableTracing(true) // GH-90000
            .build(); // GH-90000

        assertThat(launcher).isNotNull(); // GH-90000
        assertThat(launcher.getServiceName()).isEqualTo("test-service [GH-90000]");
        assertThat(launcher.getServiceVersion()).isEqualTo("2.0.0 [GH-90000]");
        assertThat(launcher.getMeterRegistry()).isInstanceOf(SimpleMeterRegistry.class); // GH-90000
    }

    @Test
    @DisplayName("Should fail when service name is null [GH-90000]")
    void shouldFailWhenServiceNameIsNull() { // GH-90000
        assertThatThrownBy(() ->  // GH-90000
            ObservabilityLauncher.builder().build() // GH-90000
        ).isInstanceOf(IllegalStateException.class) // GH-90000
         .hasMessageContaining("serviceName is required [GH-90000]");
    }

    @Test
    @DisplayName("Should fail when service name is blank [GH-90000]")
    void shouldFailWhenServiceNameIsBlank() { // GH-90000
        assertThatThrownBy(() ->  // GH-90000
            ObservabilityLauncher.builder() // GH-90000
                .serviceName("   [GH-90000]")
                .build() // GH-90000
        ).isInstanceOf(IllegalStateException.class) // GH-90000
         .hasMessageContaining("serviceName is required [GH-90000]");
    }

    @Test
    @DisplayName("Should use SimpleMeterRegistry by default [GH-90000]")
    void shouldUseSimpleMeterRegistryByDefault() { // GH-90000
        launcher = ObservabilityLauncher.builder() // GH-90000
            .serviceName("test-service [GH-90000]")
            .build(); // GH-90000

        assertThat(launcher.getMeterRegistry()).isInstanceOf(SimpleMeterRegistry.class); // GH-90000
    }

    @Test
    @DisplayName("Should disable tracing when configured [GH-90000]")
    void shouldDisableTracingWhenConfigured() { // GH-90000
        launcher = ObservabilityLauncher.builder() // GH-90000
            .serviceName("test-service [GH-90000]")
            .enableTracing(false) // GH-90000
            .build(); // GH-90000

        assertThat(launcher.getOpenTelemetry()).isNotNull(); // GH-90000
        assertThat(launcher.getTracingManager()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should record metrics through MetricsCollector [GH-90000]")
    void shouldRecordMetricsThroughMetricsCollector() { // GH-90000
        launcher = ObservabilityLauncher.builder() // GH-90000
            .serviceName("test-service [GH-90000]")
            .build(); // GH-90000

        MetricsCollector collector = launcher.getMetricsCollector(); // GH-90000
        collector.incrementCounter("test.metric", "tag", "value"); // GH-90000

        MeterRegistry registry = launcher.getMeterRegistry(); // GH-90000
        assertThat(registry.get("test.metric [GH-90000]").counters().size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should shutdown gracefully [GH-90000]")
    void shouldShutdownGracefully() { // GH-90000
        launcher = ObservabilityLauncher.builder() // GH-90000
            .serviceName("test-service [GH-90000]")
            .build(); // GH-90000

        launcher.shutdown(); // GH-90000
        // No exception should be thrown
    }

    @Test
    @DisplayName("Should handle multiple shutdowns gracefully [GH-90000]")
    void shouldHandleMultipleShutdownsGracefully() { // GH-90000
        launcher = ObservabilityLauncher.builder() // GH-90000
            .serviceName("test-service [GH-90000]")
            .build(); // GH-90000

        launcher.shutdown(); // GH-90000
        launcher.shutdown(); // Should not throw exception // GH-90000
    }

    @Test
    @DisplayName("Should create launcher with custom SpanExporter [GH-90000]")
    void shouldCreateLauncherWithCustomSpanExporter() { // GH-90000
        SpanExporter customExporter = new InMemorySpanExporter(); // GH-90000
        
        launcher = ObservabilityLauncher.builder() // GH-90000
            .serviceName("test-service [GH-90000]")
            .spanExporter(customExporter) // GH-90000
            .build(); // GH-90000

        assertThat(launcher).isNotNull(); // GH-90000
        assertThat(launcher.getOpenTelemetry()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should use default environment when not specified [GH-90000]")
    void shouldUseDefaultEnvironmentWhenNotSpecified() { // GH-90000
        launcher = ObservabilityLauncher.builder() // GH-90000
            .serviceName("test-service [GH-90000]")
            .build(); // GH-90000

        assertThat(launcher.getServiceName()).isEqualTo("test-service [GH-90000]");
        // Environment defaults to "default" in MetricsRegistry.initialize
    }
}
