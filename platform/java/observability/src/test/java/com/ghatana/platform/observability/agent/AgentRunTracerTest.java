/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.platform.observability.agent;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AgentRunTracer}.
 *
 * <p>Verifies span hierarchy, attribute keys from {@link AgentTelemetryContract},
 * error handling, and all 11 lifecycle phases.
 */
@DisplayName("AgentRunTracer")
class AgentRunTracerTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private AgentRunTracer tracer;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        Tracer otelTracer = tracerProvider.get("test-agent-tracer");
        tracer = new AgentRunTracer(otelTracer);
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    private List<SpanData> getSpans() {
        return spanExporter.getFinishedSpanItems();
    }

    @Nested
    @DisplayName("startRun")
    class StartRun {

        @Test
        @DisplayName("creates root span with required attributes")
        void createsRootSpanWithRequiredAttributes() {
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun(
                    "my-agent", "rel-001", "tenant-a", "corr-xyz")) {
                run.setStatus(StatusCode.OK, "done");
            }

            List<SpanData> spans = getSpans();
            assertThat(spans).hasSize(1);
            SpanData span = spans.get(0);
            assertThat(span.getName()).isEqualTo(AgentTelemetryContract.SPAN_RUN_START);
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_AGENT_ID)))
                    .isEqualTo("my-agent");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_AGENT_RELEASE_ID)))
                    .isEqualTo("rel-001");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_TENANT_ID)))
                    .isEqualTo("tenant-a");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_CORRELATION_ID)))
                    .isEqualTo("corr-xyz");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_TELEMETRY_VERSION)))
                    .isEqualTo(AgentTelemetryContract.VERSION);
        }

        @Test
        @DisplayName("null correlationId does not set correlation attribute")
        void nullCorrelationIdNotSet() {
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) {
                // close span
            }

            SpanData span = getSpans().get(0);
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_CORRELATION_ID)))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("traceContextRetrieval")
    class TraceContextRetrieval {

        @Test
        @DisplayName("creates child span with item count attribute")
        void createsChildSpanWithItemCount() {
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) {
                tracer.traceContextRetrieval(run, 7, Duration.ofMillis(15));
            }

            List<SpanData> spans = getSpans();
            SpanData contextSpan = spans.stream()
                    .filter(s -> s.getName().equals(AgentTelemetryContract.SPAN_CONTEXT_RETRIEVAL))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No context retrieval span found"));
            assertThat(contextSpan.getAttributes()
                    .get(AttributeKey.longKey(AgentTelemetryContract.ATTR_CONTEXT_ITEM_COUNT)))
                    .isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("tracePolicyEval")
    class TracePolicyEval {

        @Test
        @DisplayName("creates child span with policy decision attribute")
        void createsChildSpanWithDecision() {
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) {
                tracer.tracePolicyEval(run, "pack-123", "ALLOW");
            }

            SpanData policySpan = getSpans().stream()
                    .filter(s -> s.getName().equals(AgentTelemetryContract.SPAN_POLICY_EVAL))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No policy eval span found"));
            assertThat(policySpan.getAttributes()
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_POLICY_PACK_ID)))
                    .isEqualTo("pack-123");
            assertThat(policySpan.getAttributes()
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_POLICY_DECISION)))
                    .isEqualTo("ALLOW");
        }
    }

    @Nested
    @DisplayName("traceToolExecution")
    class TraceToolExecution {

        @Test
        @DisplayName("creates child span with tool attributes")
        void createsChildSpanWithToolAttributes() {
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) {
                tracer.traceToolExecution(run, "search-tool", "READ", "ALLOW");
            }

            SpanData toolSpan = getSpans().stream()
                    .filter(s -> s.getName().equals(AgentTelemetryContract.SPAN_TOOL_EXECUTE))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No tool execution span found"));
            assertThat(toolSpan.getAttributes()
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_TOOL_ID)))
                    .isEqualTo("search-tool");
            assertThat(toolSpan.getAttributes()
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_ACTION_CLASS)))
                    .isEqualTo("READ");
        }
    }

    @Nested
    @DisplayName("exception recording")
    class ExceptionRecording {

        @Test
        @DisplayName("recordException sets ERROR status")
        void recordExceptionSetsErrorStatus() {
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) {
                run.recordException(new RuntimeException("boom"));
            }

            SpanData span = getSpans().get(0);
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        }

        @Test
        @DisplayName("recordException records exception event on span")
        void recordExceptionCreatesEvent() {
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) {
                run.recordException(new IllegalStateException("bad state"));
            }

            SpanData span = getSpans().get(0);
            assertThat(span.getEvents())
                    .anyMatch(e -> e.getName().equals("exception"));
        }
    }

    @Nested
    @DisplayName("all 11 lifecycle phases")
    class AllPhases {

        @Test
        @DisplayName("each phase creates a named child span")
        void allPhasesCreateSpans() {
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", "c1")) {
                tracer.traceContextRetrieval(run, 3, Duration.ofMillis(10));
                tracer.tracePlannerInvoke(run);
                tracer.tracePolicyEval(run, "pp-1", "ALLOW");
                tracer.traceToolExecution(run, "tool-1", "READ", "ALLOW");
                tracer.traceSubAgentDelegate(run, "child-agent");
                tracer.traceApprovalRequest(run, "tool-2", "APPROVED");
                tracer.traceMemoryWrite(run, "episodic", "ns-1");
                tracer.traceEvalGate(run, "PASS");
                tracer.traceExternalCommit(run, "WRITE");
            }

            List<SpanData> spans = getSpans();
            // root + 9 child spans = 10 total
            assertThat(spans).hasSizeGreaterThanOrEqualTo(10);

            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_RUN_START));
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_CONTEXT_RETRIEVAL));
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_PLANNER_INVOKE));
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_POLICY_EVAL));
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_TOOL_EXECUTE));
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_SUB_AGENT_DELEGATE));
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_APPROVAL_REQUEST));
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_MEMORY_WRITE));
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_EVAL_GATE));
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_EXTERNAL_COMMIT));
        }
    }
}
