/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Generates and attaches correlation metadata for Finance transaction workflows
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class FinanceTraceContext {

    private FinanceTraceContext() {}

    public static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    public static Map<String, Object> metadata(String correlationId, String operation) {
        return metadata(correlationId, operation, Map.of());
    }

    public static Map<String, Object> metadata(
            String correlationId,
            String operation,
            Map<String, Object> baseMetadata) {
        String sanitizedCorrelationId = TransactionInputSanitizationUtils.requireSafeIdentifier(
            correlationId,
            "correlationId"
        );
        String sanitizedOperation = TransactionInputSanitizationUtils.requireSafeCode(operation, "operation");
        HashMap<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put("correlation_id", sanitizedCorrelationId);
        metadata.put("trace_operation", sanitizedOperation);
        metadata.put("trace_recorded_at", Instant.now().toString());
        return Map.copyOf(metadata);
    }
}
