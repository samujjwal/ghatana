package com.ghatana.kernel.telemetry;

import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KernelLifecycleTelemetry.
 *
 * @doc.type class
 * @doc.purpose Test kernel lifecycle telemetry functionality
 * @doc.layer kernel
 */
@DisplayName("KernelLifecycleTelemetry Tests")
class KernelLifecycleTelemetryTest {

    @Test
    @DisplayName("Records lifecycle phase start and completion")
    void recordsLifecyclePhaseStartAndCompletion() {
        Eventloop eventloop = Eventloop.create();
        KernelLifecycleTelemetry telemetry = new KernelLifecycleTelemetry(eventloop, "test-component");

        KernelLifecycleTelemetry.SpanContext context = telemetry.recordLifecycleStart("product-1", "build", "web");
        assertNotNull(context);
        assertEquals("lifecycle", context.spanType());
        assertEquals("product-1", context.metadata().get("productId"));
        assertEquals("build", context.metadata().get("phase"));

        telemetry.recordLifecycleComplete(context.spanId(), "success", Map.of("buildTime", "5000ms"));

        KernelLifecycleTelemetry.LifecycleMetrics metrics = telemetry.getMetrics("lifecycle.build");
        assertEquals(1, metrics.totalCount());
        assertEquals(1, metrics.successCount());
        assertEquals(0, metrics.failureCount());
        assertTrue(metrics.averageDurationMs() >= 0);
    }

    @Test
    @DisplayName("Records adapter execution start and completion")
    void recordsAdapterExecutionStartAndCompletion() {
        Eventloop eventloop = Eventloop.create();
        KernelLifecycleTelemetry telemetry = new KernelLifecycleTelemetry(eventloop, "test-component");

        KernelLifecycleTelemetry.SpanContext context = telemetry.recordAdapterStart("gradle-java", "build");
        assertNotNull(context);
        assertEquals("adapter", context.spanType());
        assertEquals("gradle-java", context.metadata().get("adapterId"));

        telemetry.recordAdapterComplete(context.spanId(), "success", Map.of("outputFiles", "5"));

        KernelLifecycleTelemetry.LifecycleMetrics metrics = telemetry.getMetrics("adapter.gradle-java");
        assertEquals(1, metrics.totalCount());
        assertEquals(1, metrics.successCount());
    }

    @Test
    @DisplayName("Records broker interaction start and completion")
    void recordsBrokerInteractionStartAndCompletion() {
        Eventloop eventloop = Eventloop.create();
        KernelLifecycleTelemetry telemetry = new KernelLifecycleTelemetry(eventloop, "test-component");

        KernelLifecycleTelemetry.SpanContext context = telemetry.recordBrokerStart("product", "kernel.consent-status.v1");
        assertNotNull(context);
        assertEquals("broker", context.spanType());
        assertEquals("product", context.metadata().get("brokerType"));

        telemetry.recordBrokerComplete(context.spanId(), "success", Map.of("responseTime", "50ms"));

        KernelLifecycleTelemetry.LifecycleMetrics metrics = telemetry.getMetrics("broker.product");
        assertEquals(1, metrics.totalCount());
        assertEquals(1, metrics.successCount());
    }

    @Test
    @DisplayName("Tracks success and failure counts correctly")
    void tracksSuccessAndFailureCountsCorrectly() {
        Eventloop eventloop = Eventloop.create();
        KernelLifecycleTelemetry telemetry = new KernelLifecycleTelemetry(eventloop, "test-component");

        KernelLifecycleTelemetry.SpanContext context1 = telemetry.recordLifecycleStart("product-1", "build", "web");
        telemetry.recordLifecycleComplete(context1.spanId(), "success", Map.of());

        KernelLifecycleTelemetry.SpanContext context2 = telemetry.recordLifecycleStart("product-1", "build", "web");
        telemetry.recordLifecycleComplete(context2.spanId(), "failed", Map.of());

        KernelLifecycleTelemetry.SpanContext context3 = telemetry.recordLifecycleStart("product-1", "build", "web");
        telemetry.recordLifecycleComplete(context3.spanId(), "success", Map.of());

        KernelLifecycleTelemetry.LifecycleMetrics metrics = telemetry.getMetrics("lifecycle.build");
        assertEquals(3, metrics.totalCount());
        assertEquals(2, metrics.successCount());
        assertEquals(1, metrics.failureCount());
        assertEquals(2.0 / 3.0, metrics.successRate(), 0.001);
    }

    @Test
    @DisplayName("Calculates duration statistics correctly")
    void calculatesDurationStatisticsCorrectly() throws InterruptedException {
        Eventloop eventloop = Eventloop.create();
        KernelLifecycleTelemetry telemetry = new KernelLifecycleTelemetry(eventloop, "test-component");

        KernelLifecycleTelemetry.SpanContext context1 = telemetry.recordLifecycleStart("product-1", "build", "web");
        Thread.sleep(10);
        telemetry.recordLifecycleComplete(context1.spanId(), "success", Map.of());

        KernelLifecycleTelemetry.SpanContext context2 = telemetry.recordLifecycleStart("product-1", "build", "web");
        Thread.sleep(20);
        telemetry.recordLifecycleComplete(context2.spanId(), "success", Map.of());

        KernelLifecycleTelemetry.LifecycleMetrics metrics = telemetry.getMetrics("lifecycle.build");
        assertTrue(metrics.minDurationMs() >= 10);
        assertTrue(metrics.maxDurationMs() >= 20);
        assertTrue(metrics.averageDurationMs() >= 15);
    }

    @Test
    @DisplayName("Returns empty metrics for unknown keys")
    void returnsEmptyMetricsForUnknownKeys() {
        Eventloop eventloop = Eventloop.create();
        KernelLifecycleTelemetry telemetry = new KernelLifecycleTelemetry(eventloop, "test-component");

        KernelLifecycleTelemetry.LifecycleMetrics metrics = telemetry.getMetrics("unknown.key");
        assertEquals(0, metrics.totalCount());
        assertEquals(0, metrics.successCount());
        assertEquals(0, metrics.failureCount());
    }

    @Test
    @DisplayName("Resets metrics correctly")
    void resetsMetricsCorrectly() {
        Eventloop eventloop = Eventloop.create();
        KernelLifecycleTelemetry telemetry = new KernelLifecycleTelemetry(eventloop, "test-component");

        KernelLifecycleTelemetry.SpanContext context = telemetry.recordLifecycleStart("product-1", "build", "web");
        telemetry.recordLifecycleComplete(context.spanId(), "success", Map.of());

        telemetry.resetMetrics();

        KernelLifecycleTelemetry.LifecycleMetrics metrics = telemetry.getMetrics("lifecycle.build");
        assertEquals(0, metrics.totalCount());
        assertTrue(telemetry.getAllMetrics().isEmpty());
    }

    @Test
    @DisplayName("Null componentId throws exception on construction")
    void nullComponentIdThrowsExceptionOnConstruction() {
        Eventloop eventloop = Eventloop.create();
        assertThrows(NullPointerException.class, () -> {
            new KernelLifecycleTelemetry(eventloop, null);
        });
    }

    @Test
    @DisplayName("Null eventloop throws exception on construction")
    void nullEventloopThrowsExceptionOnConstruction() {
        assertThrows(NullPointerException.class, () -> {
            new KernelLifecycleTelemetry(null, "test-component");
        });
    }
}
