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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;

import java.util.List;
import java.util.Objects;

/**
 * Package-private {@link SpanData} decorator used by {@link TelemetryRedactionFilter}.
 *
 * <p>All methods delegate to the original {@link SpanData} instance except
 * {@link #getAttributes()}, which returns the caller-supplied redacted attributes,
 * and {@link #getTotalAttributeCount()}, which reflects the redacted count.
 *
 * @doc.type class
 * @doc.purpose SpanData decorator that replaces span attributes with a redacted copy.
 * @doc.layer platform
 * @doc.pattern Decorator
 */
final class RedactedSpanData implements SpanData {

    private final SpanData original;
    private final Attributes redactedAttributes;

    RedactedSpanData(SpanData original, Attributes redactedAttributes) {
        this.original = Objects.requireNonNull(original, "original");
        this.redactedAttributes = Objects.requireNonNull(redactedAttributes, "redactedAttributes");
    }

    @Override
    public SpanContext getSpanContext() {
        return original.getSpanContext();
    }

    @Override
    public SpanContext getParentSpanContext() {
        return original.getParentSpanContext();
    }

    @Override
    public Resource getResource() {
        return original.getResource();
    }

    @Override
    public InstrumentationScopeInfo getInstrumentationScopeInfo() {
        return original.getInstrumentationScopeInfo();
    }

    @Override
    public String getName() {
        return original.getName();
    }

    @Override
    public SpanKind getKind() {
        return original.getKind();
    }

    @Override
    public long getStartEpochNanos() {
        return original.getStartEpochNanos();
    }

    @Override
    public Attributes getAttributes() {
        return redactedAttributes;
    }

    @Override
    public List<EventData> getEvents() {
        return original.getEvents();
    }

    @Override
    public List<LinkData> getLinks() {
        return original.getLinks();
    }

    @Override
    public StatusData getStatus() {
        return original.getStatus();
    }

    @Override
    public long getEndEpochNanos() {
        return original.getEndEpochNanos();
    }

    @Override
    public boolean hasEnded() {
        return original.hasEnded();
    }

    @Override
    public int getTotalRecordedEvents() {
        return original.getTotalRecordedEvents();
    }

    @Override
    public int getTotalRecordedLinks() {
        return original.getTotalRecordedLinks();
    }

    @Override
    public int getTotalAttributeCount() {
        return (int) redactedAttributes.size();
    }

    @SuppressWarnings("deprecation")
    @Override
    public io.opentelemetry.sdk.common.InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
        return original.getInstrumentationLibraryInfo();
    }
}
