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
@DisplayName("AepHttpServer – Observability (Phase-6) [GH-90000]")
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
        System.clearProperty("KAFKA_BOOTSTRAP_SERVERS [GH-90000]");
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
    @DisplayName("GET /health [GH-90000]")
    class HealthEndpoint {

        @Test
        @DisplayName("returns 200 with status field [GH-90000]")
        void returns200WithStatus() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body).containsKey("status [GH-90000]");
            assertThat(body).containsKey("version [GH-90000]");
            assertThat(body).containsKey("timestamp [GH-90000]");
        }

        @Test
        @DisplayName("returns correlation and trace headers for every request [GH-90000]")
        void returnsCorrelationAndTraceHeaders() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            assertThat(resp.headers().firstValue("X-Correlation-ID [GH-90000]")).hasValueSatisfying(value ->
                assertThat(value).isNotBlank()); // GH-90000
            assertThat(resp.headers().firstValue("traceparent [GH-90000]")).hasValueSatisfying(value ->
                assertThat(value).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01] [GH-90000]"));
        }

        @Test
        @DisplayName("echoes inbound correlation id and preserves inbound trace id [GH-90000]")
        void echoesInboundCorrelationAndTraceId() throws Exception { // GH-90000
            String traceId = "0123456789abcdef0123456789abcdef";
            HttpResponse<String> resp = get("/health", Map.of( // GH-90000
                "X-Correlation-ID", "corr-http-123",
                "traceparent", "00-" + traceId + "-1111222233334444-01",
                "tracestate", "vendor=test"
            ));

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            assertThat(resp.headers().firstValue("X-Correlation-ID [GH-90000]")).hasValue("corr-http-123 [GH-90000]");
            assertThat(resp.headers().firstValue("traceparent [GH-90000]")).hasValueSatisfying(value ->
                assertThat(value).startsWith("00-" + traceId + "-")); // GH-90000
            assertThat(resp.headers().firstValue("tracestate [GH-90000]")).hasValue("vendor=test [GH-90000]");
        }

        @Test
        @DisplayName("reports component statuses for no-DataCloud setup [GH-90000]")
        void reportsComponentStatusesWithNoDataCloud() throws Exception { // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(get("/health [GH-90000]").body(), Map.class);
            // Without DataCloud, data-cloud and run-ledger are "disabled" → degraded overall
            assertThat(body.get("status [GH-90000]")).isIn("healthy", "degraded");
            if (body.containsKey("components [GH-90000]")) {
                @SuppressWarnings("unchecked [GH-90000]")
                Map<String, Object> components = (Map<String, Object>) body.get("components [GH-90000]");
                assertThat(components).containsKey("data-cloud [GH-90000]");
                assertThat(components).containsKey("database [GH-90000]");
                assertThat(components).containsKey("redis [GH-90000]");
                assertThat(components).containsKey("review-queue [GH-90000]");
                assertThat(components).containsKey("run-ledger [GH-90000]");
            }
        }

        @Test
        @DisplayName("deep health probe includes deeper dependency detail [GH-90000]")
        void deepHealthProbeIncludesDeepDependencyDetail() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health/deep [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body).containsEntry("probe", "deep"); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> durability = (Map<String, Object>) body.get("durability [GH-90000]");
            assertThat(durability).containsEntry("mode", "ephemeral"); // GH-90000
            assertThat(durability).containsEntry("profile", "test"); // GH-90000
            assertThat(durability).containsEntry("dataCloudStorage", "disabled"); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> components = (Map<String, Object>) body.get("components [GH-90000]");
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
        @DisplayName("deep health probe verifies Kafka bootstrap connectivity when configured [GH-90000]")
        void deepHealthProbeVerifiesKafkaConnectivity() throws Exception { // GH-90000
            kafkaProbeSocket = new ServerSocket(0); // GH-90000
            System.setProperty("KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:" + kafkaProbeSocket.getLocalPort()); // GH-90000

            HttpResponse<String> resp = get("/health/deep [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> components = (Map<String, Object>) body.get("components [GH-90000]");
            assertThat(components).containsEntry("kafka.connectivity", "ok"); // GH-90000
        }

        @Test
        @DisplayName("deep health probe verifies Data Cloud connectivity when configured [GH-90000]")
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

            HttpResponse<String> resp = get("/health/deep [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> durability = (Map<String, Object>) body.get("durability [GH-90000]");
            assertThat(durability).containsEntry("mode", "durable"); // GH-90000
            assertThat(durability).containsEntry("dataCloudStorage", "embedded"); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> components = (Map<String, Object>) body.get("components [GH-90000]");
            assertThat(components).containsEntry("data-cloud.connectivity", "ok"); // GH-90000
            verify(mockDataCloud).queryEvents(anyString(), any(DataCloudClient.EventQuery.class)); // GH-90000
        }

        @Test
        @DisplayName("deep health probe reports degraded status when Data Cloud connectivity check fails [GH-90000]")
        void deepHealthProbeReportsConnectivityFailures() throws Exception { // GH-90000
            if (server != null) { // GH-90000
                server.stop(); // GH-90000
            }
            port = findFreePort(); // GH-90000

            DataCloudClient mockDataCloud = mock(DataCloudClient.class); // GH-90000
            when(mockDataCloud.entityStore()).thenReturn(mock(com.ghatana.datacloud.spi.EntityStore.class)); // GH-90000
            when(mockDataCloud.eventLogStore()).thenReturn(mock(EventLogStore.class)); // GH-90000
            when(mockDataCloud.queryEvents(anyString(), any(DataCloudClient.EventQuery.class))) // GH-90000
                .thenReturn(Promise.ofException(new IllegalStateException("probe failed [GH-90000]")));

            server = new AepHttpServer( // GH-90000
                engine,
                port,
                mockDataCloud,
                MetricsCollectorFactory.createNoop()); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/health/deep [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> components = (Map<String, Object>) body.get("components [GH-90000]");
            assertThat(body).containsEntry("status", "degraded"); // GH-90000
            assertThat(components.get("data-cloud.connectivity [GH-90000]")).asString()
                .startsWith("error: [GH-90000]")
                .contains("IllegalStateException [GH-90000]");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // /metrics/slo — SLO snapshot endpoint
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /metrics/slo [GH-90000]")
    class SloMetricsEndpoint {

        @Test
        @DisplayName("returns 200 with runCounts snapshot [GH-90000]")
        void returns200WithRunCounts() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/metrics/slo [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body).containsKey("runCounts [GH-90000]");
            assertThat(body).containsKey("replay [GH-90000]");
            assertThat(body).containsKey("agentExecution [GH-90000]");
            assertThat(body).containsKey("metricsLink [GH-90000]");
            assertThat(body).containsKey("timestamp [GH-90000]");
        }

        @Test
        @DisplayName("runCounts contains totalRuns, failedRuns, runFailureRate [GH-90000]")
        void runCountsHasExpectedFields() throws Exception { // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(get("/metrics/slo [GH-90000]").body(), Map.class);
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> runCounts = (Map<String, Object>) body.get("runCounts [GH-90000]");
            assertThat(runCounts).containsKeys("completedRuns", "totalRuns", "failedRuns", "runSuccessRate", "runFailureRate"); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> replay = (Map<String, Object>) body.get("replay [GH-90000]");
            assertThat(replay).containsKeys("attempts", "succeeded", "failed", "successRate", "failureRate"); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> agentExecution = (Map<String, Object>) body.get("agentExecution [GH-90000]");
            assertThat(agentExecution).containsKeys("attempts", "succeeded", "failed", "successRate", "failureRate"); // GH-90000
        }

        @Test
        @DisplayName("run counters increment after processing an event [GH-90000]")
        void runCountersIncrementAfterEventProcessing() throws Exception { // GH-90000
            // Baseline
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> before = mapper.readValue(get("/metrics/slo [GH-90000]").body(), Map.class);
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> beforeCounts = (Map<String, Object>) before.get("runCounts [GH-90000]");
            long totalBefore = ((Number) beforeCounts.get("totalRuns [GH-90000]")).longValue();

            // Process an event
            String event = mapper.writeValueAsString(Map.of( // GH-90000
                "tenantId", "test-tenant",
                "type", "user.action",
                "payload", Map.of("key", "value") // GH-90000
            ));
            HttpResponse<String> eventResp = post("/api/v1/events", event); // GH-90000
            assertThat(eventResp.statusCode()).isEqualTo(200); // GH-90000

            // Check counters increased
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> after = mapper.readValue(get("/metrics/slo [GH-90000]").body(), Map.class);
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> afterCounts = (Map<String, Object>) after.get("runCounts [GH-90000]");
            long totalAfter = ((Number) afterCounts.get("totalRuns [GH-90000]")).longValue();
            assertThat(totalAfter).isGreaterThan(totalBefore); // GH-90000
        }

        @Test
        @DisplayName("failed processing results increment failed run counts [GH-90000]")
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

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> before = mapper.readValue(get("/metrics/slo [GH-90000]").body(), Map.class);
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> beforeCounts = (Map<String, Object>) before.get("runCounts [GH-90000]");
            long totalBefore = ((Number) beforeCounts.get("totalRuns [GH-90000]")).longValue();
            long failedBefore = ((Number) beforeCounts.get("failedRuns [GH-90000]")).longValue();

            String event = mapper.writeValueAsString(Map.of( // GH-90000
                "tenantId", "test-tenant",
                "type", "user.action",
                "payload", Map.of("key", "value") // GH-90000
            ));
            HttpResponse<String> eventResp = post("/api/v1/events", event); // GH-90000

            assertThat(eventResp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> eventBody = mapper.readValue(eventResp.body(), Map.class); // GH-90000
            assertThat(eventBody).containsEntry("success", false); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> after = mapper.readValue(get("/metrics/slo [GH-90000]").body(), Map.class);
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> afterCounts = (Map<String, Object>) after.get("runCounts [GH-90000]");
            long totalAfter = ((Number) afterCounts.get("totalRuns [GH-90000]")).longValue();
            long failedAfter = ((Number) afterCounts.get("failedRuns [GH-90000]")).longValue();
            double runFailureRate = ((Number) afterCounts.get("runFailureRate [GH-90000]")).doubleValue();

            assertThat(totalAfter).isEqualTo(totalBefore + 1); // GH-90000
            assertThat(failedAfter).isEqualTo(failedBefore + 1); // GH-90000
            assertThat(runFailureRate).isGreaterThan(0.0); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────
    // /metrics/slo should be accessible alongside /metrics
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /metrics [GH-90000]")
    class MetricsEndpoint {

        @Test
        @DisplayName("still returns 200 with JVM stats after Phase-6 changes [GH-90000]")
        void returnsJvmStats() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/metrics [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body).containsKey("service [GH-90000]");
            assertThat(body).containsKey("memory_used_mb [GH-90000]");
        }

        @Test
        @DisplayName("returns Prometheus text format when registry is configured [GH-90000]")
        void returnsPrometheusTextFormatWhenRegistryConfigured() throws Exception { // GH-90000
            restartServerWithPrometheus(); // GH-90000
            Counter.builder("aep_test_counter [GH-90000]")
                .description("test counter for metrics scrape verification [GH-90000]")
                .register(prometheusRegistry) // GH-90000
                .increment(); // GH-90000

            HttpResponse<String> resp = get("/metrics [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            assertThat(resp.headers().firstValue("content-type [GH-90000]")).hasValueSatisfying(value ->
                assertThat(value).startsWith("text/plain; version=0.0.4 [GH-90000]"));
            assertThat(resp.body()).contains("# TYPE aep_test_counter_total counter [GH-90000]");
            assertThat(resp.body()).contains("aep_test_counter_total 1.0 [GH-90000]");
        }

        @Test
        @DisplayName("invokes PrometheusMeterRegistry.scrape when serving /metrics [GH-90000]")
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

            HttpResponse<String> resp = get("/metrics [GH-90000]");

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
