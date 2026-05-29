/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.operations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OperationLedger.
 * 
 * P10.5: Verify operation ledger integration tests, error-path tests, runtime truth degraded/unavailable tests.
 * 
 * @doc.type test
 * @doc.purpose Verify operation ledger behavior
 * @doc.layer product
 */
@DisplayName("OperationLedger Tests")
class OperationLedgerTest {

    private OperationLedger ledger;
    private TestStore store;

    @BeforeEach
    void setUp() {
        store = new TestStore();
        ledger = new OperationLedger(store);
    }

    @Test
    @DisplayName("createOperation creates a new operation with STARTED status")
    void createOperationCreatesNewOperation() {
        OperationLedger.Operation operation = ledger.createOperation(
            OperationLedger.OperationType.DATA_INGESTION,
            "tenant-1",
            "user-1"
        );

        assertNotNull(operation.id());
        assertEquals(OperationLedger.OperationType.DATA_INGESTION, operation.operationType());
        assertEquals("tenant-1", operation.tenantId());
        assertEquals(OperationLedger.OperationStatus.STARTED, operation.status());
        assertEquals("user-1", operation.initiatedBy());
        assertNotNull(operation.startedAt());
        assertTrue(operation.isRunning());
        assertFalse(operation.isCompleted());
    }

    @Test
    @DisplayName("recordEvent adds event to operation")
    void recordEventAddsEvent() {
        OperationLedger.Operation operation = ledger.createOperation(
            OperationLedger.OperationType.PIPELINE_RUN,
            "tenant-1",
            "user-1"
        );

        OperationLedger.OperationEvent event = new OperationLedger.OperationEvent(
            "checkpoint_reached",
            "Stage 1 completed",
            Map.of("stage", "1")
        );
        ledger.recordEvent(operation.id(), event);

        OperationLedger.Operation updated = store.findById(operation.id()).orElseThrow();
        assertEquals(1, updated.events().size());
        assertEquals("checkpoint_reached", updated.events().get(0).eventType());
    }

    @Test
    @DisplayName("recordRetry increments retry count and sets RETRYING status")
    void recordRetryIncrementsCount() {
        OperationLedger.Operation operation = ledger.createOperation(
            OperationLedger.OperationType.AGENT_EXECUTION,
            "tenant-1",
            "user-1"
        );

        OperationLedger.RetryInfo retryInfo = new OperationLedger.RetryInfo(
            1,
            "Transient error",
            Instant.now().plusMillis(1000)
        );
        ledger.recordRetry(operation.id(), retryInfo);

        OperationLedger.Operation updated = store.findById(operation.id()).orElseThrow();
        assertEquals(OperationLedger.OperationStatus.RETRYING, updated.status());
        assertEquals(1, updated.metadata().get("retryCount"));
    }

    @Test
    @DisplayName("recordFailure sets FAILED status")
    void recordFailureSetsFailedStatus() {
        OperationLedger.Operation operation = ledger.createOperation(
            OperationLedger.OperationType.CONNECTOR_SYNC,
            "tenant-1",
            "user-1"
        );

        OperationLedger.ErrorInfo errorInfo = new OperationLedger.ErrorInfo(
            "ConnectionError",
            "Failed to connect",
            "stack trace",
            true
        );
        ledger.recordFailure(operation.id(), errorInfo);

        OperationLedger.Operation updated = store.findById(operation.id()).orElseThrow();
        assertEquals(OperationLedger.OperationStatus.FAILED, updated.status());
        assertNotNull(updated.completedAt());
    }

    @Test
    @DisplayName("completeOperation sets COMPLETED status with success result")
    void completeOperationSetsCompletedStatus() {
        OperationLedger.Operation operation = ledger.createOperation(
            OperationLedger.OperationType.POLICY_EVALUATION,
            "tenant-1",
            "user-1"
        );

        OperationLedger.OperationResult result = new OperationLedger.OperationResult(
            true,
            Map.of("recordsProcessed", 100),
            null
        );
        ledger.completeOperation(operation.id(), result);

        OperationLedger.Operation updated = store.findById(operation.id()).orElseThrow();
        assertEquals(OperationLedger.OperationStatus.COMPLETED, updated.status());
        assertNotNull(updated.completedAt());
    }

    @Test
    @DisplayName("completeOperation sets FAILED status with failure result")
    void completeOperationSetsFailedStatusOnFailure() {
        OperationLedger.Operation operation = ledger.createOperation(
            OperationLedger.OperationType.RETENTION_PURGE,
            "tenant-1",
            "user-1"
        );

        OperationLedger.OperationResult result = new OperationLedger.OperationResult(
            false,
            Map.of(),
            "Purge failed"
        );
        ledger.completeOperation(operation.id(), result);

        OperationLedger.Operation updated = store.findById(operation.id()).orElseThrow();
        assertEquals(OperationLedger.OperationStatus.FAILED, updated.status());
    }

    @Test
    @DisplayName("getOperation returns operation by ID")
    void getOperationReturnsOperation() {
        OperationLedger.Operation operation = ledger.createOperation(
            OperationLedger.OperationType.AV_PROCESSING,
            "tenant-1",
            "user-1"
        );

        Optional<OperationLedger.Operation> found = ledger.getOperation(operation.id());
        assertTrue(found.isPresent());
        assertEquals(operation.id(), found.get().id());
    }

    @Test
    @DisplayName("getOperation returns empty for non-existent ID")
    void getOperationReturnsEmptyForNonExistent() {
        Optional<OperationLedger.Operation> found = ledger.getOperation("non-existent");
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("getOperationsForTenant returns operations for tenant")
    void getOperationsForTenantReturnsOperations() {
        ledger.createOperation(OperationLedger.OperationType.DATA_INGESTION, "tenant-1", "user-1");
        ledger.createOperation(OperationLedger.OperationType.PIPELINE_RUN, "tenant-1", "user-1");
        ledger.createOperation(OperationLedger.OperationType.AGENT_EXECUTION, "tenant-2", "user-1");

        List<OperationLedger.Operation> operations = ledger.getOperationsForTenant("tenant-1", null);
        assertEquals(2, operations.size());
    }

    @Test
    @DisplayName("getOperationsForTenant filters by status")
    void getOperationsForTenantFiltersByStatus() {
        OperationLedger.Operation op1 = ledger.createOperation(OperationLedger.OperationType.DATA_INGESTION, "tenant-1", "user-1");
        ledger.completeOperation(op1.id(), new OperationLedger.OperationResult(true, Map.of(), null));
        
        ledger.createOperation(OperationLedger.OperationType.PIPELINE_RUN, "tenant-1", "user-1");

        List<OperationLedger.Operation> completed = ledger.getOperationsForTenant("tenant-1", OperationLedger.OperationStatus.COMPLETED);
        assertEquals(1, completed.size());
    }

    @Test
    @DisplayName("durationMillis returns correct duration for completed operation")
    void durationMillisReturnsCorrectDuration() {
        OperationLedger.Operation operation = ledger.createOperation(
            OperationLedger.OperationType.INDEXING,
            "tenant-1",
            "user-1"
        );

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ledger.completeOperation(operation.id(), new OperationLedger.OperationResult(true, Map.of(), null));

        OperationLedger.Operation updated = store.findById(operation.id()).orElseThrow();
        assertTrue(updated.durationMillis() >= 10);
    }

    @Test
    @DisplayName("durationMillis returns running duration for running operation")
    void durationMillisReturnsRunningDuration() {
        OperationLedger.Operation operation = ledger.createOperation(
            OperationLedger.OperationType.AUDIT,
            "tenant-1",
            "user-1"
        );

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        OperationLedger.Operation updated = store.findById(operation.id()).orElseThrow();
        assertTrue(updated.durationMillis() >= 10);
    }

    /**
     * Test store implementation.
     */
    private static class TestStore implements OperationLedger.OperationStore {
        private final Map<String, OperationLedger.Operation> operations = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void save(OperationLedger.Operation operation) {
            operations.put(operation.id(), operation);
        }

        @Override
        public Optional<OperationLedger.Operation> findById(String operationId) {
            return Optional.ofNullable(operations.get(operationId));
        }

        @Override
        public List<OperationLedger.Operation> findByTenantId(String tenantId, OperationLedger.OperationStatus status) {
            return operations.values().stream()
                .filter(op -> op.tenantId().equals(tenantId))
                .filter(op -> status == null || op.status() == status)
                .toList();
        }
    }
}
