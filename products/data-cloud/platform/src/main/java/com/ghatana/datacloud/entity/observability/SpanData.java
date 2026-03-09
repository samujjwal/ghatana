package com.ghatana.datacloud.entity.observability;

import java.time.Instant;

/**
 * Represents span data for storage tier management.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a minimal interface for span data needed by storage tier management.
 * This is a domain-level abstraction that doesn't depend on OpenTelemetry SDK.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * SpanData span = new SimpleSpanData("trace-123", "span-456", Instant.now());
 * RetentionPolicy.StorageTier tier = policy.getStorageTier(span);
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Domain abstraction for span data
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public interface SpanData {

    /**
     * Gets the trace ID this span belongs to.
     *
     * @return trace ID
     */
    String getTraceId();

    /**
     * Gets the unique span ID.
     *
     * @return span ID
     */
    String getSpanId();

    /**
     * Gets the start time of this span.
     *
     * @return start time as Instant
     */
    Instant getStartTime();

    /**
     * Simple record implementation of SpanData.
     */
    record Simple(String traceId, String spanId, Instant startTime) implements SpanData {

        @Override
        public String getTraceId() {
            return traceId;
        }

        @Override
        public String getSpanId() {
            return spanId;
        }

        @Override
        public Instant getStartTime() {
            return startTime;
        }
    }
}
