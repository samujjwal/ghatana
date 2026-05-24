package com.ghatana.agent.runtime.replay;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable tool call record for agent audit and replay.
 *
 * @doc.type record
 * @doc.purpose Captures one governed tool invocation made during agent execution
 * @doc.layer agent-runtime
 * @doc.pattern AuditRecord
 */
public record AgentToolCallRecord(
        String toolCallId,
        String toolName,
        String idempotencyKey,
        boolean mutating,
        String requestHash,
        String responseHash,
        String status,
        Instant startedAt,
        Instant completedAt,
        Map<String, String> auditMetadata
) {

    public AgentToolCallRecord {
        requireNonBlank(toolCallId, "toolCallId");
        requireNonBlank(toolName, "toolName");
        requireNonBlank(requestHash, "requestHash");
        requireNonBlank(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        if (mutating) {
            requireNonBlank(idempotencyKey, "idempotencyKey");
        }
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey;
        responseHash = responseHash == null ? "" : responseHash;
        auditMetadata = auditMetadata == null ? Map.of() : Map.copyOf(auditMetadata);
    }

    private static void requireNonBlank(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
