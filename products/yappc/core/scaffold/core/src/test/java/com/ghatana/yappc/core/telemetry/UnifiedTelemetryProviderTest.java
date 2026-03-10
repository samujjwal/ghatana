/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.core.telemetry.model.TelemetryEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles unified telemetry provider test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class UnifiedTelemetryProviderTest extends EventloopTestBase {

    @TempDir Path tempDir;

    private TelemetryProvider telemetryProvider;

    @BeforeEach
    void setUp() {
        // Reset any global state
        TelemetryManager.shutdown();
    }

    @AfterEach
    void tearDown() {
        if (telemetryProvider != null) {
            telemetryProvider.shutdown();
        }
        TelemetryManager.shutdown();
    }

    @Test
    void testSimpleTelemetryProvider() {
        telemetryProvider = UnifiedTelemetryProvider.createSimple("test-service");

        assertNotNull(telemetryProvider);
        assertEquals("test-service", telemetryProvider.getServiceName());
        assertFalse(telemetryProvider.isObservabilityEnabled());
        assertTrue(telemetryProvider.isUsageCollectionEnabled());

        // Test OpenTelemetry components (should be no-op)
        OpenTelemetry otel = telemetryProvider.getOpenTelemetry();
        assertNotNull(otel);

        Tracer tracer = telemetryProvider.getTracer("test");
        assertNotNull(tracer);

        Meter meter = telemetryProvider.getMeter("test");
        assertNotNull(meter);
    }

    @Test
    void testDefaultTelemetryProvider() {
        telemetryProvider =
                UnifiedTelemetryProvider.createDefault("test-service", "http://localhost:4317");

        assertNotNull(telemetryProvider);
        assertEquals("test-service", telemetryProvider.getServiceName());
        assertTrue(telemetryProvider.isObservabilityEnabled());
        assertTrue(telemetryProvider.isUsageCollectionEnabled());

        // Test OpenTelemetry components
        OpenTelemetry otel = telemetryProvider.getOpenTelemetry();
        assertNotNull(otel);

        Tracer tracer = telemetryProvider.getTracer("test-instrumentation");
        assertNotNull(tracer);

        Meter meter = telemetryProvider.getMeter("test-instrumentation");
        assertNotNull(meter);
    }

    @Test
    void testNoOpTelemetryProvider() {
        telemetryProvider = UnifiedTelemetryProvider.createNoop("test-service");

        assertNotNull(telemetryProvider);
        assertEquals("test-service", telemetryProvider.getServiceName());
        assertFalse(telemetryProvider.isObservabilityEnabled());
        assertFalse(telemetryProvider.isUsageCollectionEnabled());
    }

    @Test
    void testBuilderConfiguration() {
        telemetryProvider =
                UnifiedTelemetryProvider.builder()
                        .serviceName("custom-service")
                        .otlpEndpoint("http://custom:4317")
                        .tracingEnabled(true)
                        .metricsEnabled(false)
                        .usageCollectionEnabled(true)
                        .samplingProbability(0.5)
                        .build();

        assertNotNull(telemetryProvider);
        assertEquals("custom-service", telemetryProvider.getServiceName());
        assertTrue(telemetryProvider.isObservabilityEnabled());
        assertTrue(telemetryProvider.isUsageCollectionEnabled());
    }

    @Test
    void testUsageEventRecording() {
        telemetryProvider = UnifiedTelemetryProvider.createSimple("test-service");

        // Enable usage collection
        telemetryProvider.setUsageCollectionEnabled(true);
        assertTrue(telemetryProvider.isUsageCollectionEnabled());

        // Record a test event
        TelemetryEvent event =
                TelemetryEvent.builder()
                        .eventType("test.event")
                        .command("test-command")
                        .success(true)
                        .durationMs(123L)
                        .build();

        runPromise(() -> telemetryProvider.recordUsageEvent(event));

        // Wait for completion (runPromise already waited)

        // Disable usage collection
        telemetryProvider.setUsageCollectionEnabled(false);
        assertFalse(telemetryProvider.isUsageCollectionEnabled());
    }

    @Test
    void testTelemetryManager() {
        // Test CLI initialization
        TelemetryManager.initializeForCli();
        TelemetryProvider provider = TelemetryManager.getInstance();

        assertNotNull(provider);
        assertEquals("yappc-cli", provider.getServiceName());
        assertFalse(provider.isObservabilityEnabled());
        assertTrue(provider.isUsageCollectionEnabled());

        // Test instrumentation
        TelemetryInstrumentation instrumentation =
                TelemetryManager.getInstrumentation("test-component");
        assertNotNull(instrumentation);

        // Test service initialization
        TelemetryManager.initializeForService("test-service");
        provider = TelemetryManager.getInstance();
        assertEquals("test-service", provider.getServiceName());

        // Test shutdown
        TelemetryManager.shutdown();
        assertFalse(TelemetryManager.isInitialized());
    }

    @Test
    void testTelemetryConfig() {
        // Test CLI config
        TelemetryConfig cliConfig = TelemetryConfig.forCli();
        assertEquals("yappc-cli", cliConfig.getServiceName());
        assertFalse(cliConfig.isTracingEnabled());
        assertFalse(cliConfig.isMetricsEnabled());
        assertTrue(cliConfig.isUsageCollectionEnabled());

        // Test service config
        TelemetryConfig serviceConfig = TelemetryConfig.forService("test-service");
        assertEquals("test-service", serviceConfig.getServiceName());

        // Test build tool config
        TelemetryConfig buildConfig = TelemetryConfig.forBuildTool("maven");
        assertEquals("yappc-maven", buildConfig.getServiceName());
        assertTrue(buildConfig.isTracingEnabled());
        assertTrue(buildConfig.isMetricsEnabled());
        assertEquals(0.1, buildConfig.getSamplingProbability(), 0.001);

        // Test disabled config
        TelemetryConfig disabledConfig = TelemetryConfig.disabled();
        assertFalse(disabledConfig.isTracingEnabled());
        assertFalse(disabledConfig.isMetricsEnabled());
        assertFalse(disabledConfig.isUsageCollectionEnabled());
    }

    @Test
    void testTelemetryInstrumentation() {
        telemetryProvider =
                UnifiedTelemetryProvider.createDefault("test-service", "http://localhost:4317");
        TelemetryInstrumentation instrumentation =
                new TelemetryInstrumentation(telemetryProvider, "test");

        // Test command instrumentation
        assertDoesNotThrow(
                () -> {
                    Integer result = instrumentation.instrumentCommand("test-command", () -> 42);
                    assertEquals(42, result);
                });

        // Test build operation instrumentation
        assertDoesNotThrow(
                () -> {
                    String result =
                            instrumentation.instrumentBuildOperation(
                                    "compile", "java", "spring", () -> "success");
                    assertEquals("success", result);
                });

        // Test simple operation instrumentation
        assertDoesNotThrow(
                () -> {
                    String result =
                            instrumentation.instrumentOperation(
                                    "test-op",
                                    span -> {
                                        span.setAttribute("test.attribute", "value");
                                        return "completed";
                                    });
                    assertEquals("completed", result);
                });

        // Test metrics recording
        assertDoesNotThrow(
                () -> {
                    instrumentation.recordMetric(
                            "test.metric",
                            123L,
                            Attributes.of(AttributeKey.stringKey("test"), "value"));
                    instrumentation.recordDuration(
                            "test.duration",
                            456L,
                            Attributes.of(AttributeKey.stringKey("operation"), "test"));
                });
    }

    @Test
    void testErrorHandling() {
        telemetryProvider =
                UnifiedTelemetryProvider.createDefault("test-service", "http://localhost:4317");
        TelemetryInstrumentation instrumentation =
                new TelemetryInstrumentation(telemetryProvider, "test");

        // Test command instrumentation with exception
        RuntimeException testException = new RuntimeException("Test error");

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            instrumentation.instrumentCommand(
                                    "failing-command",
                                    () -> {
                                        throw testException;
                                    });
                        });

        assertEquals("Test error", thrown.getMessage());
    }
}
