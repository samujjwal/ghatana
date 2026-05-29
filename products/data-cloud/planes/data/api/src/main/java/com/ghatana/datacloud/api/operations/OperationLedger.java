/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.operations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Unified operation/run model for observability and operations ledger.
 * 
 * P10.1: Add unified operation/run model.
 * Provides a single source of truth for tracking all operations across the system.
 * 
 * @doc.type class
 * @doc.purpose Unified operation/run model for observability
 * @doc.layer product
 * @doc.pattern Ledger
 */
public final class OperationLedger {

    private final OperationStore store;

    public OperationLedger(OperationStore store) {
        this.store = store;
    }

    /**
     * Creates a new operation record.
     *
     * @param operationType the operation type
     * @param tenantId the tenant ID
     * @param initiatedBy who initiated the operation
     * @return the created operation
     */
    public Operation createOperation(OperationType operationType, String tenantId, String initiatedBy) {
        Operation operation = new Operation(
            java.util.UUID.randomUUID().toString(),
            operationType,
            tenantId,
            OperationStatus.STARTED,
            initiatedBy,
            Instant.now(),
            null,
            Map.of(),
            List.of(),
            Map.of()
        );
        store.save(operation);
        return operation;
    }

    /**
     * Records an event for an operation.
     *
     * @param operationId the operation ID
     * @param event the event to record
     */
    public void recordEvent(String operationId, OperationEvent event) {
        Operation operation = store.findById(operationId)
            .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationId));
        
        Operation updated = new Operation(
            operation.id(),
            operation.operationType(),
            operation.tenantId(),
            operation.status(),
            operation.initiatedBy(),
            operation.startedAt(),
            operation.completedAt(),
            operation.metadata(),
            Stream.concat(operation.events().stream(), Stream.of(event)).toList(),
            operation.dependencies()
        );
        store.save(updated);
    }

    /**
     * Records a retry for an operation.
     *
     * @param operationId the operation ID
     * @param retryInfo the retry information
     */
    public void recordRetry(String operationId, RetryInfo retryInfo) {
        Operation operation = store.findById(operationId)
            .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationId));
        
        Map<String, Object> updatedMetadata = new java.util.LinkedHashMap<>(operation.metadata());
        updatedMetadata.put("lastRetry", retryInfo);
        updatedMetadata.put("retryCount", (operation.metadata().getOrDefault("retryCount", 0) instanceof Integer n ? n + 1 : 1));
        
        Operation updated = new Operation(
            operation.id(),
            operation.operationType(),
            operation.tenantId(),
            OperationStatus.RETRYING,
            operation.initiatedBy(),
            operation.startedAt(),
            operation.completedAt(),
            updatedMetadata,
            operation.events(),
            operation.dependencies()
        );
        store.save(updated);
    }

    /**
     * Records a failure for an operation.
     *
     * @param operationId the operation ID
     * @param error the error information
     */
    public void recordFailure(String operationId, ErrorInfo error) {
        Operation operation = store.findById(operationId)
            .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationId));
        
        Operation updated = new Operation(
            operation.id(),
            operation.operationType(),
            operation.tenantId(),
            OperationStatus.FAILED,
            operation.initiatedBy(),
            operation.startedAt(),
            Instant.now(),
            Map.copyOf(operation.metadata()),
            operation.events(),
            operation.dependencies()
        );
        store.save(updated);
    }

    /**
     * Completes an operation.
     *
     * @param operationId the operation ID
     * @param result the operation result
     */
    public void completeOperation(String operationId, OperationResult result) {
        Operation operation = store.findById(operationId)
            .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationId));
        
        Map<String, Object> updatedMetadata = new java.util.LinkedHashMap<>(operation.metadata());
        updatedMetadata.put("result", result);
        
        Operation updated = new Operation(
            operation.id(),
            operation.operationType(),
            operation.tenantId(),
            result.success() ? OperationStatus.COMPLETED : OperationStatus.FAILED,
            operation.initiatedBy(),
            operation.startedAt(),
            Instant.now(),
            updatedMetadata,
            operation.events(),
            operation.dependencies()
        );
        store.save(updated);
    }

    /**
     * Gets an operation by ID.
     *
     * @param operationId the operation ID
     * @return the operation if found
     */
    public Optional<Operation> getOperation(String operationId) {
        return store.findById(operationId);
    }

    /**
     * Gets operations for a tenant.
     *
     * @param tenantId the tenant ID
     * @param status optional status filter
     * @return list of operations
     */
    public List<Operation> getOperationsForTenant(String tenantId, OperationStatus status) {
        return store.findByTenantId(tenantId, status);
    }

    /**
     * Operation record.
     *
     * @param id unique identifier
     * @param operationType the operation type
     * @param tenantId the tenant ID
     * @param status current status
     * @param initiatedBy who initiated the operation
     * @param startedAt when the operation started
     * @param completedAt when the operation completed (null if still running)
     * @param metadata operation metadata
     * @param events recorded events
     * @param dependencies runtime dependencies
     */
    public record Operation(
            String id,
            OperationType operationType,
            String tenantId,
            OperationStatus status,
            String initiatedBy,
            Instant startedAt,
            Instant completedAt,
            Map<String, Object> metadata,
            List<OperationEvent> events,
            Map<String, RuntimeDependency> dependencies) {

        public boolean isRunning() {
            return status == OperationStatus.STARTED || status == OperationStatus.RETRYING;
        }

        public boolean isCompleted() {
            return status == OperationStatus.COMPLETED || status == OperationStatus.FAILED || status == OperationStatus.CANCELLED;
        }

        public long durationMillis() {
            if (completedAt == null) {
                return Instant.now().toEpochMilli() - startedAt.toEpochMilli();
            }
            return completedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
    }

    /**
     * Operation type.
     */
    public enum OperationType {
        DATA_INGESTION,
        DATA_SYNC,
        PIPELINE_RUN,
        AGENT_EXECUTION,
        POLICY_EVALUATION,
        RETENTION_PURGE,
        REDACTION,
        AV_PROCESSING,
        INDEXING,
        AUDIT
    }

    /**
     * Operation status.
     */
    public enum OperationStatus {
        STARTED,
        RETRYING,
        COMPLETED,
        FAILED,
        CANCELLED,
        DEAD_LETTER
    }

    /**
     * Operation event.
     *
     * @param timestamp when the event occurred
     * @param eventType the event type
     * @param message the event message
     * @param data event data
     */
    public record OperationEvent(
            Instant timestamp,
            String eventType,
            String message,
            Map<String, Object> data) {

        public OperationEvent(String eventType, String message, Map<String, Object> data) {
            this(Instant.now(), eventType, message, data);
        }
    }

    /**
     * Retry information.
     *
     * @param attemptNumber the attempt number
     * @param reason the reason for retry
     * @param nextRetryAt when the next retry will occur
     */
    public record RetryInfo(
            int attemptNumber,
            String reason,
            Instant nextRetryAt) {}

    /**
     * Error information.
     *
     * @param errorType the error type
     * @param errorMessage the error message
     * @param stackTrace the stack trace
     * @param recoverable whether the error is recoverable
     */
    public record ErrorInfo(
            String errorType,
            String errorMessage,
            String stackTrace,
            boolean recoverable) {}

    /**
     * Operation result.
     *
     * @param success whether the operation succeeded
     * @param output the operation output
     * @param error error message (if failed)
     */
    public record OperationResult(
            boolean success,
            Map<String, Object> output,
            String error) {}

    /**
     * Runtime dependency.
     *
     * @param name the dependency name
     * @param status the dependency status
     * @param lastChecked when the dependency was last checked
     */
    public record RuntimeDependency(
            String name,
            DependencyStatus status,
            Instant lastChecked) {

        public enum DependencyStatus {
            HEALTHY,
            DEGRADED,
            UNAVAILABLE,
            MISCONFIGURED
        }
    }

    /**
     * Store interface for operations.
     */
    public interface OperationStore {
        void save(Operation operation);
        Optional<Operation> findById(String operationId);
        List<Operation> findByTenantId(String tenantId, OperationStatus status);
    }
}
