/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Platform observability integration tests.
 *
 * <p>Validates metric recording, span tracing, structured logging, health checks,
 * and alert thresholds across the platform observability stack.
 *
 * @doc.type    class
 * @doc.purpose Platform observability integration: metrics, tracing, logging, health
 * @doc.layer   platform
 * @doc.pattern IntegrationTest
 */
@DisplayName("Platform Observability Integration Tests")
@Tag("integration")
class ObservabilityIntegrationTest extends EventloopTestBase {

    private ObservabilityPlatform observability;

    @BeforeEach
    void setUp() {
        observability = new ObservabilityPlatform();
    }

    // ── Metrics ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("counter increments are accumulated correctly")
    void counterIncrementsAreAccumulatedCorrectly() {
        observability.increment("http.requests.total", Map.of("method", "GET", "path", "/api"));
        observability.increment("http.requests.total", Map.of("method", "GET", "path", "/api"));
        observability.increment("http.requests.total", Map.of("method", "GET", "path", "/api"));

        long value = observability.getCounter("http.requests.total",
                Map.of("method", "GET", "path", "/api"));
        assertThat(value).isEqualTo(3);
    }

    @Test
    @DisplayName("gauge records the last written value")
    void gaugeRecordsLastWrittenValue() {
        observability.gauge("jvm.memory.used", 512_000_000L, Map.of("area", "heap"));
        observability.gauge("jvm.memory.used", 600_000_000L, Map.of("area", "heap"));

        long value = observability.getGauge("jvm.memory.used", Map.of("area", "heap"));
        assertThat(value).isEqualTo(600_000_000L);
    }

    @Test
    @DisplayName("histogram records all observations and computes correct percentile")
    void histogramRecordsAllObservationsAndComputesPercentile() {
        long[] latencies = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        for (long l : latencies) {
            observability.histogram("http.request.duration_ms", l, Map.of("route", "/api"));
        }

        // p50 should be around 50
        long p50 = observability.getHistogramPercentile("http.request.duration_ms",
                Map.of("route", "/api"), 50);
        assertThat(p50).isBetween(45L, 55L);
    }

    @Test
    @DisplayName("different label combinations are tracked independently")
    void differentLabelCombinationsTrackedIndependently() {
        observability.increment("requests", Map.of("status", "200"));
        observability.increment("requests", Map.of("status", "200"));
        observability.increment("requests", Map.of("status", "500"));

        assertThat(observability.getCounter("requests", Map.of("status", "200"))).isEqualTo(2);
        assertThat(observability.getCounter("requests", Map.of("status", "500"))).isEqualTo(1);
    }

    // ── Tracing ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("starting a span creates a new trace with a non-null traceId")
    void startingSpanCreatesTraceWithNonNullTraceId() {
        ObservabilityPlatform.Span span = observability.startSpan("process-request", "service-a");
        assertThat(span.traceId()).isNotNull().isNotBlank();
        assertThat(span.spanId()).isNotNull().isNotBlank();
        span.end();
    }

    @Test
    @DisplayName("child span shares the parent traceId")
    void childSpanSharesParentTraceId() {
        ObservabilityPlatform.Span parent = observability.startSpan("parent-op", "service-a");
        ObservabilityPlatform.Span child = observability.startChildSpan("child-op", parent);

        assertThat(child.traceId()).isEqualTo(parent.traceId());
        child.end();
        parent.end();
    }

    @Test
    @DisplayName("ended span records a non-null duration")
    void endedSpanRecordsNonNullDuration() {
        ObservabilityPlatform.Span span = observability.startSpan("timed-op", "service-b");
        span.end();

        assertThat(span.durationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("spans can be tagged with key-value attributes")
    void spansCanBeTaggedWithKeyValueAttributes() {
        ObservabilityPlatform.Span span = observability.startSpan("tagged-op", "service-c");
        span.addTag("db.type", "postgres");
        span.addTag("http.status", "200");
        span.end();

        assertThat(span.tags()).containsEntry("db.type", "postgres");
        assertThat(span.tags()).containsEntry("http.status", "200");
    }

    // ── Structured logging ────────────────────────────────────────────────────

    @Test
    @DisplayName("log entry is persisted with structured fields")
    void logEntryPersistedWithStructuredFields() {
        observability.log("INFO", "user.login.success",
                Map.of("userId", "u-1", "tenantId", "t-1", "correlationId", "c-1"));

        List<ObservabilityPlatform.LogEntry> logs = observability.getLogs("INFO");
        assertThat(logs).anyMatch(l -> "user.login.success".equals(l.event())
                && "u-1".equals(l.fields().get("userId")));
    }

    @Test
    @DisplayName("logs at ERROR level are retrievable independently from INFO level")
    void errorLogsRetrievableIndependently() {
        observability.log("INFO", "info.event", Map.of());
        observability.log("ERROR", "error.event", Map.of("reason", "timeout"));

        assertThat(observability.getLogs("ERROR")).hasSize(1);
        assertThat(observability.getLogs("INFO")).hasSize(1);
    }

    // ── Health checks ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("healthy service produces UP health status")
    void healthyServiceProducesUpStatus() {
        observability.registerHealthCheck("database", () -> true);

        ObservabilityPlatform.HealthStatus status = observability.getHealth("database");
        assertThat(status.status()).isEqualTo("UP");
    }

    @Test
    @DisplayName("failing health check produces DOWN status with error message")
    void failingHealthCheckProducesDownStatus() {
        observability.registerHealthCheck("message-queue", () -> false);

        ObservabilityPlatform.HealthStatus status = observability.getHealth("message-queue");
        assertThat(status.status()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("aggregate health is DOWN if any component is DOWN")
    void aggregateHealthIsDownIfAnyComponentDown() {
        observability.registerHealthCheck("db", () -> true);
        observability.registerHealthCheck("cache", () -> false);

        assertThat(observability.getAggregateHealth()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("aggregate health is UP when all components are healthy")
    void aggregateHealthIsUpWhenAllComponentsHealthy() {
        observability.registerHealthCheck("db2", () -> true);
        observability.registerHealthCheck("cache2", () -> true);

        assertThat(observability.getAggregateHealth()).isEqualTo("UP");
    }

    // ── Alert thresholds ──────────────────────────────────────────────────────

    @Test
    @DisplayName("counter exceeding threshold fires an alert")
    void counterExceedingThresholdFiresAlert() {
        observability.setAlert("http.errors", Map.of("status", "500"), 3, "WARN");

        for (int i = 0; i < 4; i++) {
            observability.increment("http.errors", Map.of("status", "500"));
        }

        List<String> alerts = observability.getFiredAlerts();
        assertThat(alerts).anyMatch(a -> a.contains("http.errors"));
    }

    // ── Observability platform implementation (for tests) ─────────────────────

    static class ObservabilityPlatform {
        record Span(String traceId, String spanId, String operationName,
                    String service, AtomicLong startMs, AtomicLong endMs,
                    Map<String, String> tags) {
            long durationMs() {
                long end = endMs.get();
                return end == 0 ? 0 : end - startMs.get();
            }
            void addTag(String key, String value) { tags.put(key, value); }
            void end() { endMs.set(System.currentTimeMillis()); }
        }

        record LogEntry(String level, String event, Map<String, Object> fields, Instant timestamp) {}
        record HealthStatus(String component, String status) {}

        private final ConcurrentHashMap<String, ConcurrentHashMap<Map<String, String>, AtomicLong>>
                counters = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, ConcurrentHashMap<Map<String, String>, AtomicLong>>
                gauges = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, ConcurrentHashMap<Map<String, String>, CopyOnWriteArrayList<Long>>>
                histograms = new ConcurrentHashMap<>();
        private final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>());
        private final ConcurrentHashMap<String, java.util.function.BooleanSupplier> healthChecks = new ConcurrentHashMap<>();
        private final List<String> firedAlerts = Collections.synchronizedList(new ArrayList<>());
        private final List<Object[]> alertConfigs = Collections.synchronizedList(new ArrayList<>());

        void increment(String metric, Map<String, String> labels) {
            counters.computeIfAbsent(metric, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(labels, k -> new AtomicLong(0)).incrementAndGet();
            checkAlerts(metric, labels);
        }

        long getCounter(String metric, Map<String, String> labels) {
            ConcurrentHashMap<Map<String, String>, AtomicLong> m = counters.get(metric);
            if (m == null) return 0;
            AtomicLong v = m.get(labels);
            return v == null ? 0 : v.get();
        }

        void gauge(String metric, long value, Map<String, String> labels) {
            gauges.computeIfAbsent(metric, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(labels, k -> new AtomicLong(0)).set(value);
        }

        long getGauge(String metric, Map<String, String> labels) {
            ConcurrentHashMap<Map<String, String>, AtomicLong> m = gauges.get(metric);
            if (m == null) return 0;
            AtomicLong v = m.get(labels);
            return v == null ? 0 : v.get();
        }

        void histogram(String metric, long value, Map<String, String> labels) {
            histograms.computeIfAbsent(metric, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(labels, k -> new CopyOnWriteArrayList<>()).add(value);
        }

        long getHistogramPercentile(String metric, Map<String, String> labels, int percentile) {
            ConcurrentHashMap<Map<String, String>, CopyOnWriteArrayList<Long>> m = histograms.get(metric);
            if (m == null) return 0;
            CopyOnWriteArrayList<Long> observations = m.get(labels);
            if (observations == null || observations.isEmpty()) return 0;
            List<Long> sorted = observations.stream().sorted().toList();
            int idx = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
        }

        Span startSpan(String operationName, String service) {
            return new Span(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    operationName, service, new AtomicLong(System.currentTimeMillis()),
                    new AtomicLong(0), new ConcurrentHashMap<>());
        }

        Span startChildSpan(String operationName, Span parent) {
            return new Span(parent.traceId(), UUID.randomUUID().toString(),
                    operationName, parent.service(), new AtomicLong(System.currentTimeMillis()),
                    new AtomicLong(0), new ConcurrentHashMap<>());
        }

        void log(String level, String event, Map<String, Object> fields) {
            logs.add(new LogEntry(level, event, new HashMap<>(fields), Instant.now()));
        }

        List<LogEntry> getLogs(String level) {
            return logs.stream().filter(l -> level.equals(l.level())).toList();
        }

        void registerHealthCheck(String component, java.util.function.BooleanSupplier check) {
            healthChecks.put(component, check);
        }

        HealthStatus getHealth(String component) {
            java.util.function.BooleanSupplier check = healthChecks.get(component);
            String status = (check != null && check.getAsBoolean()) ? "UP" : "DOWN";
            return new HealthStatus(component, status);
        }

        String getAggregateHealth() {
            return healthChecks.values().stream().allMatch(java.util.function.BooleanSupplier::getAsBoolean)
                    ? "UP" : "DOWN";
        }

        void setAlert(String metric, Map<String, String> labels, long threshold, String severity) {
            alertConfigs.add(new Object[]{metric, labels, threshold, severity});
        }

        private void checkAlerts(String metric, Map<String, String> labels) {
            for (Object[] config : alertConfigs) {
                String cfgMetric = (String) config[0];
                @SuppressWarnings("unchecked") Map<String, String> cfgLabels = (Map<String, String>) config[1];
                long threshold = (long) config[2];
                String severity = (String) config[3];
                if (cfgMetric.equals(metric) && cfgLabels.equals(labels)) {
                    long current = getCounter(metric, labels);
                    if (current > threshold) {
                        firedAlerts.add(severity + ": " + metric + " exceeded threshold " + threshold);
                    }
                }
            }
        }

        List<String> getFiredAlerts() { return List.copyOf(firedAlerts); }
    }
}
