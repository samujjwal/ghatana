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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TelemetryRedactionFilter}.
 *
 * <p>Verifies TX-3: prompts, memory fragments, and tool payloads are redacted
 * by default, while non-sensitive attributes pass through unchanged.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TelemetryRedactionFilter")
class TelemetryRedactionFilterTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Mock
    private SpanExporter delegate;

    // ────────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("wrap() — default sensitive keys")
    class DefaultSensitiveKeys {

        @Test
        @DisplayName("redacts prompt attribute")
        void redactsPromptAttribute() {
            List<SpanData> received = captureExportedSpans(
                    TelemetryRedactionFilter.wrap(delegate),
                    Attributes.of(AttributeKey.stringKey("ghatana.agent.prompt"), "secret prompt text"));

            assertRedacted(received, "ghatana.agent.prompt");
        }

        @Test
        @DisplayName("redacts tool.input attribute")
        void redactsToolInput() {
            List<SpanData> received = captureExportedSpans(
                    TelemetryRedactionFilter.wrap(delegate),
                    Attributes.of(AttributeKey.stringKey("ghatana.agent.tool.input"), "sensitive input"));

            assertRedacted(received, "ghatana.agent.tool.input");
        }

        @Test
        @DisplayName("redacts tool.output attribute")
        void redactsToolOutput() {
            List<SpanData> received = captureExportedSpans(
                    TelemetryRedactionFilter.wrap(delegate),
                    Attributes.of(AttributeKey.stringKey("ghatana.agent.tool.output"), "sensitive output"));

            assertRedacted(received, "ghatana.agent.tool.output");
        }

        @Test
        @DisplayName("redacts memory.content attribute")
        void redactsMemoryContent() {
            List<SpanData> received = captureExportedSpans(
                    TelemetryRedactionFilter.wrap(delegate),
                    Attributes.of(AttributeKey.stringKey("ghatana.memory.content"), "private memory"));

            assertRedacted(received, "ghatana.memory.content");
        }

        @Test
        @DisplayName("redacts embedding attribute")
        void redactsEmbedding() {
            List<SpanData> received = captureExportedSpans(
                    TelemetryRedactionFilter.wrap(delegate),
                    Attributes.of(AttributeKey.stringKey("ghatana.embedding.vector"), "float,values"));

            assertRedacted(received, "ghatana.embedding.vector");
        }

        @Test
        @DisplayName("passes through non-sensitive attributes unchanged")
        void passesThroughNonSensitiveAttributes() {
            List<SpanData> received = captureExportedSpans(
                    TelemetryRedactionFilter.wrap(delegate),
                    Attributes.of(
                            AttributeKey.stringKey(AgentTelemetryContract.ATTR_AGENT_ID), "my-agent",
                            AttributeKey.stringKey(AgentTelemetryContract.ATTR_TENANT_ID), "tenant-1"));

            assertThat(received).hasSize(1);
            Attributes attrs = received.get(0).getAttributes();
            assertThat(attrs.get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_AGENT_ID)))
                    .isEqualTo("my-agent");
            assertThat(attrs.get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_TENANT_ID)))
                    .isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("mixed span: sensitive attrs redacted, safe attrs preserved")
        void mixedSpanRedactsSensitiveOnly() {
            List<SpanData> received = captureExportedSpans(
                    TelemetryRedactionFilter.wrap(delegate),
                    Attributes.of(
                            AttributeKey.stringKey("ghatana.agent.id"), "agent-1",
                            AttributeKey.stringKey("ghatana.agent.prompt"), "do not leak me"));

            assertThat(received).hasSize(1);
            Attributes attrs = received.get(0).getAttributes();
            assertThat(attrs.get(AttributeKey.stringKey("ghatana.agent.id"))).isEqualTo("agent-1");
            assertThat(attrs.get(AttributeKey.stringKey("ghatana.agent.prompt")))
                    .isEqualTo(TelemetryRedactionFilter.REDACTED_MARKER);
        }
    }

    @Nested
    @DisplayName("builder() — custom key patterns")
    class CustomKeyPatterns {

        @Test
        @DisplayName("custom pattern redacts matching attributes")
        void customPatternRedactsMatchingAttributes() {
            TelemetryRedactionFilter filter = TelemetryRedactionFilter.builder(delegate)
                    .addSensitiveKey("ssn")
                    .build();

            List<SpanData> received = captureExportedSpans(filter,
                    Attributes.of(AttributeKey.stringKey("user.ssn"), "123-45-6789"));

            assertRedacted(received, "user.ssn");
        }

        @Test
        @DisplayName("builder without withDefaults() does not redact default patterns")
        void builderWithoutDefaultsDoesNotRedactDefaults() {
            TelemetryRedactionFilter filter = TelemetryRedactionFilter.builder(delegate)
                    .addSensitiveKey("custom.key")
                    .build();

            List<SpanData> received = captureExportedSpans(filter,
                    Attributes.of(AttributeKey.stringKey("ghatana.agent.prompt"), "should pass through"));

            // prompt is NOT redacted because defaults weren't included
            assertThat(received).hasSize(1);
            assertThat(received.get(0).getAttributes()
                    .get(AttributeKey.stringKey("ghatana.agent.prompt")))
                    .isEqualTo("should pass through");
        }

        @Test
        @DisplayName("withDefaults() reinstates all default sensitive patterns")
        void withDefaultsReinstatesDefaultPatterns() {
            TelemetryRedactionFilter filter = TelemetryRedactionFilter.builder(delegate)
                    .withDefaults()
                    .build();

            List<SpanData> received = captureExportedSpans(filter,
                    Attributes.of(AttributeKey.stringKey("ghatana.agent.prompt"), "sensitive"));

            assertRedacted(received, "ghatana.agent.prompt");
        }
    }

    @Nested
    @DisplayName("lifecycle delegation")
    class Lifecycle {

        @Test
        @DisplayName("flush delegates to downstream")
        void flushDelegates() {
            when(delegate.flush()).thenReturn(io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess());
            TelemetryRedactionFilter filter = TelemetryRedactionFilter.wrap(delegate);
            filter.flush();
            verify(delegate).flush();
        }

        @Test
        @DisplayName("shutdown delegates to downstream")
        void shutdownDelegates() {
            when(delegate.shutdown()).thenReturn(io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess());
            TelemetryRedactionFilter filter = TelemetryRedactionFilter.wrap(delegate);
            filter.shutdown();
            verify(delegate).shutdown();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Instruments a span with the given attributes, exports it through {@code filter},
     * and returns the list of {@link SpanData} received by the delegate.
     */
    @SuppressWarnings("unchecked")
    private List<SpanData> captureExportedSpans(SpanExporter filter, Attributes spanAttributes) {
        List<SpanData> received = new ArrayList<>();
        when(delegate.export(any())).thenAnswer(inv -> {
            received.addAll((java.util.Collection<SpanData>) inv.getArgument(0));
            return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
        });

        // Build a minimal SpanData using TestSpanData from the OTel testing library
        io.opentelemetry.sdk.testing.trace.TestSpanData spanData =
                io.opentelemetry.sdk.testing.trace.TestSpanData.builder()
                        .setName("test-span")
                        .setStartEpochNanos(0L)
                        .setEndEpochNanos(1000L)
                        .setHasEnded(true)
                        .setKind(io.opentelemetry.api.trace.SpanKind.INTERNAL)
                        .setStatus(io.opentelemetry.sdk.trace.data.StatusData.ok())
                        .setAttributes(spanAttributes)
                        .build();

        filter.export(List.of(spanData));
        return received;
    }

    private static void assertRedacted(List<SpanData> spans, String attributeKey) {
        assertThat(spans).hasSize(1);
        String value = spans.get(0).getAttributes().get(AttributeKey.stringKey(attributeKey));
        assertThat(value)
                .as("Attribute '%s' should be redacted", attributeKey)
                .isEqualTo(TelemetryRedactionFilter.REDACTED_MARKER);
    }
}
