/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
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
    void setUp() { // GH-90000
        spanExporter = InMemorySpanExporter.create(); // GH-90000
        tracerProvider = SdkTracerProvider.builder() // GH-90000
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter)) // GH-90000
                .build(); // GH-90000
        Tracer otelTracer = tracerProvider.get("test-agent-tracer");
        tracer = new AgentRunTracer(otelTracer); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        tracerProvider.close(); // GH-90000
    }

    private List<SpanData> getSpans() { // GH-90000
        return spanExporter.getFinishedSpanItems(); // GH-90000
    }

    @Nested
    @DisplayName("startRun")
    class StartRun {

        @Test
        @DisplayName("creates root span with required attributes")
        void createsRootSpanWithRequiredAttributes() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun( // GH-90000
                    "my-agent", "rel-001", "tenant-a", "corr-xyz")) {
                run.setStatus(StatusCode.OK, "done"); // GH-90000
            }

            List<SpanData> spans = getSpans(); // GH-90000
            assertThat(spans).hasSize(1); // GH-90000
            SpanData span = spans.get(0); // GH-90000
            assertThat(span.getName()).isEqualTo(AgentTelemetryContract.SPAN_RUN_START); // GH-90000
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_AGENT_ID))) // GH-90000
                    .isEqualTo("my-agent");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_AGENT_RELEASE_ID))) // GH-90000
                    .isEqualTo("rel-001");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_TENANT_ID))) // GH-90000
                    .isEqualTo("tenant-a");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_CORRELATION_ID))) // GH-90000
                    .isEqualTo("corr-xyz");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_TELEMETRY_VERSION))) // GH-90000
                    .isEqualTo(AgentTelemetryContract.VERSION); // GH-90000
        }

        @Test
        @DisplayName("null correlationId does not set correlation attribute")
        void nullCorrelationIdNotSet() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) { // GH-90000
                // close span
            }

            SpanData span = getSpans().get(0); // GH-90000
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_CORRELATION_ID))) // GH-90000
                    .isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("traceContextRetrieval")
    class TraceContextRetrieval {

        @Test
        @DisplayName("creates child span with item count attribute")
        void createsChildSpanWithItemCount() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) { // GH-90000
                tracer.traceContextRetrieval(run, 7, Duration.ofMillis(15)); // GH-90000
            }

            List<SpanData> spans = getSpans(); // GH-90000
            SpanData contextSpan = spans.stream() // GH-90000
                    .filter(s -> s.getName().equals(AgentTelemetryContract.SPAN_CONTEXT_RETRIEVAL)) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(() -> new AssertionError("No context retrieval span found"));
            assertThat(contextSpan.getAttributes() // GH-90000
                    .get(AttributeKey.longKey(AgentTelemetryContract.ATTR_CONTEXT_ITEM_COUNT))) // GH-90000
                    .isEqualTo(7L); // GH-90000
        }
    }

    @Nested
    @DisplayName("tracePolicyEval")
    class TracePolicyEval {

        @Test
        @DisplayName("creates child span with policy decision attribute")
        void createsChildSpanWithDecision() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) { // GH-90000
                tracer.tracePolicyEval(run, "pack-123", "ALLOW"); // GH-90000
            }

            SpanData policySpan = getSpans().stream() // GH-90000
                    .filter(s -> s.getName().equals(AgentTelemetryContract.SPAN_POLICY_EVAL)) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(() -> new AssertionError("No policy eval span found"));
            assertThat(policySpan.getAttributes() // GH-90000
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_POLICY_PACK_ID))) // GH-90000
                    .isEqualTo("pack-123");
            assertThat(policySpan.getAttributes() // GH-90000
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_POLICY_DECISION))) // GH-90000
                    .isEqualTo("ALLOW");
        }
    }

    @Nested
    @DisplayName("traceToolExecution")
    class TraceToolExecution {

        @Test
        @DisplayName("creates child span with tool attributes")
        void createsChildSpanWithToolAttributes() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) { // GH-90000
                tracer.traceToolExecution(run, "search-tool", "READ", "ALLOW"); // GH-90000
            }

            SpanData toolSpan = getSpans().stream() // GH-90000
                    .filter(s -> s.getName().equals(AgentTelemetryContract.SPAN_TOOL_EXECUTE)) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(() -> new AssertionError("No tool execution span found"));
            assertThat(toolSpan.getAttributes() // GH-90000
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_TOOL_ID))) // GH-90000
                    .isEqualTo("search-tool");
            assertThat(toolSpan.getAttributes() // GH-90000
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_ACTION_CLASS))) // GH-90000
                    .isEqualTo("READ");
        }
    }

    @Nested
    @DisplayName("exception recording")
    class ExceptionRecording {

        @Test
        @DisplayName("recordException sets ERROR status")
        void recordExceptionSetsErrorStatus() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) { // GH-90000
                run.recordException(new RuntimeException("boom"));
            }

            SpanData span = getSpans().get(0); // GH-90000
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR); // GH-90000
        }

        @Test
        @DisplayName("recordException records exception event on span")
        void recordExceptionCreatesEvent() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) { // GH-90000
                run.recordException(new IllegalStateException("bad state"));
            }

            SpanData span = getSpans().get(0); // GH-90000
            assertThat(span.getEvents()) // GH-90000
                    .anyMatch(e -> e.getName().equals("exception"));
        }
    }

    @Nested
    @DisplayName("all 11 lifecycle phases")
    class AllPhases {

        @Test
        @DisplayName("each phase creates a named child span")
        void allPhasesCreateSpans() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", "c1")) { // GH-90000
                tracer.traceContextRetrieval(run, 3, Duration.ofMillis(10)); // GH-90000
                tracer.tracePlannerInvoke(run); // GH-90000
                tracer.tracePolicyEval(run, "pp-1", "ALLOW"); // GH-90000
                tracer.traceToolExecution(run, "tool-1", "READ", "ALLOW"); // GH-90000
                tracer.traceSubAgentDelegate(run, "child-agent"); // GH-90000
                tracer.traceApprovalRequest(run, "tool-2", "APPROVED"); // GH-90000
                tracer.traceMemoryWrite(run, "episodic", "ns-1"); // GH-90000
                tracer.traceEvalGate(run, "PASS"); // GH-90000
                tracer.traceExternalCommit(run, "WRITE"); // GH-90000
            }

            List<SpanData> spans = getSpans(); // GH-90000
            // root + 9 child spans = 10 total
            assertThat(spans).hasSizeGreaterThanOrEqualTo(10); // GH-90000

            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_RUN_START)); // GH-90000
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_CONTEXT_RETRIEVAL)); // GH-90000
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_PLANNER_INVOKE)); // GH-90000
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_POLICY_EVAL)); // GH-90000
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_TOOL_EXECUTE)); // GH-90000
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_SUB_AGENT_DELEGATE)); // GH-90000
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_APPROVAL_REQUEST)); // GH-90000
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_MEMORY_WRITE)); // GH-90000
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_EVAL_GATE)); // GH-90000
            assertThat(spans).anyMatch(s -> s.getName().equals(AgentTelemetryContract.SPAN_EXTERNAL_COMMIT)); // GH-90000
        }
    }
}
