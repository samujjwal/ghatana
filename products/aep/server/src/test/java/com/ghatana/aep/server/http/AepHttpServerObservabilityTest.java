/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
import org.junit.jupiter.api.Tag;
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
 * <p>Covers {@code GET /health} (dependency aggregation), {@code GET /metrics/slo} // GH-90000
 * (SLO snapshot), and basic event-processing → SLO counter wiring. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Integration tests for Phase-6 health + SLO metric endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("local-network")
@DisplayName("AepHttpServer – Observability (Phase-6)")
class AepHttpServerObservabilityTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000
    private PrometheusMeterRegistry prometheusRegistry;
    private ServerSocket kafkaProbeSocket;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        port = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
        startServer(false); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        System.clearProperty("KAFKA_BOOTSTRAP_SERVERS");
        if (kafkaProbeSocket != null) { // GH-90000
            try {
                kafkaProbeSocket.close(); // GH-90000
            } catch (IOException ignored) { // GH-90000
                // best-effort cleanup for test probe socket
            }
        }
        if (server != null) server.stop(); // GH-90000
        if (prometheusRegistry != null) prometheusRegistry.close(); // GH-90000
        if (engine != null) engine.close(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────
    // /health — dependency probe responses
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /health")
    class HealthEndpoint {

        @Test
        @DisplayName("returns 200 with status field")
        void returns200WithStatus() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body).containsKey("status");
            assertThat(body).containsKey("version");
            assertThat(body).containsKey("timestamp");
        }

        @Test
        @DisplayName("returns correlation and trace headers for every request")
        void returnsCorrelationAndTraceHeaders() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            assertThat(resp.headers().firstValue("X-Correlation-ID")).hasValueSatisfying(value ->
                assertThat(value).isNotBlank()); // GH-90000
            assertThat(resp.headers().firstValue("traceparent")).hasValueSatisfying(value ->
                assertThat(value).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]"));
        }

        @Test
        @DisplayName("echoes inbound correlation id and preserves inbound trace id")
        void echoesInboundCorrelationAndTraceId() throws Exception { // GH-90000
            String traceId = "0123456789abcdef0123456789abcdef";
            HttpResponse<String> resp = get("/health", Map.of( // GH-90000
                "X-Correlation-ID", "corr-http-123",
                "traceparent", "00-" + traceId + "-1111222233334444-01",
                "tracestate", "vendor=test"
            ));

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            assertThat(resp.headers().firstValue("X-Correlation-ID")).hasValue("corr-http-123");
            assertThat(resp.headers().firstValue("traceparent")).hasValueSatisfying(value ->
                assertThat(value).startsWith("00-" + traceId + "-")); // GH-90000
            assertThat(resp.headers().firstValue("tracestate")).hasValue("vendor=test");
        }

        @Test
        @DisplayName("reports component statuses for no-DataCloud setup")
        void reportsComponentStatusesWithNoDataCloud() throws Exception { // GH-90000
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
        void deepHealthProbeIncludesDeepDependencyDetail() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health/deep");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body).containsEntry("probe", "deep"); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> durability = (Map<String, Object>) body.get("durability");
            assertThat(durability).containsEntry("mode", "ephemeral"); // GH-90000
            assertThat(durability).containsEntry("profile", "test"); // GH-90000
            assertThat(durability).containsEntry("dataCloudStorage", "disabled"); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsKeys( // GH-90000
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
        void deepHealthProbeVerifiesKafkaConnectivity() throws Exception { // GH-90000
            kafkaProbeSocket = new ServerSocket(0); // GH-90000
            System.setProperty("KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:" + kafkaProbeSocket.getLocalPort()); // GH-90000

            HttpResponse<String> resp = get("/health/deep");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsEntry("kafka.connectivity", "ok"); // GH-90000
        }

        @Test
        @DisplayName("deep health probe verifies Data Cloud connectivity when configured")
        void deepHealthProbeVerifiesDataCloudConnectivity() throws Exception { // GH-90000
            if (server != null) { // GH-90000
                server.stop(); // GH-90000
            }
            port = findFreePort(); // GH-90000

            DataCloudClient mockDataCloud = mock(DataCloudClient.class); // GH-90000
            when(mockDataCloud.entityStore()).thenReturn(mock(com.ghatana.datacloud.spi.EntityStore.class)); // GH-90000
            when(mockDataCloud.eventLogStore()).thenReturn(mock(EventLogStore.class)); // GH-90000
            when(mockDataCloud.queryEvents(anyString(), any(DataCloudClient.EventQuery.class))) // GH-90000
                .thenReturn(Promise.of(java.util.List.of())); // GH-90000

            server = new AepHttpServer( // GH-90000
                engine,
                port,
                mockDataCloud,
                MetricsCollectorFactory.createNoop()); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/health/deep");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> durability = (Map<String, Object>) body.get("durability");
            assertThat(durability).containsEntry("mode", "durable"); // GH-90000
            assertThat(durability).containsEntry("dataCloudStorage", "embedded"); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsEntry("data-cloud.connectivity", "ok"); // GH-90000
            verify(mockDataCloud).queryEvents(anyString(), any(DataCloudClient.EventQuery.class)); // GH-90000
        }

        @Test
        @DisplayName("deep health probe reports degraded status when Data Cloud connectivity check fails")
        void deepHealthProbeReportsConnectivityFailures() throws Exception { // GH-90000
            if (server != null) { // GH-90000
                server.stop(); // GH-90000
            }
            port = findFreePort(); // GH-90000

            DataCloudClient mockDataCloud = mock(DataCloudClient.class); // GH-90000
            when(mockDataCloud.entityStore()).thenReturn(mock(com.ghatana.datacloud.spi.EntityStore.class)); // GH-90000
            when(mockDataCloud.eventLogStore()).thenReturn(mock(EventLogStore.class)); // GH-90000
            when(mockDataCloud.queryEvents(anyString(), any(DataCloudClient.EventQuery.class))) // GH-90000
                .thenReturn(Promise.ofException(new IllegalStateException("probe failed")));

            server = new AepHttpServer( // GH-90000
                engine,
                port,
                mockDataCloud,
                MetricsCollectorFactory.createNoop()); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/health/deep");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(body).containsEntry("status", "degraded"); // GH-90000
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
        void returns200WithRunCounts() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/metrics/slo");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body).containsKey("runCounts");
            assertThat(body).containsKey("replay");
            assertThat(body).containsKey("agentExecution");
            assertThat(body).containsKey("metricsLink");
            assertThat(body).containsKey("timestamp");
        }

        @Test
        @DisplayName("runCounts contains totalRuns, failedRuns, runFailureRate")
        void runCountsHasExpectedFields() throws Exception { // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> runCounts = (Map<String, Object>) body.get("runCounts");
            assertThat(runCounts).containsKeys("completedRuns", "totalRuns", "failedRuns", "runSuccessRate", "runFailureRate"); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> replay = (Map<String, Object>) body.get("replay");
            assertThat(replay).containsKeys("attempts", "succeeded", "failed", "successRate", "failureRate"); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> agentExecution = (Map<String, Object>) body.get("agentExecution");
            assertThat(agentExecution).containsKeys("attempts", "succeeded", "failed", "successRate", "failureRate"); // GH-90000
        }

        @Test
        @DisplayName("run counters increment after processing an event")
        void runCountersIncrementAfterEventProcessing() throws Exception { // GH-90000
            // Baseline
            @SuppressWarnings("unchecked")
            Map<String, Object> before = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> beforeCounts = (Map<String, Object>) before.get("runCounts");
            long totalBefore = ((Number) beforeCounts.get("totalRuns")).longValue();

            // Process an event
            String event = mapper.writeValueAsString(Map.of( // GH-90000
                "tenantId", "test-tenant",
                "type", "user.action",
                "payload", Map.of("key", "value") // GH-90000
            ));
            HttpResponse<String> eventResp = post("/api/v1/events", event); // GH-90000
            assertThat(eventResp.statusCode()).isEqualTo(200); // GH-90000

            // Check counters increased
            @SuppressWarnings("unchecked")
            Map<String, Object> after = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> afterCounts = (Map<String, Object>) after.get("runCounts");
            long totalAfter = ((Number) afterCounts.get("totalRuns")).longValue();
            assertThat(totalAfter).isGreaterThan(totalBefore); // GH-90000
        }

        @Test
        @DisplayName("failed processing results increment failed run counts")
        void failedProcessingResultsIncrementFailedRunCounts() throws Exception { // GH-90000
            if (server != null) { // GH-90000
                server.stop(); // GH-90000
            }
            if (engine != null) { // GH-90000
                engine.close(); // GH-90000
            }
            port = findFreePort(); // GH-90000

            engine = spy(Aep.forTesting()); // GH-90000
            doReturn(Promise.of(AepEngine.ProcessingResult.failed("evt-failed", "engine failure"))) // GH-90000
                .when(engine).process(anyString(), any(AepEngine.Event.class)); // GH-90000

            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> before = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> beforeCounts = (Map<String, Object>) before.get("runCounts");
            long totalBefore = ((Number) beforeCounts.get("totalRuns")).longValue();
            long failedBefore = ((Number) beforeCounts.get("failedRuns")).longValue();

            String event = mapper.writeValueAsString(Map.of( // GH-90000
                "tenantId", "test-tenant",
                "type", "user.action",
                "payload", Map.of("key", "value") // GH-90000
            ));
            HttpResponse<String> eventResp = post("/api/v1/events", event); // GH-90000

            assertThat(eventResp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> eventBody = mapper.readValue(eventResp.body(), Map.class); // GH-90000
            assertThat(eventBody).containsEntry("success", false); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> after = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> afterCounts = (Map<String, Object>) after.get("runCounts");
            long totalAfter = ((Number) afterCounts.get("totalRuns")).longValue();
            long failedAfter = ((Number) afterCounts.get("failedRuns")).longValue();
            double runFailureRate = ((Number) afterCounts.get("runFailureRate")).doubleValue();

            assertThat(totalAfter).isEqualTo(totalBefore + 1); // GH-90000
            assertThat(failedAfter).isEqualTo(failedBefore + 1); // GH-90000
            assertThat(runFailureRate).isGreaterThan(0.0); // GH-90000
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
        void returnsJvmStats() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/metrics");
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body).containsKey("service");
            assertThat(body).containsKey("memory_used_mb");
        }

        @Test
        @DisplayName("returns Prometheus text format when registry is configured")
        void returnsPrometheusTextFormatWhenRegistryConfigured() throws Exception { // GH-90000
            restartServerWithPrometheus(); // GH-90000
            Counter.builder("aep_test_counter")
                .description("test counter for metrics scrape verification")
                .register(prometheusRegistry) // GH-90000
                .increment(); // GH-90000

            HttpResponse<String> resp = get("/metrics");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            assertThat(resp.headers().firstValue("content-type")).hasValueSatisfying(value ->
                assertThat(value).startsWith("text/plain; version=0.0.4"));
            assertThat(resp.body()).contains("# TYPE aep_test_counter_total counter");
            assertThat(resp.body()).contains("aep_test_counter_total 1.0");
        }

        @Test
        @DisplayName("invokes PrometheusMeterRegistry.scrape when serving /metrics")
        void invokesPrometheusRegistryScrape() throws Exception { // GH-90000
            if (server != null) { // GH-90000
                server.stop(); // GH-90000
            }
            if (prometheusRegistry != null) { // GH-90000
                prometheusRegistry.close(); // GH-90000
            }
            port = findFreePort(); // GH-90000
            prometheusRegistry = spy(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)); // GH-90000
            server = new AepHttpServer( // GH-90000
                engine,
                port,
                null,
                null,
                MetricsCollectorFactory.create(prometheusRegistry), // GH-90000
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                prometheusRegistry);
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/metrics");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            verify(prometheusRegistry).scrape(); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        return get(path, Map.of()); // GH-90000
    }

    private HttpResponse<String> get(String path, Map<String, String> headers) throws Exception { // GH-90000
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)); // GH-90000
        headers.forEach(builder::header); // GH-90000
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        return post(path, body, Map.of()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body, Map<String, String> headers) throws Exception { // GH-90000
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json"); // GH-90000
        headers.forEach(builder::header); // GH-90000
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private void restartServerWithPrometheus() throws Exception { // GH-90000
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
        if (prometheusRegistry != null) { // GH-90000
            prometheusRegistry.close(); // GH-90000
        }
        port = findFreePort(); // GH-90000
        startServer(true); // GH-90000
    }

    private void startServer(boolean withPrometheus) throws Exception { // GH-90000
        prometheusRegistry = withPrometheus ? new PrometheusMeterRegistry(PrometheusConfig.DEFAULT) : null; // GH-90000
        server = withPrometheus
            ? new AepHttpServer( // GH-90000
                engine,
                port,
                null,
                null,
                MetricsCollectorFactory.create(prometheusRegistry), // GH-90000
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                prometheusRegistry)
            : new AepHttpServer(engine, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket ss = new ServerSocket(0)) { // GH-90000
            return ss.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + 5_000; // GH-90000
        while (System.currentTimeMillis() < deadline) { // GH-90000
            try {
                new Socket("127.0.0.1", port).close(); // GH-90000
                return;
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new AssertionError("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
