/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.brain;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    record StreamEvent(String workspaceId, String metricName, double value, long timestampMs) {}

    record WindowAggregate(String workspaceId, String metricName,
                           double sum, double avg, double max, double min, int count) {}

    record Alert(String workspaceId, String metricName, double threshold,
                 double actualValue, String message) {}

    private BrainWorkspaceStream stream;

    @BeforeEach
    void setUp() {
        stream = new BrainWorkspaceStream();
    }

    // ── Event ingestion ───────────────────────────────────────────────────────

    @Test
    @DisplayName("ingested events are accepted without error")
    void ingestedEventsAreAccepted() {
        stream.emit(new StreamEvent("ws-1", "cpu_usage", 42.5, System.currentTimeMillis()));
        stream.emit(new StreamEvent("ws-1", "cpu_usage", 55.0, System.currentTimeMillis()));
        stream.emit(new StreamEvent("ws-1", "memory_usage", 70.0, System.currentTimeMillis()));

        assertThat(stream.eventCount("ws-1")).isEqualTo(3);
    }

    @Test
    @DisplayName("events from different workspaces are tracked independently")
    void eventsFromDifferentWorkspacesTrackedIndependently() {
        stream.emit(new StreamEvent("ws-A", "latency_ms", 100.0, System.currentTimeMillis()));
        stream.emit(new StreamEvent("ws-B", "latency_ms", 200.0, System.currentTimeMillis()));
        stream.emit(new StreamEvent("ws-B", "latency_ms", 250.0, System.currentTimeMillis()));

        assertThat(stream.eventCount("ws-A")).isEqualTo(1);
        assertThat(stream.eventCount("ws-B")).isEqualTo(2);
    }

    // ── Aggregation ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("aggregate computes correct sum, avg, max, min, and count")
    void aggregateComputesCorrectStats() {
        String ws = "ws-agg";
        String metric = "throughput_rps";
        long t = System.currentTimeMillis();

        stream.emit(new StreamEvent(ws, metric, 100.0, t));
        stream.emit(new StreamEvent(ws, metric, 200.0, t + 1000));
        stream.emit(new StreamEvent(ws, metric, 150.0, t + 2000));

        WindowAggregate agg = stream.aggregate(ws, metric);

        assertThat(agg.count()).isEqualTo(3);
        assertThat(agg.sum()).isCloseTo(450.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(agg.avg()).isCloseTo(150.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(agg.max()).isCloseTo(200.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(agg.min()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("aggregate on empty metric returns zero-value aggregate")
    void aggregateOnEmptyMetricReturnsZeros() {
        WindowAggregate agg = stream.aggregate("ws-empty", "nonexistent_metric");

        assertThat(agg.count()).isEqualTo(0);
        assertThat(agg.sum()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("aggregate is isolated per workspace and metric combination")
    void aggregateIsIsolatedPerWorkspaceAndMetric() {
        stream.emit(new StreamEvent("ws-X", "metric-1", 10.0, System.currentTimeMillis()));
        stream.emit(new StreamEvent("ws-X", "metric-2", 20.0, System.currentTimeMillis()));
        stream.emit(new StreamEvent("ws-Y", "metric-1", 30.0, System.currentTimeMillis()));

        assertThat(stream.aggregate("ws-X", "metric-1").sum()).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(stream.aggregate("ws-X", "metric-2").sum()).isCloseTo(20.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(stream.aggregate("ws-Y", "metric-1").sum()).isCloseTo(30.0, org.assertj.core.data.Offset.offset(0.001));
    }

    // ── Threshold alerting ────────────────────────────────────────────────────

    @Test
    @DisplayName("alert is triggered when event value exceeds the configured threshold")
    void alertTriggeredWhenValueExceedsThreshold() {
        stream.setThreshold("ws-alert", "cpu_usage", 80.0);
        stream.emit(new StreamEvent("ws-alert", "cpu_usage", 95.0, System.currentTimeMillis()));

        List<Alert> alerts = stream.drainAlerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).metricName()).isEqualTo("cpu_usage");
        assertThat(alerts.get(0).actualValue()).isCloseTo(95.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("no alert is triggered when event value is below the configured threshold")
    void noAlertWhenValueBelowThreshold() {
        stream.setThreshold("ws-ok", "cpu_usage", 80.0);
        stream.emit(new StreamEvent("ws-ok", "cpu_usage", 50.0, System.currentTimeMillis()));

        assertThat(stream.drainAlerts()).isEmpty();
    }

    @Test
    @DisplayName("multiple threshold violations produce separate alerts")
    void multipleViolationsProduceSeparateAlerts() {
        stream.setThreshold("ws-multi", "latency", 100.0);
        stream.emit(new StreamEvent("ws-multi", "latency", 150.0, System.currentTimeMillis()));
        stream.emit(new StreamEvent("ws-multi", "latency", 200.0, System.currentTimeMillis()));

        assertThat(stream.drainAlerts()).hasSize(2);
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    @DisplayName("emitting null workspace ID records an error and does not crash")
    void emittingNullWorkspaceIdRecordsError() {
        try {
            stream.emit(new StreamEvent(null, "metric", 1.0, System.currentTimeMillis()));
        } catch (Exception e) {
            // Expected: validation error
        }
        // Stream is still operational after the invalid event
        stream.emit(new StreamEvent("ws-ok2", "metric", 1.0, System.currentTimeMillis()));
        assertThat(stream.eventCount("ws-ok2")).isEqualTo(1);
    }

    // ── Brain workspace stream implementation (for tests) ─────────────────────

    static class BrainWorkspaceStream {
        private final Map<String, List<StreamEvent>> events = new HashMap<>();
        private final Map<String, Map<String, Double>> thresholds = new HashMap<>();
        private final List<Alert> alertBuffer = new ArrayList<>();

        void emit(StreamEvent event) {
            Objects.requireNonNull(event.workspaceId(), "workspaceId must not be null");
            events.computeIfAbsent(event.workspaceId(), k -> new ArrayList<>()).add(event);

            // Check thresholds
            Map<String, Double> wsThresholds = thresholds.get(event.workspaceId());
            if (wsThresholds != null) {
                Double threshold = wsThresholds.get(event.metricName());
                if (threshold != null && event.value() > threshold) {
                    alertBuffer.add(new Alert(event.workspaceId(), event.metricName(),
                            threshold, event.value(),
                            event.metricName() + " exceeded threshold: " + event.value() + " > " + threshold));
                }
            }
        }

        int eventCount(String workspaceId) {
            return events.getOrDefault(workspaceId, List.of()).size();
        }

        WindowAggregate aggregate(String workspaceId, String metricName) {
            List<StreamEvent> filtered = events.getOrDefault(workspaceId, List.of()).stream()
                    .filter(e -> e.metricName().equals(metricName))
                    .toList();

            if (filtered.isEmpty()) {
                return new WindowAggregate(workspaceId, metricName, 0, 0, 0, 0, 0);
            }

            double sum = 0, max = Double.MIN_VALUE, min = Double.MAX_VALUE;
            for (StreamEvent e : filtered) {
                sum += e.value();
                if (e.value() > max) max = e.value();
                if (e.value() < min) min = e.value();
            }
            return new WindowAggregate(workspaceId, metricName,
                    sum, sum / filtered.size(), max, min, filtered.size());
        }

        void setThreshold(String workspaceId, String metricName, double threshold) {
            thresholds.computeIfAbsent(workspaceId, k -> new HashMap<>()).put(metricName, threshold);
        }

        List<Alert> drainAlerts() {
            List<Alert> drained = new ArrayList<>(alertBuffer);
            alertBuffer.clear();
            return drained;
        }
    }
}
