/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
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
    void setUp() { // GH-90000
        // Reset any global state
        TelemetryManager.shutdown(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (telemetryProvider != null) { // GH-90000
            telemetryProvider.shutdown(); // GH-90000
        }
        TelemetryManager.shutdown(); // GH-90000
    }

    @Test
    void testSimpleTelemetryProvider() { // GH-90000
        telemetryProvider = UnifiedTelemetryProvider.createSimple("test-service [GH-90000]");

        assertNotNull(telemetryProvider); // GH-90000
        assertEquals("test-service", telemetryProvider.getServiceName()); // GH-90000
        assertFalse(telemetryProvider.isObservabilityEnabled()); // GH-90000
        assertTrue(telemetryProvider.isUsageCollectionEnabled()); // GH-90000

        // Test OpenTelemetry components (should be no-op) // GH-90000
        OpenTelemetry otel = telemetryProvider.getOpenTelemetry(); // GH-90000
        assertNotNull(otel); // GH-90000

        Tracer tracer = telemetryProvider.getTracer("test [GH-90000]");
        assertNotNull(tracer); // GH-90000

        Meter meter = telemetryProvider.getMeter("test [GH-90000]");
        assertNotNull(meter); // GH-90000
    }

    @Test
    void testDefaultTelemetryProvider() { // GH-90000
        telemetryProvider =
                UnifiedTelemetryProvider.createDefault("test-service", "http://localhost:4317"); // GH-90000

        assertNotNull(telemetryProvider); // GH-90000
        assertEquals("test-service", telemetryProvider.getServiceName()); // GH-90000
        assertTrue(telemetryProvider.isObservabilityEnabled()); // GH-90000
        assertTrue(telemetryProvider.isUsageCollectionEnabled()); // GH-90000

        // Test OpenTelemetry components
        OpenTelemetry otel = telemetryProvider.getOpenTelemetry(); // GH-90000
        assertNotNull(otel); // GH-90000

        Tracer tracer = telemetryProvider.getTracer("test-instrumentation [GH-90000]");
        assertNotNull(tracer); // GH-90000

        Meter meter = telemetryProvider.getMeter("test-instrumentation [GH-90000]");
        assertNotNull(meter); // GH-90000
    }

    @Test
    void testNoOpTelemetryProvider() { // GH-90000
        telemetryProvider = UnifiedTelemetryProvider.createNoop("test-service [GH-90000]");

        assertNotNull(telemetryProvider); // GH-90000
        assertEquals("test-service", telemetryProvider.getServiceName()); // GH-90000
        assertFalse(telemetryProvider.isObservabilityEnabled()); // GH-90000
        assertFalse(telemetryProvider.isUsageCollectionEnabled()); // GH-90000
    }

    @Test
    void testBuilderConfiguration() { // GH-90000
        telemetryProvider =
                UnifiedTelemetryProvider.builder() // GH-90000
                        .serviceName("custom-service [GH-90000]")
                        .otlpEndpoint("http://custom:4317 [GH-90000]")
                        .tracingEnabled(true) // GH-90000
                        .metricsEnabled(false) // GH-90000
                        .usageCollectionEnabled(true) // GH-90000
                        .samplingProbability(0.5) // GH-90000
                        .build(); // GH-90000

        assertNotNull(telemetryProvider); // GH-90000
        assertEquals("custom-service", telemetryProvider.getServiceName()); // GH-90000
        assertTrue(telemetryProvider.isObservabilityEnabled()); // GH-90000
        assertTrue(telemetryProvider.isUsageCollectionEnabled()); // GH-90000
    }

    @Test
    void testUsageEventRecording() { // GH-90000
        telemetryProvider = UnifiedTelemetryProvider.createSimple("test-service [GH-90000]");

        // Enable usage collection
        telemetryProvider.setUsageCollectionEnabled(true); // GH-90000
        assertTrue(telemetryProvider.isUsageCollectionEnabled()); // GH-90000

        // Record a test event
        TelemetryEvent event =
                TelemetryEvent.builder() // GH-90000
                        .eventType("test.event [GH-90000]")
                        .command("test-command [GH-90000]")
                        .success(true) // GH-90000
                        .durationMs(123L) // GH-90000
                        .build(); // GH-90000

        runPromise(() -> telemetryProvider.recordUsageEvent(event)); // GH-90000

        // Wait for completion (runPromise already waited) // GH-90000

        // Disable usage collection
        telemetryProvider.setUsageCollectionEnabled(false); // GH-90000
        assertFalse(telemetryProvider.isUsageCollectionEnabled()); // GH-90000
    }

    @Test
    void testTelemetryManager() { // GH-90000
        // Test CLI initialization
        TelemetryManager.initializeForCli(); // GH-90000
        TelemetryProvider provider = TelemetryManager.getInstance(); // GH-90000

        assertNotNull(provider); // GH-90000
        assertEquals("yappc-cli", provider.getServiceName()); // GH-90000
        assertFalse(provider.isObservabilityEnabled()); // GH-90000
        assertTrue(provider.isUsageCollectionEnabled()); // GH-90000

        // Test instrumentation
        TelemetryInstrumentation instrumentation =
                TelemetryManager.getInstrumentation("test-component [GH-90000]");
        assertNotNull(instrumentation); // GH-90000

        // Test service initialization
        TelemetryManager.initializeForService("test-service [GH-90000]");
        provider = TelemetryManager.getInstance(); // GH-90000
        assertEquals("test-service", provider.getServiceName()); // GH-90000

        // Test shutdown
        TelemetryManager.shutdown(); // GH-90000
        assertFalse(TelemetryManager.isInitialized()); // GH-90000
    }

    @Test
    void testTelemetryConfig() { // GH-90000
        // Test CLI config
        TelemetryConfig cliConfig = TelemetryConfig.forCli(); // GH-90000
        assertEquals("yappc-cli", cliConfig.getServiceName()); // GH-90000
        assertFalse(cliConfig.isTracingEnabled()); // GH-90000
        assertFalse(cliConfig.isMetricsEnabled()); // GH-90000
        assertTrue(cliConfig.isUsageCollectionEnabled()); // GH-90000

        // Test service config
        TelemetryConfig serviceConfig = TelemetryConfig.forService("test-service [GH-90000]");
        assertEquals("test-service", serviceConfig.getServiceName()); // GH-90000

        // Test build tool config
        TelemetryConfig buildConfig = TelemetryConfig.forBuildTool("maven [GH-90000]");
        assertEquals("yappc-maven", buildConfig.getServiceName()); // GH-90000
        assertTrue(buildConfig.isTracingEnabled()); // GH-90000
        assertTrue(buildConfig.isMetricsEnabled()); // GH-90000
        assertEquals(0.1, buildConfig.getSamplingProbability(), 0.001); // GH-90000

        // Test disabled config
        TelemetryConfig disabledConfig = TelemetryConfig.disabled(); // GH-90000
        assertFalse(disabledConfig.isTracingEnabled()); // GH-90000
        assertFalse(disabledConfig.isMetricsEnabled()); // GH-90000
        assertFalse(disabledConfig.isUsageCollectionEnabled()); // GH-90000
    }

    @Test
    void testTelemetryInstrumentation() { // GH-90000
        telemetryProvider =
                UnifiedTelemetryProvider.createDefault("test-service", "http://localhost:4317"); // GH-90000
        TelemetryInstrumentation instrumentation =
                new TelemetryInstrumentation(telemetryProvider, "test"); // GH-90000

        // Test command instrumentation
        assertDoesNotThrow( // GH-90000
                () -> { // GH-90000
                    Integer result = instrumentation.instrumentCommand("test-command", () -> 42); // GH-90000
                    assertEquals(42, result); // GH-90000
                });

        // Test build operation instrumentation
        assertDoesNotThrow( // GH-90000
                () -> { // GH-90000
                    String result =
                            instrumentation.instrumentBuildOperation( // GH-90000
                                    "compile", "java", "spring", () -> "success"); // GH-90000
                    assertEquals("success", result); // GH-90000
                });

        // Test simple operation instrumentation
        assertDoesNotThrow( // GH-90000
                () -> { // GH-90000
                    String result =
                            instrumentation.instrumentOperation( // GH-90000
                                    "test-op",
                                    span -> {
                                        span.setAttribute("test.attribute", "value"); // GH-90000
                                        return "completed";
                                    });
                    assertEquals("completed", result); // GH-90000
                });

        // Test metrics recording
        assertDoesNotThrow( // GH-90000
                () -> { // GH-90000
                    instrumentation.recordMetric( // GH-90000
                            "test.metric",
                            123L,
                            Attributes.of(AttributeKey.stringKey("test [GH-90000]"), "value"));
                    instrumentation.recordDuration( // GH-90000
                            "test.duration",
                            456L,
                            Attributes.of(AttributeKey.stringKey("operation [GH-90000]"), "test"));
                });
    }

    @Test
    void testErrorHandling() { // GH-90000
        telemetryProvider =
                UnifiedTelemetryProvider.createDefault("test-service", "http://localhost:4317"); // GH-90000
        TelemetryInstrumentation instrumentation =
                new TelemetryInstrumentation(telemetryProvider, "test"); // GH-90000

        // Test command instrumentation with exception
        RuntimeException testException = new RuntimeException("Test error [GH-90000]");

        RuntimeException thrown =
                assertThrows( // GH-90000
                        RuntimeException.class,
                        () -> { // GH-90000
                            instrumentation.instrumentCommand( // GH-90000
                                    "failing-command",
                                    () -> { // GH-90000
                                        throw testException;
                                    });
                        });

        assertEquals("Test error", thrown.getMessage()); // GH-90000
    }
}
