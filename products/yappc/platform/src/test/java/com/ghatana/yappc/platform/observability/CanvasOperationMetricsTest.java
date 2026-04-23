package com.ghatana.yappc.platform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link CanvasOperationMetrics}.
 *
 * <p>Verifies that the correct counters, timers, and error details are emitted
 * for each canvas lifecycle event and that constructor guards reject nulls.
 */
@DisplayName("CanvasOperationMetrics")
@ExtendWith(MockitoExtension.class) // GH-90000
class CanvasOperationMetricsTest {

    @Mock
    private MetricsCollector metricsCollector;

    private MeterRegistry meterRegistry;
    private CanvasOperationMetrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        meterRegistry = new SimpleMeterRegistry(); // GH-90000
        metrics = new CanvasOperationMetrics(metricsCollector, meterRegistry); // GH-90000
    }

    // ─── Constructor guards ────────────────────────────────────────────────

    @Test
    @DisplayName("constructor rejects null MetricsCollector")
    void constructor_rejectsNullMetricsCollector() { // GH-90000
        assertThatThrownBy(() -> new CanvasOperationMetrics(null, meterRegistry)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("constructor rejects null MeterRegistry")
    void constructor_rejectsNullMeterRegistry() { // GH-90000
        assertThatThrownBy(() -> new CanvasOperationMetrics(metricsCollector, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─── startOperation ────────────────────────────────────────────────────

    @Test
    @DisplayName("startOperation increments started counter and returns a Timer.Sample")
    void startOperation_incrementsCounterAndReturnsSample() { // GH-90000
        Timer.Sample sample = metrics.startOperation("element.create", "tenant-1", "architecture"); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                CanvasOperationMetrics.METRIC_OPERATION_STARTED,
                "operation",   "element.create",
                "tenant",      "tenant-1",
                "canvas_type", "architecture");
        assertThat(sample).isNotNull(); // GH-90000
    }

    // ─── recordOperationSuccess ─────────────────────────────────────────────

    @Test
    @DisplayName("recordOperationSuccess increments succeeded counter and stops timer")
    void recordOperationSuccess_incrementsCounterAndStopsTimer() { // GH-90000
        Timer.Sample sample = metrics.startOperation("layout.update", "tenant-a", "wireframe"); // GH-90000

        metrics.recordOperationSuccess(sample, "layout.update", "tenant-a", "wireframe"); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                CanvasOperationMetrics.METRIC_OPERATION_SUCCEEDED,
                "operation",   "layout.update",
                "tenant",      "tenant-a",
                "canvas_type", "wireframe");

        // Timer should have recorded a duration measurement
        assertThat(meterRegistry.find(CanvasOperationMetrics.METRIC_OPERATION_DURATION).timer()) // GH-90000
                .isNotNull() // GH-90000
                .satisfies(t -> assertThat(t.count()).isEqualTo(1)); // GH-90000
    }

    @Test
    @DisplayName("recordOperationSuccess with null sample still increments counter")
    void recordOperationSuccess_nullSample_stillIncrementsCounter() { // GH-90000
        metrics.recordOperationSuccess(null, "connection.delete", "tenant-b", "kanban"); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                CanvasOperationMetrics.METRIC_OPERATION_SUCCEEDED,
                "operation",   "connection.delete",
                "tenant",      "tenant-b",
                "canvas_type", "kanban");
    }

    // ─── recordOperationFailure ─────────────────────────────────────────────

    @Test
    @DisplayName("recordOperationFailure with cause tags error_type and stops timer")
    void recordOperationFailure_withCause_tagsErrorType() { // GH-90000
        Timer.Sample sample = metrics.startOperation("element.resize", "tenant-x", "architecture"); // GH-90000
        RuntimeException cause = new RuntimeException("constraint violation");

        metrics.recordOperationFailure(sample, "element.resize", "tenant-x", "architecture", cause); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                CanvasOperationMetrics.METRIC_OPERATION_FAILED,
                "operation",   "element.resize",
                "tenant",      "tenant-x",
                "canvas_type", "architecture",
                "error_type",  "RuntimeException");

        assertThat(meterRegistry.find(CanvasOperationMetrics.METRIC_OPERATION_DURATION).timer()) // GH-90000
                .isNotNull() // GH-90000
                .satisfies(t -> assertThat(t.count()).isEqualTo(1)); // GH-90000
    }

    @Test
    @DisplayName("recordOperationFailure with null cause uses 'unknown' error type")
    void recordOperationFailure_nullCause_usesUnknownErrorType() { // GH-90000
        metrics.recordOperationFailure(null, "element.move", "tenant-y", "wireframe", null); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                CanvasOperationMetrics.METRIC_OPERATION_FAILED,
                "operation",   "element.move",
                "tenant",      "tenant-y",
                "canvas_type", "wireframe",
                "error_type",  "unknown");
    }

    // ─── collaboration metrics ──────────────────────────────────────────────

    @Test
    @DisplayName("recordCollaborationConflict increments conflict counter")
    void recordCollaborationConflict_incrementsCounter() { // GH-90000
        metrics.recordCollaborationConflict("tenant-z", "kanban"); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                CanvasOperationMetrics.METRIC_COLLAB_CONFLICT,
                "tenant",      "tenant-z",
                "canvas_type", "kanban");
    }

    @Test
    @DisplayName("recordCollaborationResolved increments resolved counter")
    void recordCollaborationResolved_incrementsCounter() { // GH-90000
        metrics.recordCollaborationResolved("tenant-z", "kanban"); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                CanvasOperationMetrics.METRIC_COLLAB_RESOLVED,
                "tenant",      "tenant-z",
                "canvas_type", "kanban");
    }

    // ─── Round-trip timer verification ─────────────────────────────────────

    @Test
    @DisplayName("full operation start-to-success cycle uses real SimpleMeterRegistry timer")
    void fullCycle_realTimerRecordsSuccessfully() { // GH-90000
        Timer.Sample sample = metrics.startOperation("component.add", "tenant-rt", "architecture"); // GH-90000
        metrics.recordOperationSuccess(sample, "component.add", "tenant-rt", "architecture"); // GH-90000

        double totalTime = meterRegistry
                .find(CanvasOperationMetrics.METRIC_OPERATION_DURATION) // GH-90000
                .timer() // GH-90000
                .totalTime(java.util.concurrent.TimeUnit.NANOSECONDS); // GH-90000

        assertThat(totalTime).isGreaterThanOrEqualTo(0.0); // GH-90000
    }
}
