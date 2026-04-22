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
@DisplayName("AgentRunTracer [GH-90000]")
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
        Tracer otelTracer = tracerProvider.get("test-agent-tracer [GH-90000]");
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
    @DisplayName("startRun [GH-90000]")
    class StartRun {

        @Test
        @DisplayName("creates root span with required attributes [GH-90000]")
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
                    .isEqualTo("my-agent [GH-90000]");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_AGENT_RELEASE_ID))) // GH-90000
                    .isEqualTo("rel-001 [GH-90000]");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_TENANT_ID))) // GH-90000
                    .isEqualTo("tenant-a [GH-90000]");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_CORRELATION_ID))) // GH-90000
                    .isEqualTo("corr-xyz [GH-90000]");
            assertThat(span.getAttributes().get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_TELEMETRY_VERSION))) // GH-90000
                    .isEqualTo(AgentTelemetryContract.VERSION); // GH-90000
        }

        @Test
        @DisplayName("null correlationId does not set correlation attribute [GH-90000]")
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
    @DisplayName("traceContextRetrieval [GH-90000]")
    class TraceContextRetrieval {

        @Test
        @DisplayName("creates child span with item count attribute [GH-90000]")
        void createsChildSpanWithItemCount() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) { // GH-90000
                tracer.traceContextRetrieval(run, 7, Duration.ofMillis(15)); // GH-90000
            }

            List<SpanData> spans = getSpans(); // GH-90000
            SpanData contextSpan = spans.stream() // GH-90000
                    .filter(s -> s.getName().equals(AgentTelemetryContract.SPAN_CONTEXT_RETRIEVAL)) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(() -> new AssertionError("No context retrieval span found [GH-90000]"));
            assertThat(contextSpan.getAttributes() // GH-90000
                    .get(AttributeKey.longKey(AgentTelemetryContract.ATTR_CONTEXT_ITEM_COUNT))) // GH-90000
                    .isEqualTo(7L); // GH-90000
        }
    }

    @Nested
    @DisplayName("tracePolicyEval [GH-90000]")
    class TracePolicyEval {

        @Test
        @DisplayName("creates child span with policy decision attribute [GH-90000]")
        void createsChildSpanWithDecision() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) { // GH-90000
                tracer.tracePolicyEval(run, "pack-123", "ALLOW"); // GH-90000
            }

            SpanData policySpan = getSpans().stream() // GH-90000
                    .filter(s -> s.getName().equals(AgentTelemetryContract.SPAN_POLICY_EVAL)) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(() -> new AssertionError("No policy eval span found [GH-90000]"));
            assertThat(policySpan.getAttributes() // GH-90000
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_POLICY_PACK_ID))) // GH-90000
                    .isEqualTo("pack-123 [GH-90000]");
            assertThat(policySpan.getAttributes() // GH-90000
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_POLICY_DECISION))) // GH-90000
                    .isEqualTo("ALLOW [GH-90000]");
        }
    }

    @Nested
    @DisplayName("traceToolExecution [GH-90000]")
    class TraceToolExecution {

        @Test
        @DisplayName("creates child span with tool attributes [GH-90000]")
        void createsChildSpanWithToolAttributes() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) { // GH-90000
                tracer.traceToolExecution(run, "search-tool", "READ", "ALLOW"); // GH-90000
            }

            SpanData toolSpan = getSpans().stream() // GH-90000
                    .filter(s -> s.getName().equals(AgentTelemetryContract.SPAN_TOOL_EXECUTE)) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(() -> new AssertionError("No tool execution span found [GH-90000]"));
            assertThat(toolSpan.getAttributes() // GH-90000
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_TOOL_ID))) // GH-90000
                    .isEqualTo("search-tool [GH-90000]");
            assertThat(toolSpan.getAttributes() // GH-90000
                    .get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_ACTION_CLASS))) // GH-90000
                    .isEqualTo("READ [GH-90000]");
        }
    }

    @Nested
    @DisplayName("exception recording [GH-90000]")
    class ExceptionRecording {

        @Test
        @DisplayName("recordException sets ERROR status [GH-90000]")
        void recordExceptionSetsErrorStatus() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) { // GH-90000
                run.recordException(new RuntimeException("boom [GH-90000]"));
            }

            SpanData span = getSpans().get(0); // GH-90000
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR); // GH-90000
        }

        @Test
        @DisplayName("recordException records exception event on span [GH-90000]")
        void recordExceptionCreatesEvent() { // GH-90000
            try (AgentRunTracer.AgentRunSpan run = tracer.startRun("ag", "r1", "t1", null)) { // GH-90000
                run.recordException(new IllegalStateException("bad state [GH-90000]"));
            }

            SpanData span = getSpans().get(0); // GH-90000
            assertThat(span.getEvents()) // GH-90000
                    .anyMatch(e -> e.getName().equals("exception [GH-90000]"));
        }
    }

    @Nested
    @DisplayName("all 11 lifecycle phases [GH-90000]")
    class AllPhases {

        @Test
        @DisplayName("each phase creates a named child span [GH-90000]")
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
