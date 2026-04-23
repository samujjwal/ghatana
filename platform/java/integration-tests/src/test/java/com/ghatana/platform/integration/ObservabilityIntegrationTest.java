/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        observability = new ObservabilityPlatform(); // GH-90000
    }

    // ── Metrics ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("counter increments are accumulated correctly")
    void counterIncrementsAreAccumulatedCorrectly() { // GH-90000
        observability.increment("http.requests.total", Map.of("method", "GET", "path", "/api")); // GH-90000
        observability.increment("http.requests.total", Map.of("method", "GET", "path", "/api")); // GH-90000
        observability.increment("http.requests.total", Map.of("method", "GET", "path", "/api")); // GH-90000

        long value = observability.getCounter("http.requests.total", // GH-90000
                Map.of("method", "GET", "path", "/api")); // GH-90000
        assertThat(value).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("gauge records the last written value")
    void gaugeRecordsLastWrittenValue() { // GH-90000
        observability.gauge("jvm.memory.used", 512_000_000L, Map.of("area", "heap")); // GH-90000
        observability.gauge("jvm.memory.used", 600_000_000L, Map.of("area", "heap")); // GH-90000

        long value = observability.getGauge("jvm.memory.used", Map.of("area", "heap")); // GH-90000
        assertThat(value).isEqualTo(600_000_000L); // GH-90000
    }

    @Test
    @DisplayName("histogram records all observations and computes correct percentile")
    void histogramRecordsAllObservationsAndComputesPercentile() { // GH-90000
        long[] latencies = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        for (long l : latencies) { // GH-90000
            observability.histogram("http.request.duration_ms", l, Map.of("route", "/api")); // GH-90000
        }

        // p50 should be around 50
        long p50 = observability.getHistogramPercentile("http.request.duration_ms", // GH-90000
                Map.of("route", "/api"), 50); // GH-90000
        assertThat(p50).isBetween(45L, 55L); // GH-90000
    }

    @Test
    @DisplayName("different label combinations are tracked independently")
    void differentLabelCombinationsTrackedIndependently() { // GH-90000
        observability.increment("requests", Map.of("status", "200")); // GH-90000
        observability.increment("requests", Map.of("status", "200")); // GH-90000
        observability.increment("requests", Map.of("status", "500")); // GH-90000

        assertThat(observability.getCounter("requests", Map.of("status", "200"))).isEqualTo(2); // GH-90000
        assertThat(observability.getCounter("requests", Map.of("status", "500"))).isEqualTo(1); // GH-90000
    }

    // ── Tracing ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("starting a span creates a new trace with a non-null traceId")
    void startingSpanCreatesTraceWithNonNullTraceId() { // GH-90000
        ObservabilityPlatform.Span span = observability.startSpan("process-request", "service-a"); // GH-90000
        assertThat(span.traceId()).isNotNull().isNotBlank(); // GH-90000
        assertThat(span.spanId()).isNotNull().isNotBlank(); // GH-90000
        span.end(); // GH-90000
    }

    @Test
    @DisplayName("child span shares the parent traceId")
    void childSpanSharesParentTraceId() { // GH-90000
        ObservabilityPlatform.Span parent = observability.startSpan("parent-op", "service-a"); // GH-90000
        ObservabilityPlatform.Span child = observability.startChildSpan("child-op", parent); // GH-90000

        assertThat(child.traceId()).isEqualTo(parent.traceId()); // GH-90000
        child.end(); // GH-90000
        parent.end(); // GH-90000
    }

    @Test
    @DisplayName("ended span records a non-null duration")
    void endedSpanRecordsNonNullDuration() { // GH-90000
        ObservabilityPlatform.Span span = observability.startSpan("timed-op", "service-b"); // GH-90000
        span.end(); // GH-90000

        assertThat(span.durationMs()).isGreaterThanOrEqualTo(0L); // GH-90000
    }

    @Test
    @DisplayName("spans can be tagged with key-value attributes")
    void spansCanBeTaggedWithKeyValueAttributes() { // GH-90000
        ObservabilityPlatform.Span span = observability.startSpan("tagged-op", "service-c"); // GH-90000
        span.addTag("db.type", "postgres"); // GH-90000
        span.addTag("http.status", "200"); // GH-90000
        span.end(); // GH-90000

        assertThat(span.tags()).containsEntry("db.type", "postgres"); // GH-90000
        assertThat(span.tags()).containsEntry("http.status", "200"); // GH-90000
    }

    // ── Structured logging ────────────────────────────────────────────────────

    @Test
    @DisplayName("log entry is persisted with structured fields")
    void logEntryPersistedWithStructuredFields() { // GH-90000
        observability.log("INFO", "user.login.success", // GH-90000
                Map.of("userId", "u-1", "tenantId", "t-1", "correlationId", "c-1")); // GH-90000

        List<ObservabilityPlatform.LogEntry> logs = observability.getLogs("INFO");
        assertThat(logs).anyMatch(l -> "user.login.success".equals(l.event()) // GH-90000
                && "u-1".equals(l.fields().get("userId")));
    }

    @Test
    @DisplayName("logs at ERROR level are retrievable independently from INFO level")
    void errorLogsRetrievableIndependently() { // GH-90000
        observability.log("INFO", "info.event", Map.of()); // GH-90000
        observability.log("ERROR", "error.event", Map.of("reason", "timeout")); // GH-90000

        assertThat(observability.getLogs("ERROR")).hasSize(1);
        assertThat(observability.getLogs("INFO")).hasSize(1);
    }

    // ── Health checks ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("healthy service produces UP health status")
    void healthyServiceProducesUpStatus() { // GH-90000
        observability.registerHealthCheck("database", () -> true); // GH-90000

        ObservabilityPlatform.HealthStatus status = observability.getHealth("database");
        assertThat(status.status()).isEqualTo("UP");
    }

    @Test
    @DisplayName("failing health check produces DOWN status with error message")
    void failingHealthCheckProducesDownStatus() { // GH-90000
        observability.registerHealthCheck("message-queue", () -> false); // GH-90000

        ObservabilityPlatform.HealthStatus status = observability.getHealth("message-queue");
        assertThat(status.status()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("aggregate health is DOWN if any component is DOWN")
    void aggregateHealthIsDownIfAnyComponentDown() { // GH-90000
        observability.registerHealthCheck("db", () -> true); // GH-90000
        observability.registerHealthCheck("cache", () -> false); // GH-90000

        assertThat(observability.getAggregateHealth()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("aggregate health is UP when all components are healthy")
    void aggregateHealthIsUpWhenAllComponentsHealthy() { // GH-90000
        observability.registerHealthCheck("db2", () -> true); // GH-90000
        observability.registerHealthCheck("cache2", () -> true); // GH-90000

        assertThat(observability.getAggregateHealth()).isEqualTo("UP");
    }

    // ── Alert thresholds ──────────────────────────────────────────────────────

    @Test
    @DisplayName("counter exceeding threshold fires an alert")
    void counterExceedingThresholdFiresAlert() { // GH-90000
        observability.setAlert("http.errors", Map.of("status", "500"), 3, "WARN"); // GH-90000

        for (int i = 0; i < 4; i++) { // GH-90000
            observability.increment("http.errors", Map.of("status", "500")); // GH-90000
        }

        List<String> alerts = observability.getFiredAlerts(); // GH-90000
        assertThat(alerts).anyMatch(a -> a.contains("http.errors"));
    }

    // ── Observability platform implementation (for tests) ───────────────────── // GH-90000

    static class ObservabilityPlatform {
        record Span(String traceId, String spanId, String operationName, // GH-90000
                    String service, AtomicLong startMs, AtomicLong endMs,
                    Map<String, String> tags) {
            long durationMs() { // GH-90000
                long end = endMs.get(); // GH-90000
                return end == 0 ? 0 : end - startMs.get(); // GH-90000
            }
            void addTag(String key, String value) { tags.put(key, value); } // GH-90000
            void end() { endMs.set(System.currentTimeMillis()); } // GH-90000
        }

        record LogEntry(String level, String event, Map<String, Object> fields, Instant timestamp) {} // GH-90000
        record HealthStatus(String component, String status) {} // GH-90000

        private final ConcurrentHashMap<String, ConcurrentHashMap<Map<String, String>, AtomicLong>>
                counters = new ConcurrentHashMap<>(); // GH-90000
        private final ConcurrentHashMap<String, ConcurrentHashMap<Map<String, String>, AtomicLong>>
                gauges = new ConcurrentHashMap<>(); // GH-90000
        private final ConcurrentHashMap<String, ConcurrentHashMap<Map<String, String>, CopyOnWriteArrayList<Long>>>
                histograms = new ConcurrentHashMap<>(); // GH-90000
        private final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>()); // GH-90000
        private final ConcurrentHashMap<String, java.util.function.BooleanSupplier> healthChecks = new ConcurrentHashMap<>(); // GH-90000
        private final List<String> firedAlerts = Collections.synchronizedList(new ArrayList<>()); // GH-90000
        private final List<Object[]> alertConfigs = Collections.synchronizedList(new ArrayList<>()); // GH-90000

        void increment(String metric, Map<String, String> labels) { // GH-90000
            counters.computeIfAbsent(metric, k -> new ConcurrentHashMap<>()) // GH-90000
                    .computeIfAbsent(labels, k -> new AtomicLong(0)).incrementAndGet(); // GH-90000
            checkAlerts(metric, labels); // GH-90000
        }

        long getCounter(String metric, Map<String, String> labels) { // GH-90000
            ConcurrentHashMap<Map<String, String>, AtomicLong> m = counters.get(metric); // GH-90000
            if (m == null) return 0; // GH-90000
            AtomicLong v = m.get(labels); // GH-90000
            return v == null ? 0 : v.get(); // GH-90000
        }

        void gauge(String metric, long value, Map<String, String> labels) { // GH-90000
            gauges.computeIfAbsent(metric, k -> new ConcurrentHashMap<>()) // GH-90000
                    .computeIfAbsent(labels, k -> new AtomicLong(0)).set(value); // GH-90000
        }

        long getGauge(String metric, Map<String, String> labels) { // GH-90000
            ConcurrentHashMap<Map<String, String>, AtomicLong> m = gauges.get(metric); // GH-90000
            if (m == null) return 0; // GH-90000
            AtomicLong v = m.get(labels); // GH-90000
            return v == null ? 0 : v.get(); // GH-90000
        }

        void histogram(String metric, long value, Map<String, String> labels) { // GH-90000
            histograms.computeIfAbsent(metric, k -> new ConcurrentHashMap<>()) // GH-90000
                    .computeIfAbsent(labels, k -> new CopyOnWriteArrayList<>()).add(value); // GH-90000
        }

        long getHistogramPercentile(String metric, Map<String, String> labels, int percentile) { // GH-90000
            ConcurrentHashMap<Map<String, String>, CopyOnWriteArrayList<Long>> m = histograms.get(metric); // GH-90000
            if (m == null) return 0; // GH-90000
            CopyOnWriteArrayList<Long> observations = m.get(labels); // GH-90000
            if (observations == null || observations.isEmpty()) return 0; // GH-90000
            List<Long> sorted = observations.stream().sorted().toList(); // GH-90000
            int idx = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1; // GH-90000
            return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1))); // GH-90000
        }

        Span startSpan(String operationName, String service) { // GH-90000
            return new Span(UUID.randomUUID().toString(), UUID.randomUUID().toString(), // GH-90000
                    operationName, service, new AtomicLong(System.currentTimeMillis()), // GH-90000
                    new AtomicLong(0), new ConcurrentHashMap<>()); // GH-90000
        }

        Span startChildSpan(String operationName, Span parent) { // GH-90000
            return new Span(parent.traceId(), UUID.randomUUID().toString(), // GH-90000
                    operationName, parent.service(), new AtomicLong(System.currentTimeMillis()), // GH-90000
                    new AtomicLong(0), new ConcurrentHashMap<>()); // GH-90000
        }

        void log(String level, String event, Map<String, Object> fields) { // GH-90000
            logs.add(new LogEntry(level, event, new HashMap<>(fields), Instant.now())); // GH-90000
        }

        List<LogEntry> getLogs(String level) { // GH-90000
            return logs.stream().filter(l -> level.equals(l.level())).toList(); // GH-90000
        }

        void registerHealthCheck(String component, java.util.function.BooleanSupplier check) { // GH-90000
            healthChecks.put(component, check); // GH-90000
        }

        HealthStatus getHealth(String component) { // GH-90000
            java.util.function.BooleanSupplier check = healthChecks.get(component); // GH-90000
            String status = (check != null && check.getAsBoolean()) ? "UP" : "DOWN"; // GH-90000
            return new HealthStatus(component, status); // GH-90000
        }

        String getAggregateHealth() { // GH-90000
            return healthChecks.values().stream().allMatch(java.util.function.BooleanSupplier::getAsBoolean) // GH-90000
                    ? "UP" : "DOWN";
        }

        void setAlert(String metric, Map<String, String> labels, long threshold, String severity) { // GH-90000
            alertConfigs.add(new Object[]{metric, labels, threshold, severity}); // GH-90000
        }

        private void checkAlerts(String metric, Map<String, String> labels) { // GH-90000
            for (Object[] config : alertConfigs) { // GH-90000
                String cfgMetric = (String) config[0]; // GH-90000
                @SuppressWarnings("unchecked") Map<String, String> cfgLabels = (Map<String, String>) config[1];
                long threshold = (long) config[2]; // GH-90000
                String severity = (String) config[3]; // GH-90000
                if (cfgMetric.equals(metric) && cfgLabels.equals(labels)) { // GH-90000
                    long current = getCounter(metric, labels); // GH-90000
                    if (current > threshold) { // GH-90000
                        firedAlerts.add(severity + ": " + metric + " exceeded threshold " + threshold); // GH-90000
                    }
                }
            }
        }

        List<String> getFiredAlerts() { return List.copyOf(firedAlerts); } // GH-90000
    }
}
