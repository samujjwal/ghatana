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
import io.opentelemetry.api.common.Attributes;
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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("TelemetryRedactionFilter")
class TelemetryRedactionFilterTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create(); // GH-90000

    @Mock
    private SpanExporter delegate;

    // ────────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("wrap() — default sensitive keys")
    class DefaultSensitiveKeys {

        @Test
        @DisplayName("redacts prompt attribute")
        void redactsPromptAttribute() { // GH-90000
            List<SpanData> received = captureExportedSpans( // GH-90000
                    TelemetryRedactionFilter.wrap(delegate), // GH-90000
                    Attributes.of(AttributeKey.stringKey("ghatana.agent.prompt"), "secret prompt text"));

            assertRedacted(received, "ghatana.agent.prompt"); // GH-90000
        }

        @Test
        @DisplayName("redacts tool.input attribute")
        void redactsToolInput() { // GH-90000
            List<SpanData> received = captureExportedSpans( // GH-90000
                    TelemetryRedactionFilter.wrap(delegate), // GH-90000
                    Attributes.of(AttributeKey.stringKey("ghatana.agent.tool.input"), "sensitive input"));

            assertRedacted(received, "ghatana.agent.tool.input"); // GH-90000
        }

        @Test
        @DisplayName("redacts tool.output attribute")
        void redactsToolOutput() { // GH-90000
            List<SpanData> received = captureExportedSpans( // GH-90000
                    TelemetryRedactionFilter.wrap(delegate), // GH-90000
                    Attributes.of(AttributeKey.stringKey("ghatana.agent.tool.output"), "sensitive output"));

            assertRedacted(received, "ghatana.agent.tool.output"); // GH-90000
        }

        @Test
        @DisplayName("redacts memory.content attribute")
        void redactsMemoryContent() { // GH-90000
            List<SpanData> received = captureExportedSpans( // GH-90000
                    TelemetryRedactionFilter.wrap(delegate), // GH-90000
                    Attributes.of(AttributeKey.stringKey("ghatana.memory.content"), "private memory"));

            assertRedacted(received, "ghatana.memory.content"); // GH-90000
        }

        @Test
        @DisplayName("redacts embedding attribute")
        void redactsEmbedding() { // GH-90000
            List<SpanData> received = captureExportedSpans( // GH-90000
                    TelemetryRedactionFilter.wrap(delegate), // GH-90000
                    Attributes.of(AttributeKey.stringKey("ghatana.embedding.vector"), "float,values"));

            assertRedacted(received, "ghatana.embedding.vector"); // GH-90000
        }

        @Test
        @DisplayName("passes through non-sensitive attributes unchanged")
        void passesThroughNonSensitiveAttributes() { // GH-90000
            List<SpanData> received = captureExportedSpans( // GH-90000
                    TelemetryRedactionFilter.wrap(delegate), // GH-90000
                    Attributes.of( // GH-90000
                            AttributeKey.stringKey(AgentTelemetryContract.ATTR_AGENT_ID), "my-agent", // GH-90000
                            AttributeKey.stringKey(AgentTelemetryContract.ATTR_TENANT_ID), "tenant-1")); // GH-90000

            assertThat(received).hasSize(1); // GH-90000
            Attributes attrs = received.get(0).getAttributes(); // GH-90000
            assertThat(attrs.get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_AGENT_ID))) // GH-90000
                    .isEqualTo("my-agent");
            assertThat(attrs.get(AttributeKey.stringKey(AgentTelemetryContract.ATTR_TENANT_ID))) // GH-90000
                    .isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("mixed span: sensitive attrs redacted, safe attrs preserved")
        void mixedSpanRedactsSensitiveOnly() { // GH-90000
            List<SpanData> received = captureExportedSpans( // GH-90000
                    TelemetryRedactionFilter.wrap(delegate), // GH-90000
                    Attributes.of( // GH-90000
                            AttributeKey.stringKey("ghatana.agent.id"), "agent-1",
                            AttributeKey.stringKey("ghatana.agent.prompt"), "do not leak me"));

            assertThat(received).hasSize(1); // GH-90000
            Attributes attrs = received.get(0).getAttributes(); // GH-90000
            assertThat(attrs.get(AttributeKey.stringKey("ghatana.agent.id"))).isEqualTo("agent-1");
            assertThat(attrs.get(AttributeKey.stringKey("ghatana.agent.prompt")))
                    .isEqualTo(TelemetryRedactionFilter.REDACTED_MARKER); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder() — custom key patterns")
    class CustomKeyPatterns {

        @Test
        @DisplayName("custom pattern redacts matching attributes")
        void customPatternRedactsMatchingAttributes() { // GH-90000
            TelemetryRedactionFilter filter = TelemetryRedactionFilter.builder(delegate) // GH-90000
                    .addSensitiveKey("ssn")
                    .build(); // GH-90000

            List<SpanData> received = captureExportedSpans(filter, // GH-90000
                    Attributes.of(AttributeKey.stringKey("user.ssn"), "123-45-6789"));

            assertRedacted(received, "user.ssn"); // GH-90000
        }

        @Test
        @DisplayName("builder without withDefaults() does not redact default patterns")
        void builderWithoutDefaultsDoesNotRedactDefaults() { // GH-90000
            TelemetryRedactionFilter filter = TelemetryRedactionFilter.builder(delegate) // GH-90000
                    .addSensitiveKey("custom.key")
                    .build(); // GH-90000

            List<SpanData> received = captureExportedSpans(filter, // GH-90000
                    Attributes.of(AttributeKey.stringKey("ghatana.agent.prompt"), "should pass through"));

            // prompt is NOT redacted because defaults weren't included
            assertThat(received).hasSize(1); // GH-90000
            assertThat(received.get(0).getAttributes() // GH-90000
                    .get(AttributeKey.stringKey("ghatana.agent.prompt")))
                    .isEqualTo("should pass through");
        }

        @Test
        @DisplayName("withDefaults() reinstates all default sensitive patterns")
        void withDefaultsReinstatesDefaultPatterns() { // GH-90000
            TelemetryRedactionFilter filter = TelemetryRedactionFilter.builder(delegate) // GH-90000
                    .withDefaults() // GH-90000
                    .build(); // GH-90000

            List<SpanData> received = captureExportedSpans(filter, // GH-90000
                    Attributes.of(AttributeKey.stringKey("ghatana.agent.prompt"), "sensitive"));

            assertRedacted(received, "ghatana.agent.prompt"); // GH-90000
        }
    }

    @Nested
    @DisplayName("lifecycle delegation")
    class Lifecycle {

        @Test
        @DisplayName("flush delegates to downstream")
        void flushDelegates() { // GH-90000
            when(delegate.flush()).thenReturn(io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess()); // GH-90000
            TelemetryRedactionFilter filter = TelemetryRedactionFilter.wrap(delegate); // GH-90000
            filter.flush(); // GH-90000
            verify(delegate).flush(); // GH-90000
        }

        @Test
        @DisplayName("shutdown delegates to downstream")
        void shutdownDelegates() { // GH-90000
            when(delegate.shutdown()).thenReturn(io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess()); // GH-90000
            TelemetryRedactionFilter filter = TelemetryRedactionFilter.wrap(delegate); // GH-90000
            filter.shutdown(); // GH-90000
            verify(delegate).shutdown(); // GH-90000
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
    private List<SpanData> captureExportedSpans(SpanExporter filter, Attributes spanAttributes) { // GH-90000
        List<SpanData> received = new ArrayList<>(); // GH-90000
        when(delegate.export(any())).thenAnswer(inv -> { // GH-90000
            received.addAll((java.util.Collection<SpanData>) inv.getArgument(0)); // GH-90000
            return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess(); // GH-90000
        });

        // Build a minimal SpanData using TestSpanData from the OTel testing library
        io.opentelemetry.sdk.testing.trace.TestSpanData spanData =
                io.opentelemetry.sdk.testing.trace.TestSpanData.builder() // GH-90000
                        .setName("test-span")
                        .setStartEpochNanos(0L) // GH-90000
                        .setEndEpochNanos(1000L) // GH-90000
                        .setHasEnded(true) // GH-90000
                        .setKind(io.opentelemetry.api.trace.SpanKind.INTERNAL) // GH-90000
                        .setStatus(io.opentelemetry.sdk.trace.data.StatusData.ok()) // GH-90000
                        .setAttributes(spanAttributes) // GH-90000
                        .build(); // GH-90000

        filter.export(List.of(spanData)); // GH-90000
        return received;
    }

    private static void assertRedacted(List<SpanData> spans, String attributeKey) { // GH-90000
        assertThat(spans).hasSize(1); // GH-90000
        String value = spans.get(0).getAttributes().get(AttributeKey.stringKey(attributeKey)); // GH-90000
        assertThat(value) // GH-90000
                .as("Attribute '%s' should be redacted", attributeKey) // GH-90000
                .isEqualTo(TelemetryRedactionFilter.REDACTED_MARKER); // GH-90000
    }
}
