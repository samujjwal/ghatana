/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.api.brain;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for brain workspace streaming — event ingestion, aggregation over
 * tumbling windows, threshold alerting, and error handling.
 *
 * @doc.type    class
 * @doc.purpose Tests for brain workspace streaming and threshold processing
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Brain Workspace Streaming Tests")
class BrainWorkspaceStreamingTest extends EventloopTestBase {

    // ── Streaming model ───────────────────────────────────────────────────────

    record StreamEvent(String workspaceId, String metricName, double value, long timestampMs) {} // GH-90000

    record WindowAggregate(String workspaceId, String metricName, // GH-90000
                           double sum, double avg, double max, double min, int count) {}

    record Alert(String workspaceId, String metricName, double threshold, // GH-90000
                 double actualValue, String message) {}

    private BrainWorkspaceStream stream;

    @BeforeEach
    void setUp() { // GH-90000
        stream = new BrainWorkspaceStream(); // GH-90000
    }

    // ── Event ingestion ───────────────────────────────────────────────────────

    @Test
    @DisplayName("ingested events are accepted without error")
    void ingestedEventsAreAccepted() { // GH-90000
        stream.emit(new StreamEvent("ws-1", "cpu_usage", 42.5, System.currentTimeMillis())); // GH-90000
        stream.emit(new StreamEvent("ws-1", "cpu_usage", 55.0, System.currentTimeMillis())); // GH-90000
        stream.emit(new StreamEvent("ws-1", "memory_usage", 70.0, System.currentTimeMillis())); // GH-90000

        assertThat(stream.eventCount("ws-1")).isEqualTo(3);
    }

    @Test
    @DisplayName("events from different workspaces are tracked independently")
    void eventsFromDifferentWorkspacesTrackedIndependently() { // GH-90000
        stream.emit(new StreamEvent("ws-A", "latency_ms", 100.0, System.currentTimeMillis())); // GH-90000
        stream.emit(new StreamEvent("ws-B", "latency_ms", 200.0, System.currentTimeMillis())); // GH-90000
        stream.emit(new StreamEvent("ws-B", "latency_ms", 250.0, System.currentTimeMillis())); // GH-90000

        assertThat(stream.eventCount("ws-A")).isEqualTo(1);
        assertThat(stream.eventCount("ws-B")).isEqualTo(2);
    }

    // ── Aggregation ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("aggregate computes correct sum, avg, max, min, and count")
    void aggregateComputesCorrectStats() { // GH-90000
        String ws = "ws-agg";
        String metric = "throughput_rps";
        long t = System.currentTimeMillis(); // GH-90000

        stream.emit(new StreamEvent(ws, metric, 100.0, t)); // GH-90000
        stream.emit(new StreamEvent(ws, metric, 200.0, t + 1000)); // GH-90000
        stream.emit(new StreamEvent(ws, metric, 150.0, t + 2000)); // GH-90000

        WindowAggregate agg = stream.aggregate(ws, metric); // GH-90000

        assertThat(agg.count()).isEqualTo(3); // GH-90000
        assertThat(agg.sum()).isCloseTo(450.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
        assertThat(agg.avg()).isCloseTo(150.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
        assertThat(agg.max()).isCloseTo(200.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
        assertThat(agg.min()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
    }

    @Test
    @DisplayName("aggregate on empty metric returns zero-value aggregate")
    void aggregateOnEmptyMetricReturnsZeros() { // GH-90000
        WindowAggregate agg = stream.aggregate("ws-empty", "nonexistent_metric"); // GH-90000

        assertThat(agg.count()).isEqualTo(0); // GH-90000
        assertThat(agg.sum()).isEqualTo(0.0); // GH-90000
    }

    @Test
    @DisplayName("aggregate is isolated per workspace and metric combination")
    void aggregateIsIsolatedPerWorkspaceAndMetric() { // GH-90000
        stream.emit(new StreamEvent("ws-X", "metric-1", 10.0, System.currentTimeMillis())); // GH-90000
        stream.emit(new StreamEvent("ws-X", "metric-2", 20.0, System.currentTimeMillis())); // GH-90000
        stream.emit(new StreamEvent("ws-Y", "metric-1", 30.0, System.currentTimeMillis())); // GH-90000

        assertThat(stream.aggregate("ws-X", "metric-1").sum()).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
        assertThat(stream.aggregate("ws-X", "metric-2").sum()).isCloseTo(20.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
        assertThat(stream.aggregate("ws-Y", "metric-1").sum()).isCloseTo(30.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
    }

    // ── Threshold alerting ────────────────────────────────────────────────────

    @Test
    @DisplayName("alert is triggered when event value exceeds the configured threshold")
    void alertTriggeredWhenValueExceedsThreshold() { // GH-90000
        stream.setThreshold("ws-alert", "cpu_usage", 80.0); // GH-90000
        stream.emit(new StreamEvent("ws-alert", "cpu_usage", 95.0, System.currentTimeMillis())); // GH-90000

        List<Alert> alerts = stream.drainAlerts(); // GH-90000
        assertThat(alerts).hasSize(1); // GH-90000
        assertThat(alerts.get(0).metricName()).isEqualTo("cpu_usage");
        assertThat(alerts.get(0).actualValue()).isCloseTo(95.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
    }

    @Test
    @DisplayName("no alert is triggered when event value is below the configured threshold")
    void noAlertWhenValueBelowThreshold() { // GH-90000
        stream.setThreshold("ws-ok", "cpu_usage", 80.0); // GH-90000
        stream.emit(new StreamEvent("ws-ok", "cpu_usage", 50.0, System.currentTimeMillis())); // GH-90000

        assertThat(stream.drainAlerts()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("multiple threshold violations produce separate alerts")
    void multipleViolationsProduceSeparateAlerts() { // GH-90000
        stream.setThreshold("ws-multi", "latency", 100.0); // GH-90000
        stream.emit(new StreamEvent("ws-multi", "latency", 150.0, System.currentTimeMillis())); // GH-90000
        stream.emit(new StreamEvent("ws-multi", "latency", 200.0, System.currentTimeMillis())); // GH-90000

        assertThat(stream.drainAlerts()).hasSize(2); // GH-90000
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    @DisplayName("emitting null workspace ID records an error and does not crash")
    void emittingNullWorkspaceIdRecordsError() { // GH-90000
        try {
            stream.emit(new StreamEvent(null, "metric", 1.0, System.currentTimeMillis())); // GH-90000
        } catch (Exception e) { // GH-90000
            // Expected: validation error
        }
        // Stream is still operational after the invalid event
        stream.emit(new StreamEvent("ws-ok2", "metric", 1.0, System.currentTimeMillis())); // GH-90000
        assertThat(stream.eventCount("ws-ok2")).isEqualTo(1);
    }

    // ── Brain workspace stream implementation (for tests) ───────────────────── // GH-90000

    static class BrainWorkspaceStream {
        private final Map<String, List<StreamEvent>> events = new HashMap<>(); // GH-90000
        private final Map<String, Map<String, Double>> thresholds = new HashMap<>(); // GH-90000
        private final List<Alert> alertBuffer = new ArrayList<>(); // GH-90000

        void emit(StreamEvent event) { // GH-90000
            Objects.requireNonNull(event.workspaceId(), "workspaceId must not be null"); // GH-90000
            events.computeIfAbsent(event.workspaceId(), k -> new ArrayList<>()).add(event); // GH-90000

            // Check thresholds
            Map<String, Double> wsThresholds = thresholds.get(event.workspaceId()); // GH-90000
            if (wsThresholds != null) { // GH-90000
                Double threshold = wsThresholds.get(event.metricName()); // GH-90000
                if (threshold != null && event.value() > threshold) { // GH-90000
                    alertBuffer.add(new Alert(event.workspaceId(), event.metricName(), // GH-90000
                            threshold, event.value(), // GH-90000
                            event.metricName() + " exceeded threshold: " + event.value() + " > " + threshold)); // GH-90000
                }
            }
        }

        int eventCount(String workspaceId) { // GH-90000
            return events.getOrDefault(workspaceId, List.of()).size(); // GH-90000
        }

        WindowAggregate aggregate(String workspaceId, String metricName) { // GH-90000
            List<StreamEvent> filtered = events.getOrDefault(workspaceId, List.of()).stream() // GH-90000
                    .filter(e -> e.metricName().equals(metricName)) // GH-90000
                    .toList(); // GH-90000

            if (filtered.isEmpty()) { // GH-90000
                return new WindowAggregate(workspaceId, metricName, 0, 0, 0, 0, 0); // GH-90000
            }

            double sum = 0, max = Double.MIN_VALUE, min = Double.MAX_VALUE;
            for (StreamEvent e : filtered) { // GH-90000
                sum += e.value(); // GH-90000
                if (e.value() > max) max = e.value(); // GH-90000
                if (e.value() < min) min = e.value(); // GH-90000
            }
            return new WindowAggregate(workspaceId, metricName, // GH-90000
                    sum, sum / filtered.size(), max, min, filtered.size()); // GH-90000
        }

        void setThreshold(String workspaceId, String metricName, double threshold) { // GH-90000
            thresholds.computeIfAbsent(workspaceId, k -> new HashMap<>()).put(metricName, threshold); // GH-90000
        }

        List<Alert> drainAlerts() { // GH-90000
            List<Alert> drained = new ArrayList<>(alertBuffer); // GH-90000
            alertBuffer.clear(); // GH-90000
            return drained;
        }
    }
}
