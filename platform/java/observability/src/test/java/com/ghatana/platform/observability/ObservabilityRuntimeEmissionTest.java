package com.ghatana.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runtime emission proof tests for the observability platform module.
 * <p>
 * These tests prove that the platform observability components <em>actually emit</em>
 * signals at runtime — counters increment, timers record, spans are exported,
 * and MDC context is populated — rather than silently no-op-ing.
 *
 * @doc.type class
 * @doc.purpose Prove observability runtime emission: metrics, traces, MDC/correlation, SLO
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Observability Runtime Emission Proof Tests")
class ObservabilityRuntimeEmissionTest {

    // ── Metrics emission ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Metrics — runtime emission proof")
    class MetricsEmissionTests {

        private SimpleMeterRegistry registry;
        private Metrics metrics;

        @BeforeEach
        void setUp() {
            registry = new SimpleMeterRegistry();
            metrics = new Metrics(registry);
        }

        @Test
        @DisplayName("Counter increments are recorded and queryable from the registry")
        void counterIncrementIsEmitted() {
            Counter counter = metrics.counter("test.requests.total");
            counter.increment();
            counter.increment();
            counter.increment();

            assertThat(registry.counter("test.requests.total").count())
                .as("Counter must reflect 3 increments")
                .isEqualTo(3.0);
        }

        @Test
        @DisplayName("Timer records duration and count is queryable from the registry")
        void timerRecordsAndCountIsEmitted() {
            Timer timer = metrics.timer("test.operation.latency");

            timer.record(Duration.ofMillis(10));
            timer.record(Duration.ofMillis(20));

            Timer registered = registry.timer("test.operation.latency");
            assertThat(registered.count())
                .as("Timer must record 2 observations")
                .isEqualTo(2);
            assertThat(registered.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .as("Timer total must be at least 30ms")
                .isGreaterThanOrEqualTo(30.0);
        }

        @Test
        @DisplayName("Independent counters in the same registry do not interfere")
        void independentCountersDontInterfere() {
            Counter counterA = metrics.counter("tenant.a.requests");
            Counter counterB = metrics.counter("tenant.b.requests");

            counterA.increment();
            counterA.increment();
            counterB.increment();

            assertThat(registry.counter("tenant.a.requests").count()).isEqualTo(2.0);
            assertThat(registry.counter("tenant.b.requests").count()).isEqualTo(1.0);
        }
    }

    // ── Trace span emission ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Traces — runtime span export proof")
    class TraceEmissionTests {

        private InMemorySpanExporter spanExporter;
        private SdkTracerProvider tracerProvider;
        private Tracer tracer;

        @BeforeEach
        void setUp() {
            spanExporter = new InMemorySpanExporter();
            tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
            tracer = tracerProvider.get("test-instrumentation");
        }

        @AfterEach
        void tearDown() {
            tracerProvider.close();
        }

        @Test
        @DisplayName("A completed span is exported to InMemorySpanExporter")
        void completedSpanIsExported() {
            Span span = tracer.spanBuilder("process-request").startSpan();
            span.end();

            assertThat(spanExporter.getFinishedSpans())
                .as("Exactly one span must be exported after end()")
                .hasSize(1);
            assertThat(spanExporter.getFinishedSpans().get(0).getName())
                .isEqualTo("process-request");
        }

        @Test
        @DisplayName("Child spans are exported with correct parent relationship")
        void childSpansAreExported() {
            Span parent = tracer.spanBuilder("parent-op").startSpan();
            Span child = tracer.spanBuilder("child-op").setParent(
                io.opentelemetry.context.Context.current().with(parent)).startSpan();
            child.end();
            parent.end();

            assertThat(spanExporter.getFinishedSpans()).hasSize(2);
            var childData = spanExporter.getFinishedSpans().stream()
                .filter(s -> s.getName().equals("child-op"))
                .findFirst();
            assertThat(childData).isPresent();
            assertThat(childData.get().getParentSpanId())
                .isEqualTo(parent.getSpanContext().getSpanId());
        }

        @Test
        @DisplayName("Spans started but not ended are not exported")
        void unendedSpanIsNotExported() {
            tracer.spanBuilder("orphan-span").startSpan();
            // Deliberately not calling span.end()

            assertThat(spanExporter.getFinishedSpans())
                .as("An unended span must not appear in the exporter")
                .isEmpty();
        }

        @Test
        @DisplayName("exporter.clear() resets span count between test scenarios")
        void clearResetsExporter() {
            Span span = tracer.spanBuilder("op-1").startSpan();
            span.end();
            assertThat(spanExporter.size()).isEqualTo(1);

            spanExporter.clear();
            assertThat(spanExporter.isEmpty()).isTrue();
        }
    }

    // ── MDC / correlation context emission ────────────────────────────────────

    @Nested
    @DisplayName("MDC / Correlation context — runtime propagation proof")
    class CorrelationEmissionTests {

        @AfterEach
        void tearDown() {
            CorrelationContext.clear();
        }

        @Test
        @DisplayName("initialize() populates MDC with correlationId and requestId")
        void initializePopulatesMdc() {
            CorrelationContext.initialize();

            assertThat(MDC.get(CorrelationContext.CORRELATION_ID_KEY))
                .as("correlationId must be in MDC after initialize()")
                .isNotNull()
                .startsWith("corr-");
            assertThat(MDC.get(CorrelationContext.REQUEST_ID_KEY))
                .as("requestId must be in MDC after initialize()")
                .isNotNull()
                .startsWith("req-");
        }

        @Test
        @DisplayName("initialize(correlationId, userId, tenantId, requestId) sets all MDC keys")
        void initializeWithExplicitValuesPopulatesAllMdcKeys() {
            CorrelationContext.initialize("corr-abc123", "user-42", "tenant-x", "req-xyz789");

            assertThat(MDC.get(CorrelationContext.CORRELATION_ID_KEY)).isEqualTo("corr-abc123");
            assertThat(MDC.get(CorrelationContext.USER_ID_KEY)).isEqualTo("user-42");
            assertThat(MDC.get(CorrelationContext.TENANT_ID_KEY)).isEqualTo("tenant-x");
            assertThat(MDC.get(CorrelationContext.REQUEST_ID_KEY)).isEqualTo("req-xyz789");
        }

        @Test
        @DisplayName("clear() removes all correlation keys from MDC")
        void clearRemovesAllMdcKeys() {
            CorrelationContext.initialize("corr-test", "user-1", "tenant-1", "req-1");
            CorrelationContext.clear();

            assertThat(MDC.get(CorrelationContext.CORRELATION_ID_KEY)).isNull();
            assertThat(MDC.get(CorrelationContext.USER_ID_KEY)).isNull();
            assertThat(MDC.get(CorrelationContext.TENANT_ID_KEY)).isNull();
            assertThat(MDC.get(CorrelationContext.REQUEST_ID_KEY)).isNull();
        }

        @Test
        @DisplayName("withContext() propagates correlation data to another thread")
        void withContextPropagatesAcrossThread() throws Exception {
            CorrelationContext.initialize("corr-propagated", "user-99", "tenant-99", "req-99");
            CorrelationContext.CorrelationData captured = CorrelationContext.getCurrentData();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                    CorrelationContext.withContext(captured, CorrelationContext::getCorrelationId),
                    executor
                );

                assertThat(future.get())
                    .as("Correlation ID must propagate to async thread via withContext()")
                    .isEqualTo("corr-propagated");
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("Different threads have isolated correlation contexts")
        void threadIsolationIsEnforced() throws Exception {
            CorrelationContext.initialize("corr-main", "user-main", "tenant-main", "req-main");

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                CompletableFuture<String> future = CompletableFuture.supplyAsync(
                    CorrelationContext::getCorrelationId, executor);

                assertThat(future.get())
                    .as("Worker thread must not inherit main thread's correlation context")
                    .isNull();
            } finally {
                executor.shutdown();
            }
        }
    }
}
