/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.application.observability.TraceExportService;
import com.ghatana.datacloud.entity.observability.Span;
import com.ghatana.datacloud.entity.observability.TraceExporter;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.platform.observability.NoopMetricsCollector;
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
@DisplayName("DataCloudHttpServer – Observability")
class DataCloudHttpServerObservabilityTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;
    private EntityStore mockEntityStore;
        private CapturingTraceExporter traceExporter;

    @BeforeEach
    void setUp() throws Exception { 
        mockClient = mock(DataCloudClient.class); 
        mockEntityStore = mock(EntityStore.class); 
        when(mockClient.entityStore()).thenReturn(mockEntityStore); 
                traceExporter = new CapturingTraceExporter(); 
        port = findFreePort(); 
    }

    @Override
    protected void startServer() throws Exception { 
        AuditService mockAuditService = mock(AuditService.class);
        when(mockAuditService.record(any())).thenReturn(Promise.of(null)); 
        server = new DataCloudHttpServer(mockClient, port) 
                .withTraceExportService(new TraceExportService(traceExporter, new NoopMetricsCollector())) 
                .withTraceSamplingRate(1.0) 
                .withMetricsCollector(MetricsCollectorFactory.create( 
                        new PrometheusMeterRegistry(PrometheusConfig.DEFAULT))) 
                .withAuditService(mockAuditService); 
        server.start(); 
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS); 
    }

    @Test
    @DisplayName("propagates tenant and request id through async entity save chain")
    void propagatesTenantAndRequestIdThroughAsyncEntitySaveChain() throws Exception { 
        DataCloudClient.Entity saved = DataCloudClient.Entity.of( 
                "ent-900",
                "orders",
                Map.of("status", "pending", "amount", 42)); 
        when(mockClient.save(eq("acme"), eq("orders"), any())).thenReturn(Promise.of(saved));
        when(mockClient.appendEvent(eq("acme"), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(4)));

        startServer(); 

        HttpResponse<String> response = postJson( 
                "/api/v1/entities/orders",
                Map.of("status", "pending", "amount", 42), 
                Map.of("X-Tenant-ID", "acme", "X-Request-ID", "req-tenant-42")); 

        assertThat(response.statusCode()).isEqualTo(200); 
        assertThat(response.headers().firstValue("X-Request-ID")).hasValue("req-tenant-42");
        assertThat(response.headers().firstValue("X-Correlation-ID")).hasValue("req-tenant-42");
        assertThat(response.headers().firstValue("traceparent")).hasValueSatisfying(value ->
                assertThat(value).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]"));
        verify(mockClient).save(eq("acme"), eq("orders"), any());
        verify(mockClient).appendEvent(eq("acme"), any());
    }

    @Test
    @DisplayName("preserves inbound trace id and request id on response headers")
    void preservesInboundTraceAndRequestIdOnResponses() throws Exception { 
        DataCloudClient.Entity saved = DataCloudClient.Entity.of( 
                "ent-901",
                "orders",
                Map.of("status", "processing")); 
        when(mockClient.save(eq("acme"), eq("orders"), any())).thenReturn(Promise.of(saved));
        when(mockClient.appendEvent(eq("acme"), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(5)));

        startServer(); 

        String traceId = "0123456789abcdef0123456789abcdef";
        HttpResponse<String> response = postJson( 
                "/api/v1/entities/orders",
                Map.of("status", "processing"), 
                Map.of( 
                        "X-Tenant-ID", "acme",
                        "X-Request-ID", "req-trace-900",
                        "traceparent", "00-" + traceId + "-1111222233334444-01"));

        assertThat(response.statusCode()).isEqualTo(200); 
        assertThat(response.headers().firstValue("X-Request-ID")).hasValue("req-trace-900");
        assertThat(response.headers().firstValue("traceparent")).hasValueSatisfying(value ->
                assertThat(value).startsWith("00-" + traceId + "-")); 
        assertThat(response.headers().firstValue("X-Parent-Span-Id")).hasValue("1111222233334444");
    }

    @Test
    @DisplayName("exports Prometheus metrics for entity, event, and governance operations")
    void exportsPrometheusMetricsForBusinessOperations() throws Exception { 
        DataCloudClient.Entity saved = DataCloudClient.Entity.of( 
                "ent-123",
                "products",
                Map.of("name", "Widget", "price", 9.99)); 
        when(mockClient.save(any(), eq("products"), any())).thenReturn(Promise.of(saved));
        when(mockClient.appendEvent(any(), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(7))); 

        startServer(); 

        HttpResponse<String> entityResponse = postJson( 
                "/api/v1/entities/products",
                Map.of("name", "Widget", "price", 9.99), 
                Map.of("X-Tenant-ID", "tenant-observe")); 
        assertThat(entityResponse.statusCode()).isEqualTo(200); 

        HttpResponse<String> eventResponse = postJson( 
                "/api/v1/events",
                Map.of("type", "order.placed", "payload", Map.of("orderId", "ORD-1")), 
                Map.of("X-Tenant-ID", "tenant-observe")); 
        assertThat(eventResponse.statusCode()).isEqualTo(200); 

        HttpResponse<String> governanceResponse = postJson( 
                "/api/v1/governance/retention/classify",
                Map.of("collection", "products", "tier", "standard", "reason", "retention-policy"), 
                Map.of("X-Tenant-ID", "tenant-observe")); 
        assertThat(governanceResponse.statusCode()).isEqualTo(200); 

        HttpResponse<String> metricsResponse = get("/metrics");

        assertThat(metricsResponse.statusCode()).isEqualTo(200); 
        assertThat(metricsResponse.headers().firstValue("content-type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("text/plain"));
        assertThat(metricsResponse.body()).contains(DataCloudBusinessMetrics.METRIC_ENTITY_TOTAL); 
        assertThat(metricsResponse.body()).contains(DataCloudBusinessMetrics.METRIC_EVENT_APPEND_TOTAL); 
        assertThat(metricsResponse.body()).contains(DataCloudBusinessMetrics.METRIC_GOVERNANCE_TOTAL); 
        assertThat(metricsResponse.body()).contains("tenant-observe");
    }

    @Test
    @DisplayName("records error-status business metrics for invalid entity request payloads")
    void recordsErrorStatusBusinessMetricsForInvalidPayload() throws Exception {
        startServer();

        HttpResponse<String> badRequestResponse = postJson(
                "/api/v1/entities/products",
                Map.of(),
                Map.of("X-Tenant-ID", "tenant-observe"));
        assertThat(badRequestResponse.statusCode()).isEqualTo(400);

        HttpResponse<String> metricsResponse = get("/metrics");

        assertThat(metricsResponse.statusCode()).isEqualTo(200);
        assertThat(metricsResponse.body()).contains(DataCloudBusinessMetrics.METRIC_ENTITY_TOTAL);
        assertThat(metricsResponse.body()).contains("status=\"error\"");
        assertThat(metricsResponse.body()).contains("tenant=\"tenant-observe\"");
    }

    @Test
    @DisplayName("records AI fallback rate metric when analytics suggest uses heuristic mode (P1-05)") 
    void recordsAiFallbackRateMetricForAnalyticsSuggestHeuristicMode() throws Exception { 
        // When completionService is null, AiAssistHandler falls back to static heuristics
        // and records dc.ai.recommendation.requests with fallback="true".
        // This verifies the AI fallback telemetry pipeline is instrumented end-to-end.
        startServer(); 

        HttpResponse<String> analyticsResponse = postJson( 
                "/api/v1/analytics/suggest",
                Map.of("intent", "summarize sales by region"),
                Map.of("X-Tenant-ID", "tenant-ai-obs")); 
        // Heuristic mode always returns 200
        assertThat(analyticsResponse.statusCode()).isEqualTo(200); 

        HttpResponse<String> metricsResponse = get("/metrics"); 
        assertThat(metricsResponse.statusCode()).isEqualTo(200); 
        // dc.ai.recommendation.requests emitted with fallback="true" tag
        String metricsBody = metricsResponse.body(); 
        assertThat(metricsBody)
                .as("Prometheus scrape should contain dc_ai_recommendation_requests counter. Full body:\n%s", metricsBody)
                .contains("dc_ai_recommendation_requests"); 
        assertThat(metricsBody)
                .as("dc_ai_recommendation_requests should have fallback=true tag. Full body:\n%s", metricsBody)
                .contains("fallback=\"true\""); 
        assertThat(metricsBody)
                .as("dc_ai_recommendation_requests should have type=analytics_suggest tag. Full body:\n%s", metricsBody)
                .contains("type=\"analytics_suggest\""); 
    }

        @Test
        @DisplayName("exports request to handler to store parent-child spans for entity save")
        void exportsParentChildTraceSpansForEntitySave() throws Exception { 
                DataCloudClient.Entity saved = DataCloudClient.Entity.of( 
                                "ent-trace-1",
                                "orders",
                                Map.of("status", "pending", "amount", 42)); 
                when(mockClient.save(eq("acme"), eq("orders"), any())).thenReturn(Promise.of(saved));
                when(mockClient.appendEvent(eq("acme"), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(8)));

                startServer(); 

                HttpResponse<String> response = postJson( 
                                "/api/v1/entities/orders",
                                Map.of("status", "pending", "amount", 42), 
                                Map.of("X-Tenant-ID", "acme", "X-Request-ID", "req-trace-101")); 

                assertThat(response.statusCode()).isEqualTo(200); 

                List<Span> spans = traceExporter.allSpans(); 
                Span requestSpan = findSpan(spans, "post /api/v1/entities/orders"); 
                Span handlerSpan = findSpan(spans, "datacloud.http.entity.save"); 
                Span storeSpan = findSpan(spans, "datacloud.entity.store.save"); 
                Span eventSpan = findSpan(spans, "datacloud.event.store.append"); 

                assertThat(requestSpan.getAttributes()).containsEntry("request.id", "req-trace-101"); 
                assertThat(handlerSpan.getParentSpanId()).isEqualTo(requestSpan.getSpanId()); 
                assertThat(storeSpan.getParentSpanId()).isEqualTo(handlerSpan.getSpanId()); 
                assertThat(eventSpan.getParentSpanId()).isEqualTo(handlerSpan.getSpanId()); 
                assertThat(storeSpan.getAttributes()).containsEntry("collection", "orders"); 
        }

        private static Span findSpan(List<Span> spans, String operationName) { 
                return spans.stream() 
                                .filter(span -> operationName.equals(span.getOperationName())) 
                                .findFirst() 
                                .orElseThrow(() -> new AssertionError("Missing span: " + operationName)); 
        }

        private static final class CapturingTraceExporter implements TraceExporter {
                private final List<Span> spans = new ArrayList<>(); 

                @Override
                public Promise<ExportResult> exportSpans(List<Span> spans) { 
                        this.spans.addAll(spans); 
                        return Promise.of(new ExportResult(true, spans.size(), 0, List.of(), 0L)); 
                }

                @Override
                public ExportConfig getConfig() { 
                        return new ExportConfig("memory://trace-test", 100, 1000L, false, 1, 100L); 
                }

                @Override
                public Promise<Boolean> isHealthy() { 
                        return Promise.of(true); 
                }

                private List<Span> allSpans() { 
                        return List.copyOf(spans); 
                }
        }
}