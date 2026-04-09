package com.ghatana.phr.kernel.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Generates and propagates correlation metadata across PHR workflows
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class PhrTraceContext {

    private PhrTraceContext() {}

    public static String newCorrelationId(String operation) {
        String sanitizedOperation = PhrInputSanitizationUtils.requireSafeCode(operation, "operation");
        return sanitizedOperation.toLowerCase() + "-" + UUID.randomUUID();
    }

    public static Map<String, String> metadata(String correlationId, String operation) {
        return metadata(correlationId, operation, Map.of());
    }

    public static Map<String, String> metadata(
            String correlationId,
            String operation,
            Map<String, String> baseMetadata) {
        String sanitizedCorrelationId = PhrInputSanitizationUtils.requireSafeIdentifier(
            correlationId,
            "correlationId"
        );
        String sanitizedOperation = PhrInputSanitizationUtils.requireSafeCode(operation, "operation");
        HashMap<String, String> metadata = new HashMap<>(baseMetadata);
        metadata.put("correlationId", sanitizedCorrelationId);
        metadata.put("traceOperation", sanitizedOperation);
        metadata.put("traceRecordedAt", Instant.now().toString());
        return Map.copyOf(metadata);
    }
}
