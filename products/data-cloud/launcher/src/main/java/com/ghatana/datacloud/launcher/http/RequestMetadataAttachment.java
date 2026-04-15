package com.ghatana.datacloud.launcher.http;

/**
 * Immutable request-scoped metadata attached to ActiveJ HTTP requests.
 *
 * @doc.type record
 * @doc.purpose Propagates request, tenant, and trace metadata across async handler chains
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RequestMetadataAttachment(
        String requestId,
        String tenantId,
        String traceId,
        String requestSpanId,
        String parentSpanId,
        String method,
        String path,
        boolean sampled) {
}