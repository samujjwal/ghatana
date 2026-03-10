package com.ghatana.yappc.agent;

/**
 * Distributed tracing context for observability.
 *
 * @param traceId W3C trace ID
 * @param spanId current span ID
 * @doc.type record
 * @doc.purpose Distributed tracing context propagation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TraceContext(String traceId, String spanId) {}
