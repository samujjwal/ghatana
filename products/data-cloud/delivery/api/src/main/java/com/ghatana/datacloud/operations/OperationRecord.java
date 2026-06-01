package com.ghatana.datacloud.operations;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical Data Cloud operation/job record.
 *
 * <p>The record is intentionally surface-neutral so connectors, media jobs,
 * pipeline executions, agent runs, and background work can share one UI and
 * API contract without each surface inventing a separate job shape.
 *
 * <p>Pass 9: Added traceId and requestId for unified cross-plane observability.
 * <p>WS11-1: Added failureReason, policyDecision, auditEventId for comprehensive observability.
 *
 * @doc.type record
 * @doc.purpose Shared operation lifecycle contract for Data Cloud surfaces
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record OperationRecord(
        String operationId,
        String traceId,
        String requestId,
        String tenantId,
        OperationKind kind,
        OperationStatus status,
        String resourceType,
        String resourceId,
        String action,
        String summary,
        String detail,
        String actorId,
        String correlationId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        boolean cancellable,
        String failureReason,
        String policyDecision,
        String auditEventId,
        Map<String, Object> metadata) {

    public OperationRecord {
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (operationId.isBlank()) throw new IllegalArgumentException("operationId must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        traceId = traceId == null ? operationId : traceId;
        requestId = requestId == null ? traceId : requestId;
        failureReason = failureReason == null ? "" : failureReason;
        policyDecision = policyDecision == null ? "" : policyDecision;
        auditEventId = auditEventId == null ? "" : auditEventId;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static OperationRecord create(
            String tenantId,
            OperationKind kind,
            OperationStatus status,
            String resourceType,
            String resourceId,
            String action,
            String summary,
            String actorId,
            String correlationId,
            boolean cancellable,
            Map<String, Object> metadata) {
        Instant now = Instant.now();
        String operationId = "op-" + UUID.randomUUID();
        return new OperationRecord(
                operationId,
                operationId,
                operationId,
                tenantId,
                kind,
                status,
                resourceType,
                resourceId,
                action,
                summary,
                null,
                actorId,
                correlationId,
                now,
                now,
                terminal(status) ? now : null,
                cancellable,
                null, // failureReason
                null, // policyDecision
                null, // auditEventId
                metadata);
    }

    public static OperationRecord create(
            String tenantId,
            String traceId,
            String requestId,
            OperationKind kind,
            OperationStatus status,
            String resourceType,
            String resourceId,
            String action,
            String summary,
            String actorId,
            String correlationId,
            boolean cancellable,
            Map<String, Object> metadata) {
        Instant now = Instant.now();
        String operationId = "op-" + UUID.randomUUID();
        return new OperationRecord(
                operationId,
                traceId,
                requestId,
                tenantId,
                kind,
                status,
                resourceType,
                resourceId,
                action,
                summary,
                null,
                actorId,
                correlationId,
                now,
                now,
                terminal(status) ? now : null,
                cancellable,
                null, // failureReason
                null, // policyDecision
                null, // auditEventId
                metadata);
    }

    public OperationRecord transition(OperationStatus nextStatus, String detail, Map<String, Object> additionalMetadata) {
        Map<String, Object> merged = new LinkedHashMap<>(metadata);
        if (additionalMetadata != null) {
            merged.putAll(additionalMetadata);
        }
        Instant now = Instant.now();
        return new OperationRecord(
                operationId,
                traceId,
                requestId,
                tenantId,
                kind,
                nextStatus,
                resourceType,
                resourceId,
                action,
                summary,
                detail,
                actorId,
                correlationId,
                createdAt,
                now,
                terminal(nextStatus) ? now : completedAt,
                cancellable && !terminal(nextStatus),
                failureReason,
                policyDecision,
                auditEventId,
                merged);
    }

    public Map<String, Object> toResponse() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operationId", operationId);
        body.put("traceId", traceId);
        body.put("requestId", requestId);
        body.put("tenantId", tenantId);
        body.put("kind", kind.name());
        body.put("status", status.name());
        body.put("resourceType", resourceType == null ? "" : resourceType);
        body.put("resourceId", resourceId == null ? "" : resourceId);
        body.put("action", action);
        body.put("summary", summary == null ? "" : summary);
        body.put("detail", detail);
        body.put("actorId", actorId == null ? "" : actorId);
        body.put("correlationId", correlationId == null ? "" : correlationId);
        body.put("createdAt", createdAt.toString());
        body.put("updatedAt", updatedAt.toString());
        body.put("completedAt", completedAt == null ? "" : completedAt.toString());
        body.put("cancellable", cancellable);
        body.put("failureReason", failureReason == null ? "" : failureReason);
        body.put("policyDecision", policyDecision == null ? "" : policyDecision);
        body.put("auditEventId", auditEventId == null ? "" : auditEventId);
        body.put("metadata", metadata);
        return Map.copyOf(body);
    }

    public static boolean terminal(OperationStatus status) {
        return status == OperationStatus.SUCCEEDED
                || status == OperationStatus.FAILED
                || status == OperationStatus.CANCELLED
                || status == OperationStatus.BLOCKED;
    }
}
