/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Phase-6 observability endpoints.
 *
 * <p>Covers {@code GET /health} (dependency aggregation), {@code GET /metrics/slo}
 * (SLO snapshot), and basic event-processing → SLO counter wiring.
 *
 * @doc.type class
 * @doc.purpose Integration tests for Phase-6 health + SLO metric endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepHttpServer – Observability (Phase-6)")
class AepHttpServerObservabilityTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private PrometheusMeterRegistry prometheusRegistry;
    private ServerSocket kafkaProbeSocket;

    @BeforeEach
    void setUp() throws Exception {
        engine = Aep.forTesting();
        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();
        startServer(false);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("KAFKA_BOOTSTRAP_SERVERS");
        if (kafkaProbeSocket != null) {
            try {
                kafkaProbeSocket.close();
            } catch (IOException ignored) {
                // best-effort cleanup for test probe socket
            }
        }
        if (server != null) server.stop();
        if (prometheusRegistry != null) prometheusRegistry.close();
        if (engine != null) engine.close();
    }

    // ──────────────────────────────────────────────────────────────
    // /health — dependency probe responses
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /health")
    class HealthEndpoint {

        @Test
        @DisplayName("returns 200 with status field")
        void returns200WithStatus() throws Exception {
            HttpResponse<String> resp = get("/health");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("status");
            assertThat(body).containsKey("version");
            assertThat(body).containsKey("timestamp");
        }

        @Test
        @DisplayName("returns correlation and trace headers for every request")
        void returnsCorrelationAndTraceHeaders() throws Exception {
            HttpResponse<String> resp = get("/health");

            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.headers().firstValue("X-Correlation-ID")).hasValueSatisfying(value ->
                assertThat(value).isNotBlank());
            assertThat(resp.headers().firstValue("traceparent")).hasValueSatisfying(value ->
                assertThat(value).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]"));
        }

        @Test
        @DisplayName("echoes inbound correlation id and preserves inbound trace id")
        void echoesInboundCorrelationAndTraceId() throws Exception {
            String traceId = "0123456789abcdef0123456789abcdef";
            HttpResponse<String> resp = get("/health", Map.of(
                "X-Correlation-ID", "corr-http-123",
                "traceparent", "00-" + traceId + "-1111222233334444-01",
                "tracestate", "vendor=test"
            ));

            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.headers().firstValue("X-Correlation-ID")).hasValue("corr-http-123");
            assertThat(resp.headers().firstValue("traceparent")).hasValueSatisfying(value ->
                assertThat(value).startsWith("00-" + traceId + "-"));
            assertThat(resp.headers().firstValue("tracestate")).hasValue("vendor=test");
        }

        @Test
        @DisplayName("reports component statuses for no-DataCloud setup")
        void reportsComponentStatusesWithNoDataCloud() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(get("/health").body(), Map.class);
            // Without DataCloud, data-cloud and run-ledger are "disabled" → degraded overall
            assertThat(body.get("status")).isIn("healthy", "degraded");
            if (body.containsKey("components")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> components = (Map<String, Object>) body.get("components");
                assertThat(components).containsKey("data-cloud");
                assertThat(components).containsKey("database");
                assertThat(components).containsKey("redis");
                assertThat(components).containsKey("review-queue");
                assertThat(components).containsKey("run-ledger");
            }
        }

        @Test
        @DisplayName("deep health probe includes deeper dependency detail")
        void deepHealthProbeIncludesDeepDependencyDetail() throws Exception {
            HttpResponse<String> resp = get("/health/deep");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsEntry("probe", "deep");
            @SuppressWarnings("unchecked")
            Map<String, Object> durability = (Map<String, Object>) body.get("durability");
            assertThat(durability).containsEntry("mode", "ephemeral");
            assertThat(durability).containsEntry("profile", "test");
            assertThat(durability).containsEntry("dataCloudStorage", "disabled");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsKeys(
                "database",
                "redis",
                "event-loop",
                "heap-memory",
                "data-cloud.entity-store",
                "data-cloud.event-log",
                "pipeline-storage",
                "memory-store",
                "execution-history",
                "kafka.connectivity");
        }

        @Test
        @DisplayName("deep health probe verifies Kafka bootstrap connectivity when configured")
        void deepHealthProbeVerifiesKafkaConnectivity() throws Exception {
            kafkaProbeSocket = new ServerSocket(0);
            System.setProperty("KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:" + kafkaProbeSocket.getLocalPort());

            HttpResponse<String> resp = get("/health/deep");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsEntry("kafka.connectivity", "ok");
        }

        @Test
        @DisplayName("deep health probe verifies Data Cloud connectivity when configured")
        void deepHealthProbeVerifiesDataCloudConnectivity() throws Exception {
            if (server != null) {
                server.stop();
            }
            port = findFreePort();

            DataCloudClient mockDataCloud = mock(DataCloudClient.class);
            when(mockDataCloud.entityStore()).thenReturn(mock(com.ghatana.datacloud.spi.EntityStore.class));
            when(mockDataCloud.eventLogStore()).thenReturn(mock(EventLogStore.class));
            when(mockDataCloud.queryEvents(anyString(), any(DataCloudClient.EventQuery.class)))
                .thenReturn(Promise.of(java.util.List.of()));

            server = new AepHttpServer(
                engine,
                port,
                mockDataCloud,
                MetricsCollectorFactory.createNoop());
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/health/deep");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> durability = (Map<String, Object>) body.get("durability");
            assertThat(durability).containsEntry("mode", "durable");
            assertThat(durability).containsEntry("dataCloudStorage", "embedded");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsEntry("data-cloud.connectivity", "ok");
            verify(mockDataCloud).queryEvents(anyString(), any(DataCloudClient.EventQuery.class));
        }

        @Test
        @DisplayName("deep health probe reports degraded status when Data Cloud connectivity check fails")
        void deepHealthProbeReportsConnectivityFailures() throws Exception {
            if (server != null) {
                server.stop();
            }
            port = findFreePort();

            DataCloudClient mockDataCloud = mock(DataCloudClient.class);
            when(mockDataCloud.entityStore()).thenReturn(mock(com.ghatana.datacloud.spi.EntityStore.class));
            when(mockDataCloud.eventLogStore()).thenReturn(mock(EventLogStore.class));
            when(mockDataCloud.queryEvents(anyString(), any(DataCloudClient.EventQuery.class)))
                .thenReturn(Promise.ofException(new IllegalStateException("probe failed")));

            server = new AepHttpServer(
                engine,
                port,
                mockDataCloud,
                MetricsCollectorFactory.createNoop());
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/health/deep");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(body).containsEntry("status", "degraded");
            assertThat(components.get("data-cloud.connectivity")).asString()
                .startsWith("error:")
                .contains("IllegalStateException");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // /metrics/slo — SLO snapshot endpoint
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /metrics/slo")
    class SloMetricsEndpoint {

        @Test
        @DisplayName("returns 200 with runCounts snapshot")
        void returns200WithRunCounts() throws Exception {
            HttpResponse<String> resp = get("/metrics/slo");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("runCounts");
            assertThat(body).containsKey("replay");
            assertThat(body).containsKey("agentExecution");
            assertThat(body).containsKey("metricsLink");
            assertThat(body).containsKey("timestamp");
        }

        @Test
        @DisplayName("runCounts contains totalRuns, failedRuns, runFailureRate")
        void runCountsHasExpectedFields() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> runCounts = (Map<String, Object>) body.get("runCounts");
            assertThat(runCounts).containsKeys("completedRuns", "totalRuns", "failedRuns", "runSuccessRate", "runFailureRate");

            @SuppressWarnings("unchecked")
            Map<String, Object> replay = (Map<String, Object>) body.get("replay");
            assertThat(replay).containsKeys("attempts", "succeeded", "failed", "successRate", "failureRate");

            @SuppressWarnings("unchecked")
            Map<String, Object> agentExecution = (Map<String, Object>) body.get("agentExecution");
            assertThat(agentExecution).containsKeys("attempts", "succeeded", "failed", "successRate", "failureRate");
        }

        @Test
        @DisplayName("run counters increment after processing an event")
        void runCountersIncrementAfterEventProcessing() throws Exception {
            // Baseline
            @SuppressWarnings("unchecked")
            Map<String, Object> before = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> beforeCounts = (Map<String, Object>) before.get("runCounts");
            long totalBefore = ((Number) beforeCounts.get("totalRuns")).longValue();

            // Process an event
            String event = mapper.writeValueAsString(Map.of(
                "tenantId", "test-tenant",
                "type", "user.action",
                "payload", Map.of("key", "value")
            ));
            HttpResponse<String> eventResp = post("/api/v1/events", event);
            assertThat(eventResp.statusCode()).isEqualTo(200);

            // Check counters increased
            @SuppressWarnings("unchecked")
            Map<String, Object> after = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> afterCounts = (Map<String, Object>) after.get("runCounts");
            long totalAfter = ((Number) afterCounts.get("totalRuns")).longValue();
            assertThat(totalAfter).isGreaterThan(totalBefore);
        }

        @Test
        @DisplayName("failed processing results increment failed run counts")
        void failedProcessingResultsIncrementFailedRunCounts() throws Exception {
            if (server != null) {
                server.stop();
            }
            if (engine != null) {
                engine.close();
            }
            port = findFreePort();

            engine = spy(Aep.forTesting());
            doReturn(Promise.of(AepEngine.ProcessingResult.failed("evt-failed", "engine failure")))
                .when(engine).process(anyString(), any(AepEngine.Event.class));

            server = new AepHttpServer(engine, port);
            server.start();
            waitForServerReady(port);

            @SuppressWarnings("unchecked")
            Map<String, Object> before = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> beforeCounts = (Map<String, Object>) before.get("runCounts");
            long totalBefore = ((Number) beforeCounts.get("totalRuns")).longValue();
            long failedBefore = ((Number) beforeCounts.get("failedRuns")).longValue();

            String event = mapper.writeValueAsString(Map.of(
                "tenantId", "test-tenant",
                "type", "user.action",
                "payload", Map.of("key", "value")
            ));
            HttpResponse<String> eventResp = post("/api/v1/events", event);

            assertThat(eventResp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> eventBody = mapper.readValue(eventResp.body(), Map.class);
            assertThat(eventBody).containsEntry("success", false);

            @SuppressWarnings("unchecked")
            Map<String, Object> after = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> afterCounts = (Map<String, Object>) after.get("runCounts");
            long totalAfter = ((Number) afterCounts.get("totalRuns")).longValue();
            long failedAfter = ((Number) afterCounts.get("failedRuns")).longValue();
            double runFailureRate = ((Number) afterCounts.get("runFailureRate")).doubleValue();

            assertThat(totalAfter).isEqualTo(totalBefore + 1);
            assertThat(failedAfter).isEqualTo(failedBefore + 1);
            assertThat(runFailureRate).isGreaterThan(0.0);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // /metrics/slo should be accessible alongside /metrics
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /metrics")
    class MetricsEndpoint {

        @Test
        @DisplayName("still returns 200 with JVM stats after Phase-6 changes")
        void returnsJvmStats() throws Exception {
            HttpResponse<String> resp = get("/metrics");
            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("service");
            assertThat(body).containsKey("memory_used_mb");
        }

        @Test
        @DisplayName("returns Prometheus text format when registry is configured")
        void returnsPrometheusTextFormatWhenRegistryConfigured() throws Exception {
            restartServerWithPrometheus();
            Counter.builder("aep_test_counter")
                .description("test counter for metrics scrape verification")
                .register(prometheusRegistry)
                .increment();

            HttpResponse<String> resp = get("/metrics");

            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.headers().firstValue("content-type")).hasValueSatisfying(value ->
                assertThat(value).startsWith("text/plain; version=0.0.4"));
            assertThat(resp.body()).contains("# TYPE aep_test_counter_total counter");
            assertThat(resp.body()).contains("aep_test_counter_total 1.0");
        }

        @Test
        @DisplayName("invokes PrometheusMeterRegistry.scrape when serving /metrics")
        void invokesPrometheusRegistryScrape() throws Exception {
            if (server != null) {
                server.stop();
            }
            if (prometheusRegistry != null) {
                prometheusRegistry.close();
            }
            port = findFreePort();
            prometheusRegistry = spy(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
            server = new AepHttpServer(
                engine,
                port,
                null,
                null,
                MetricsCollectorFactory.create(prometheusRegistry),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                prometheusRegistry);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/metrics");

            assertThat(resp.statusCode()).isEqualTo(200);
            verify(prometheusRegistry).scrape();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws Exception {
        return get(path, Map.of());
    }

    private HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://127.0.0.1:" + port + path));
        headers.forEach(builder::header);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return post(path, body, Map.of());
    }

    private HttpResponse<String> post(String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json");
        headers.forEach(builder::header);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void restartServerWithPrometheus() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (prometheusRegistry != null) {
            prometheusRegistry.close();
        }
        port = findFreePort();
        startServer(true);
    }

    private void startServer(boolean withPrometheus) throws Exception {
        prometheusRegistry = withPrometheus ? new PrometheusMeterRegistry(PrometheusConfig.DEFAULT) : null;
        server = withPrometheus
            ? new AepHttpServer(
                engine,
                port,
                null,
                null,
                MetricsCollectorFactory.create(prometheusRegistry),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                prometheusRegistry)
            : new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                new Socket("127.0.0.1", port).close();
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new AssertionError("Server did not start on port " + port + " within 5 s");
    }
}
