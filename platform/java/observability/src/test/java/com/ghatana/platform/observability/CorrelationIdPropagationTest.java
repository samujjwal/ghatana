package com.ghatana.platform.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-service correlation ID propagation tests.
 * <p>
 * Verifies that:
 * <ul>
 *   <li>Correlation IDs are preserved when propagated via W3C {@code traceparent} headers</li>
 *   <li>Downstream spans continue the upstream trace (same traceId, correct parentSpanId)</li>
 *   <li>Correlation IDs are generated when missing from incoming requests</li>
 *   <li>MDC is populated with the correlation ID for structured log emission</li>
 *   <li>Context propagates correctly across thread boundaries</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Correlation ID cross-service boundary propagation contract tests
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Correlation ID Cross-Service Propagation Tests")
class CorrelationIdPropagationTest {

    private static final TextMapSetter<Map<String, String>> MAP_SETTER = Map::put;
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        spanExporter = new InMemorySpanExporter();
        tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
        tracer = tracerProvider.get("test-propagation");
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
        CorrelationContext.clear();
    }

    // ── W3C traceparent inject/extract ────────────────────────────────────────

    @Nested
    @DisplayName("W3C traceparent header propagation")
    class W3cPropagation {

        @Test
        @DisplayName("Injecting context populates traceparent header")
        void injectPopulatesTraceparentHeader() {
            Span upstream = tracer.spanBuilder("upstream-service").startSpan();
            Map<String, String> headers = new HashMap<>();

            try (var ignored = upstream.makeCurrent()) {
                TracingUtils.injectContext(null, Context.current(), headers, MAP_SETTER);
            } finally {
                upstream.end();
            }

            assertThat(headers).containsKey("traceparent");
            assertThat(headers.get("traceparent"))
                .as("traceparent must encode the upstream traceId")
                .contains(upstream.getSpanContext().getTraceId());
        }

        @Test
        @DisplayName("Extracting traceparent header restores the upstream trace context")
        void extractRestoresUpstreamTraceContext() {
            // Service A starts a span and injects context into outbound headers
            Span serviceASpan = tracer.spanBuilder("service-a").startSpan();
            Map<String, String> outboundHeaders = new HashMap<>();
            try (var ignored = serviceASpan.makeCurrent()) {
                TracingUtils.injectContext(null, Context.current(), outboundHeaders, MAP_SETTER);
            } finally {
                serviceASpan.end();
            }

            // Service B extracts the incoming headers and continues the trace
            Context extractedContext = TracingUtils.extractContext(null, outboundHeaders, MAP_GETTER);
            Span serviceBSpan = tracer.spanBuilder("service-b")
                .setParent(extractedContext)
                .startSpan();
            serviceBSpan.end();

            List<SpanData> spans = spanExporter.getFinishedSpans();
            assertThat(spans).hasSize(2);

            SpanData serviceA = spans.stream().filter(s -> s.getName().equals("service-a")).findFirst().orElseThrow();
            SpanData serviceB = spans.stream().filter(s -> s.getName().equals("service-b")).findFirst().orElseThrow();

            assertThat(serviceB.getTraceId())
                .as("Downstream span must carry the same traceId as the upstream span")
                .isEqualTo(serviceA.getTraceId());
            assertThat(serviceB.getParentSpanId())
                .as("Downstream span's parentSpanId must equal the upstream spanId")
                .isEqualTo(serviceA.getSpanId());
        }

        @Test
        @DisplayName("Missing traceparent header results in a new root span (no parent)")
        void missingTraceparentCreatesNewRootSpan() {
            Map<String, String> emptyHeaders = new HashMap<>();
            Context extractedContext = TracingUtils.extractContext(null, emptyHeaders, MAP_GETTER);

            Span span = tracer.spanBuilder("service-b-no-upstream")
                .setParent(extractedContext)
                .startSpan();
            span.end();

            SpanData spanData = spanExporter.getFinishedSpans().get(0);
            assertThat(spanData.getParentSpanId())
                .as("Root span must have no valid parent span ID")
                .isEqualTo(io.opentelemetry.api.trace.SpanId.getInvalid());
        }
    }

    // ── CorrelationContext MDC propagation ────────────────────────────────────

    @Nested
    @DisplayName("CorrelationContext MDC propagation across service boundary simulation")
    class CorrelationMdcPropagation {

        @AfterEach
        void clearMdc() {
            CorrelationContext.clear();
        }

        @Test
        @DisplayName("Correlation ID from inbound request header populates MDC")
        void inboundCorrelationIdPopulatesMdc() {
            String inboundCorrelationId = "corr-inbound-abc123";
            CorrelationContext.initialize(inboundCorrelationId, null, null, null);

            assertThat(MDC.get(CorrelationContext.CORRELATION_ID_KEY))
                .isEqualTo(inboundCorrelationId);
        }

        @Test
        @DisplayName("Generated correlation ID has expected format when not provided")
        void generatedCorrelationIdHasExpectedFormat() {
            CorrelationContext.initialize();

            String correlationId = CorrelationContext.getCorrelationId();
            assertThat(correlationId)
                .as("Auto-generated correlationId must start with 'corr-'")
                .isNotNull()
                .startsWith("corr-");
            assertThat(correlationId.substring("corr-".length()))
                .as("Suffix must be 16 hex characters")
                .matches("[0-9a-f]{16}");
        }

        @Test
        @DisplayName("Correlation ID propagates to a simulated downstream thread via withContext()")
        void correlationIdPropagatesAcrossServiceBoundaryThread() throws Exception {
            CorrelationContext.initialize("corr-cross-svc-001", "user-7", "tenant-3", "req-001");
            CorrelationContext.CorrelationData captured = CorrelationContext.getCurrentData();

            ExecutorService downstream = Executors.newSingleThreadExecutor();
            try {
                CompletableFuture<String> downstreamCorrelationId = CompletableFuture.supplyAsync(
                    () -> CorrelationContext.withContext(captured, CorrelationContext::getCorrelationId),
                    downstream
                );

                assertThat(downstreamCorrelationId.get())
                    .as("Downstream thread must see the upstream correlationId")
                    .isEqualTo("corr-cross-svc-001");
            } finally {
                downstream.shutdownNow();
            }
        }

        @Test
        @DisplayName("Tenant ID is preserved across simulated service hops")
        void tenantIdPreservedAcrossServiceHops() throws Exception {
            CorrelationContext.initialize("corr-tenant-hop", "user-1", "tenant-acme", "req-hop");
            CorrelationContext.CorrelationData hop1Context = CorrelationContext.getCurrentData();

            ExecutorService hop2Executor = Executors.newSingleThreadExecutor();
            try {
                // Hop 2: downstream service picks up context
                CompletableFuture<String> hop2TenantId = CompletableFuture.supplyAsync(
                    () -> CorrelationContext.withContext(hop1Context, CorrelationContext::getTenantId),
                    hop2Executor
                );

                assertThat(hop2TenantId.get())
                    .as("Tenant ID must survive the service boundary hop")
                    .isEqualTo("tenant-acme");
            } finally {
                hop2Executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("withContext() restores original context after the scoped task completes")
        void withContextRestoresOriginalContext() {
            CorrelationContext.initialize("corr-original", "user-orig", "tenant-orig", "req-orig");

            CorrelationContext.CorrelationData foreignContext = new CorrelationContext.CorrelationData(
                "corr-foreign", "user-foreign", "tenant-foreign", "req-foreign", null, null);

            CorrelationContext.withContext(foreignContext, () -> {
                assertThat(CorrelationContext.getCorrelationId()).isEqualTo("corr-foreign");
            });

            // After the scoped task, original context must be restored
            assertThat(CorrelationContext.getCorrelationId()).isEqualTo("corr-original");
            assertThat(CorrelationContext.getTenantId()).isEqualTo("tenant-orig");
        }
    }
}
