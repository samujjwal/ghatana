package com.ghatana.appplatform.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TraceContextPropagator}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for W3C TraceContext propagation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TraceContextPropagator — Unit Tests")
class TraceContextPropagatorTest {

    @AfterEach
    void clearContext() {
        StructuredLogContext.clear();
    }

    @Test
    @DisplayName("startTrace — returns valid W3C traceparent and seeds context")
    void startTraceReturnsValidTraceparent() {
        String traceparent = TraceContextPropagator.startTrace();

        // W3C format: 00-<32-char traceId>-<16-char spanId>-01
        assertThat(traceparent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(TraceContextPropagator.currentTraceId()).hasSize(32);
        assertThat(TraceContextPropagator.currentSpanId()).hasSize(16);
    }

    @Test
    @DisplayName("startTrace — each call generates unique trace IDs")
    void startTraceGeneratesUniqueIds() {
        String tp1 = TraceContextPropagator.startTrace();
        String traceId1 = TraceContextPropagator.currentTraceId();
        StructuredLogContext.clear();

        String tp2 = TraceContextPropagator.startTrace();
        String traceId2 = TraceContextPropagator.currentTraceId();

        assertThat(traceId1).isNotEqualTo(traceId2);
        assertThat(tp1).isNotEqualTo(tp2);
    }

    @Test
    @DisplayName("continueTrace — preserves traceId, generates new spanId")
    void continueTracePreservesTraceId() {
        String incoming = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        String outgoing = TraceContextPropagator.continueTrace(incoming);

        assertThat(outgoing).startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
        // spanId must differ from parent
        String[] parts = outgoing.split("-");
        assertThat(parts[2]).isNotEqualTo("00f067aa0ba902b7");
        assertThat(parts[3]).isEqualTo("01");
        assertThat(TraceContextPropagator.currentTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    @DisplayName("continueTrace_null — falls back to startTrace")
    void continueTraceNullFallsBack() {
        String traceparent = TraceContextPropagator.continueTrace(null);
        assertThat(traceparent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    @Test
    @DisplayName("continueTrace_malformed — falls back to startTrace")
    void continueTraceMalformedFallsBack() {
        String traceparent = TraceContextPropagator.continueTrace("not-a-traceparent");
        assertThat(traceparent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    @Test
    @DisplayName("currentTraceId — returns null before any trace started")
    void currentTraceIdNullWhenNoTrace() {
        assertThat(TraceContextPropagator.currentTraceId()).isNull();
    }
}
