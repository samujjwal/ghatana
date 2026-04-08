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
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link SpanExporter} decorator that redacts sensitive span attributes before forwarding
 * spans to the downstream exporter.
 *
 * <p><strong>TX-3 requirement:</strong> Telemetry must redact prompts, memory fragments, and
 * tool payloads by default so they do not leak into tracing backends. This filter intercepts all
 * spans on the export path and removes or masks values for attributes whose keys match any of the
 * {@linkplain #DEFAULT_SENSITIVE_KEYS built-in sensitive key patterns}.
 *
 * <h2>Redaction policy</h2>
 * <ul>
 *   <li>Attribute keys containing any of the {@linkplain #DEFAULT_SENSITIVE_KEYS sensitive
 *       terms} are replaced with the value {@value #REDACTED_MARKER}.</li>
 *   <li>All other attributes are passed through unchanged.</li>
 *   <li>Additional keys can be registered via
 *       {@link Builder#addSensitiveKey(String)}.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SpanExporter exporter = TelemetryRedactionFilter.wrap(otlpExporter);
 * }</pre>
 *
 * @see AgentTelemetryContract
 * @doc.type class
 * @doc.purpose Redacts sensitive span attributes (prompts, memory fragments, tool payloads)
 *             before export to prevent telemetry backends from becoming shadow data stores.
 * @doc.layer platform
 * @doc.pattern Decorator
 */
public final class TelemetryRedactionFilter implements SpanExporter {

    /** Replacement value for redacted attribute values. */
    public static final String REDACTED_MARKER = "[REDACTED]";

    /**
     * Default set of case-insensitive key fragment patterns that trigger redaction.
     * Any span attribute key that contains one of these substrings (case-insensitive) is redacted.
     */
    public static final Set<String> DEFAULT_SENSITIVE_KEYS = Set.of(
            "prompt",
            "memory.fragment",
            "memory.content",
            "tool.input",
            "tool.output",
            "tool.payload",
            "input.raw",
            "output.raw",
            "completion",
            "embedding",
            "secret",
            "password",
            "credential",
            "token"
    );

    private final SpanExporter delegate;
    private final Set<String> sensitiveKeyPatterns;

    private TelemetryRedactionFilter(SpanExporter delegate, Set<String> sensitiveKeyPatterns) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.sensitiveKeyPatterns = Set.copyOf(sensitiveKeyPatterns);
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Wraps the given exporter with the default sensitive key patterns.
     *
     * @param delegate the downstream exporter to wrap; must not be null
     * @return a {@link TelemetryRedactionFilter} wrapping {@code delegate}
     */
    public static TelemetryRedactionFilter wrap(SpanExporter delegate) {
        return new TelemetryRedactionFilter(delegate, DEFAULT_SENSITIVE_KEYS);
    }

    /**
     * Returns a builder for configuring a custom set of sensitive key patterns.
     *
     * @param delegate the downstream exporter to wrap; must not be null
     * @return a new {@link Builder}
     */
    public static Builder builder(SpanExporter delegate) {
        return new Builder(delegate);
    }

    // ── SpanExporter ─────────────────────────────────────────────────────────

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        List<SpanData> redacted = spans.stream()
                .map(this::redactSpan)
                .collect(Collectors.toList());
        return delegate.export(redacted);
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    // ── Internal redaction ───────────────────────────────────────────────────

    private SpanData redactSpan(SpanData original) {
        Attributes originalAttrs = original.getAttributes();
        AttributesBuilder builder = Attributes.builder();
        originalAttrs.forEach((key, value) -> {
            if (isSensitive(key.getKey())) {
                builder.put(AttributeKey.stringKey(key.getKey()), REDACTED_MARKER);
            } else {
                putAttribute(builder, key, value);
            }
        });
        Attributes redactedAttrs = builder.build();

        if (redactedAttrs.equals(originalAttrs)) {
            return original;
        }
        return new RedactedSpanData(original, redactedAttrs);
    }

    private boolean isSensitive(String key) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        for (String pattern : sensitiveKeyPatterns) {
            if (lowerKey.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> void putAttribute(AttributesBuilder builder, AttributeKey<T> key, Object value) {
        builder.put((AttributeKey<Object>) key, value);
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    /**
     * Builder for {@link TelemetryRedactionFilter} with a custom set of sensitive key patterns.
     * Starts with only the explicitly added patterns — does not inherit
     * {@link #DEFAULT_SENSITIVE_KEYS} unless {@link #withDefaults()} is called.
     */
    public static final class Builder {
        private final SpanExporter delegate;
        private final java.util.HashSet<String> sensitiveKeys = new java.util.HashSet<>();

        private Builder(SpanExporter delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        /** Adds all {@link TelemetryRedactionFilter#DEFAULT_SENSITIVE_KEYS} to the set. */
        public Builder withDefaults() {
            sensitiveKeys.addAll(DEFAULT_SENSITIVE_KEYS);
            return this;
        }

        /** Adds a custom case-insensitive key fragment pattern that triggers redaction. */
        public Builder addSensitiveKey(String keyPattern) {
            sensitiveKeys.add(Objects.requireNonNull(keyPattern, "keyPattern").toLowerCase(Locale.ROOT));
            return this;
        }

        /** Builds the configured {@link TelemetryRedactionFilter}. */
        public TelemetryRedactionFilter build() {
            return new TelemetryRedactionFilter(delegate, sensitiveKeys);
        }
    }
}
