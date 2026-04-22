/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.application.observability.TraceExportService;
import com.ghatana.datacloud.entity.observability.Span;
import com.ghatana.datacloud.entity.observability.TraceExporter;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.promise.Promise;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for request observation, propagation, and metrics export behavior.
 *
 * @doc.type class
 * @doc.purpose Verifies runtime observability and async request metadata propagation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Observability [GH-90000]")
class DataCloudHttpServerObservabilityTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;
    private EntityStore mockEntityStore;
        private CapturingTraceExporter traceExporter;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        mockEntityStore = mock(EntityStore.class); // GH-90000
        when(mockClient.entityStore()).thenReturn(mockEntityStore); // GH-90000
                traceExporter = new CapturingTraceExporter(); // GH-90000
        port = findFreePort(); // GH-90000
    }

    @Override
    protected void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withTraceExportService(new TraceExportService(traceExporter, new NoopMetricsCollector())) // GH-90000
                .withTraceSamplingRate(1.0) // GH-90000
                .withMetricsCollector(MetricsCollectorFactory.create( // GH-90000
                        new PrometheusMeterRegistry(PrometheusConfig.DEFAULT))); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS); // GH-90000
    }

    @Test
    @DisplayName("propagates tenant and request id through async entity save chain [GH-90000]")
    void propagatesTenantAndRequestIdThroughAsyncEntitySaveChain() throws Exception { // GH-90000
        DataCloudClient.Entity saved = DataCloudClient.Entity.of( // GH-90000
                "ent-900",
                "orders",
                Map.of("status", "pending", "amount", 42)); // GH-90000
        when(mockClient.save(eq("acme [GH-90000]"), eq("orders [GH-90000]"), any())).thenReturn(Promise.of(saved));
        when(mockClient.appendEvent(eq("acme [GH-90000]"), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(4)));

        startServer(); // GH-90000

        HttpResponse<String> response = postJson( // GH-90000
                "/api/v1/entities/orders",
                Map.of("status", "pending", "amount", 42), // GH-90000
                Map.of("X-Tenant-ID", "acme", "X-Request-ID", "req-tenant-42")); // GH-90000

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        assertThat(response.headers().firstValue("X-Request-ID [GH-90000]")).hasValue("req-tenant-42 [GH-90000]");
        assertThat(response.headers().firstValue("X-Correlation-ID [GH-90000]")).hasValue("req-tenant-42 [GH-90000]");
        assertThat(response.headers().firstValue("traceparent [GH-90000]")).hasValueSatisfying(value ->
                assertThat(value).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01] [GH-90000]"));
        verify(mockClient).save(eq("acme [GH-90000]"), eq("orders [GH-90000]"), any());
        verify(mockClient).appendEvent(eq("acme [GH-90000]"), any());
    }

    @Test
    @DisplayName("preserves inbound trace id and request id on response headers [GH-90000]")
    void preservesInboundTraceAndRequestIdOnResponses() throws Exception { // GH-90000
        DataCloudClient.Entity saved = DataCloudClient.Entity.of( // GH-90000
                "ent-901",
                "orders",
                Map.of("status", "processing")); // GH-90000
        when(mockClient.save(eq("acme [GH-90000]"), eq("orders [GH-90000]"), any())).thenReturn(Promise.of(saved));
        when(mockClient.appendEvent(eq("acme [GH-90000]"), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(5)));

        startServer(); // GH-90000

        String traceId = "0123456789abcdef0123456789abcdef";
        HttpResponse<String> response = postJson( // GH-90000
                "/api/v1/entities/orders",
                Map.of("status", "processing"), // GH-90000
                Map.of( // GH-90000
                        "X-Tenant-ID", "acme",
                        "X-Request-ID", "req-trace-900",
                        "traceparent", "00-" + traceId + "-1111222233334444-01"));

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        assertThat(response.headers().firstValue("X-Request-ID [GH-90000]")).hasValue("req-trace-900 [GH-90000]");
        assertThat(response.headers().firstValue("traceparent [GH-90000]")).hasValueSatisfying(value ->
                assertThat(value).startsWith("00-" + traceId + "-")); // GH-90000
        assertThat(response.headers().firstValue("X-Parent-Span-Id [GH-90000]")).hasValue("1111222233334444 [GH-90000]");
    }

    @Test
    @DisplayName("exports Prometheus metrics for entity, event, and governance operations [GH-90000]")
    void exportsPrometheusMetricsForBusinessOperations() throws Exception { // GH-90000
        DataCloudClient.Entity saved = DataCloudClient.Entity.of( // GH-90000
                "ent-123",
                "products",
                Map.of("name", "Widget", "price", 9.99)); // GH-90000
        when(mockClient.save(any(), eq("products [GH-90000]"), any())).thenReturn(Promise.of(saved));
        when(mockClient.appendEvent(any(), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(7))); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> entityResponse = postJson( // GH-90000
                "/api/v1/entities/products",
                Map.of("name", "Widget", "price", 9.99), // GH-90000
                Map.of("X-Tenant-ID", "tenant-observe")); // GH-90000
        assertThat(entityResponse.statusCode()).isEqualTo(200); // GH-90000

        HttpResponse<String> eventResponse = postJson( // GH-90000
                "/api/v1/events",
                Map.of("type", "order.placed", "payload", Map.of("orderId", "ORD-1")), // GH-90000
                Map.of("X-Tenant-ID", "tenant-observe")); // GH-90000
        assertThat(eventResponse.statusCode()).isEqualTo(200); // GH-90000

        HttpResponse<String> governanceResponse = postJson( // GH-90000
                "/api/v1/governance/retention/classify",
                Map.of("collection", "products", "tier", "standard", "reason", "retention-policy"), // GH-90000
                Map.of("X-Tenant-ID", "tenant-observe")); // GH-90000
        assertThat(governanceResponse.statusCode()).isEqualTo(200); // GH-90000

        HttpResponse<String> metricsResponse = get("/metrics [GH-90000]");

        assertThat(metricsResponse.statusCode()).isEqualTo(200); // GH-90000
        assertThat(metricsResponse.headers().firstValue("content-type [GH-90000]"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("text/plain [GH-90000]"));
        assertThat(metricsResponse.body()).contains(DataCloudBusinessMetrics.METRIC_ENTITY_TOTAL); // GH-90000
        assertThat(metricsResponse.body()).contains(DataCloudBusinessMetrics.METRIC_EVENT_APPEND_TOTAL); // GH-90000
        assertThat(metricsResponse.body()).contains(DataCloudBusinessMetrics.METRIC_GOVERNANCE_TOTAL); // GH-90000
                assertThat(metricsResponse.body()).contains("tenant-observe [GH-90000]");
    }

        @Test
        @DisplayName("exports request to handler to store parent-child spans for entity save [GH-90000]")
        void exportsParentChildTraceSpansForEntitySave() throws Exception { // GH-90000
                DataCloudClient.Entity saved = DataCloudClient.Entity.of( // GH-90000
                                "ent-trace-1",
                                "orders",
                                Map.of("status", "pending", "amount", 42)); // GH-90000
                when(mockClient.save(eq("acme [GH-90000]"), eq("orders [GH-90000]"), any())).thenReturn(Promise.of(saved));
                when(mockClient.appendEvent(eq("acme [GH-90000]"), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(8)));

                startServer(); // GH-90000

                HttpResponse<String> response = postJson( // GH-90000
                                "/api/v1/entities/orders",
                                Map.of("status", "pending", "amount", 42), // GH-90000
                                Map.of("X-Tenant-ID", "acme", "X-Request-ID", "req-trace-101")); // GH-90000

                assertThat(response.statusCode()).isEqualTo(200); // GH-90000

                List<Span> spans = traceExporter.allSpans(); // GH-90000
                Span requestSpan = findSpan(spans, "post /api/v1/entities/orders"); // GH-90000
                Span handlerSpan = findSpan(spans, "datacloud.http.entity.save"); // GH-90000
                Span storeSpan = findSpan(spans, "datacloud.entity.store.save"); // GH-90000
                Span eventSpan = findSpan(spans, "datacloud.event.store.append"); // GH-90000

                assertThat(requestSpan.getAttributes()).containsEntry("request.id", "req-trace-101"); // GH-90000
                assertThat(handlerSpan.getParentSpanId()).isEqualTo(requestSpan.getSpanId()); // GH-90000
                assertThat(storeSpan.getParentSpanId()).isEqualTo(handlerSpan.getSpanId()); // GH-90000
                assertThat(eventSpan.getParentSpanId()).isEqualTo(handlerSpan.getSpanId()); // GH-90000
                assertThat(storeSpan.getAttributes()).containsEntry("collection", "orders"); // GH-90000
        }

        private static Span findSpan(List<Span> spans, String operationName) { // GH-90000
                return spans.stream() // GH-90000
                                .filter(span -> operationName.equals(span.getOperationName())) // GH-90000
                                .findFirst() // GH-90000
                                .orElseThrow(() -> new AssertionError("Missing span: " + operationName)); // GH-90000
        }

        private static final class CapturingTraceExporter implements TraceExporter {
                private final List<Span> spans = new ArrayList<>(); // GH-90000

                @Override
                public Promise<ExportResult> exportSpans(List<Span> spans) { // GH-90000
                        this.spans.addAll(spans); // GH-90000
                        return Promise.of(new ExportResult(true, spans.size(), 0, List.of(), 0L)); // GH-90000
                }

                @Override
                public ExportConfig getConfig() { // GH-90000
                        return new ExportConfig("memory://trace-test", 100, 1000L, false, 1, 100L); // GH-90000
                }

                @Override
                public Promise<Boolean> isHealthy() { // GH-90000
                        return Promise.of(true); // GH-90000
                }

                private List<Span> allSpans() { // GH-90000
                        return List.copyOf(spans); // GH-90000
                }
        }
}