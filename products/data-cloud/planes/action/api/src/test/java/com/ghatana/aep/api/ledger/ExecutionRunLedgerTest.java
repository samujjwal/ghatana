/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.api.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ExecutionRunLedger.
 * 
 * P7.3: Verify execution run ledger with logs, checkpoints, retries, rollback, cancellation, and policy decisions.
 * 
 * @doc.type test
 * @doc.purpose Verify execution run ledger behavior
 * @doc.layer product
 */
@DisplayName("ExecutionRunLedger Tests")
class ExecutionRunLedgerTest {

    private ExecutionRunLedger ledger;
    private TestStore store;

    @BeforeEach
    void setUp() {
        store = new TestStore();
        ledger = new ExecutionRunLedger(store);
    }

    @Test
    @DisplayName("createRun creates a new execution run with STARTED status")
    void createRunCreatesNewRun() {
        ExecutionRunLedger.ExecutionRun run = ledger.createRun("pipeline-1", "tenant-1", "user-1");

        assertNotNull(run);
        assertEquals("pipeline-1", run.pipelineId());
        assertEquals("tenant-1", run.tenantId());
        assertEquals(ExecutionRunLedger.ExecutionRunStatus.STARTED, run.status());
        assertEquals("user-1", run.triggeredBy());
        assertNotNull(run.startedAt());
        assertEquals(0, run.retryCount());
        assertTrue(run.isRunning());
        assertFalse(run.isCompleted());
    }

    @Test
    @DisplayName("recordCheckpoint adds checkpoint to run")
    void recordCheckpointAddsCheckpoint() {
        ExecutionRunLedger.ExecutionRun run = ledger.createRun("pipeline-1", "tenant-1", "user-1");
        
        ExecutionRunLedger.Checkpoint checkpoint = new ExecutionRunLedger.Checkpoint(
            "stage-1", Map.of("data", "value")
        );
        ledger.recordCheckpoint(run.id(), checkpoint);

        ExecutionRunLedger.ExecutionRun updated = store.findById(run.id()).orElseThrow();
        assertEquals(1, updated.checkpoints().size());
        assertEquals("stage-1", updated.checkpoints().get(0).stage());
    }

    @Test
    @DisplayName("recordLog adds log entry to run")
    void recordLogAddsLogEntry() {
        ExecutionRunLedger.ExecutionRun run = ledger.createRun("pipeline-1", "tenant-1", "user-1");
        
        ExecutionRunLedger.LogEntry log = new ExecutionRunLedger.LogEntry("INFO", "category", "message");
        ledger.recordLog(run.id(), log);

        ExecutionRunLedger.ExecutionRun updated = store.findById(run.id()).orElseThrow();
        assertEquals(1, updated.logs().size());
        assertEquals("message", updated.logs().get(0).message());
    }

    @Test
    @DisplayName("recordPolicyDecision adds policy decision to run")
    void recordPolicyDecisionAddsDecision() {
        ExecutionRunLedger.ExecutionRun run = ledger.createRun("pipeline-1", "tenant-1", "user-1");
        
        ExecutionRunLedger.PolicyDecision decision = new ExecutionRunLedger.PolicyDecision(
            "policy-1", ExecutionRunLedger.PolicyDecision.Decision.APPROVE, "reason"
        );
        ledger.recordPolicyDecision(run.id(), decision);

        ExecutionRunLedger.ExecutionRun updated = store.findById(run.id()).orElseThrow();
        assertTrue(updated.policyDecisions().containsKey("policy-1"));
        assertEquals(ExecutionRunLedger.PolicyDecision.Decision.APPROVE, 
            updated.policyDecisions().get("policy-1").decision());
    }

    @Test
    @DisplayName("incrementRetry increments retry count")
    void incrementRetryIncrementsCount() {
        ExecutionRunLedger.ExecutionRun run = ledger.createRun("pipeline-1", "tenant-1", "user-1");
        assertEquals(0, run.retryCount());

        ledger.incrementRetry(run.id());

        ExecutionRunLedger.ExecutionRun updated = store.findById(run.id()).orElseThrow();
        assertEquals(1, updated.retryCount());
    }

    @Test
    @DisplayName("completeRun sets status and completedAt")
    void completeRunSetsStatus() {
        ExecutionRunLedger.ExecutionRun run = ledger.createRun("pipeline-1", "tenant-1", "user-1");
        
        ledger.completeRun(run.id(), ExecutionRunLedger.ExecutionRunStatus.COMPLETED);

        ExecutionRunLedger.ExecutionRun updated = store.findById(run.id()).orElseThrow();
        assertEquals(ExecutionRunLedger.ExecutionRunStatus.COMPLETED, updated.status());
        assertNotNull(updated.completedAt());
        assertFalse(updated.isRunning());
        assertTrue(updated.isCompleted());
    }

    @Test
    @DisplayName("cancelRun adds log and sets CANCELLED status")
    void cancelRunCancelsRun() {
        ExecutionRunLedger.ExecutionRun run = ledger.createRun("pipeline-1", "tenant-1", "user-1");
        
        ledger.cancelRun(run.id(), "user requested");

        ExecutionRunLedger.ExecutionRun updated = store.findById(run.id()).orElseThrow();
        assertEquals(ExecutionRunLedger.ExecutionRunStatus.CANCELLED, updated.status());
        assertTrue(updated.logs().stream().anyMatch(log -> log.message().contains("cancelled")));
    }

    @Test
    @DisplayName("getRun returns run by ID")
    void getRunReturnsRun() {
        ExecutionRunLedger.ExecutionRun run = ledger.createRun("pipeline-1", "tenant-1", "user-1");

        Optional<ExecutionRunLedger.ExecutionRun> found = ledger.getRun(run.id());
        assertTrue(found.isPresent());
        assertEquals(run.id(), found.get().id());
    }

    @Test
    @DisplayName("getRunsForPipeline returns runs for pipeline")
    void getRunsForPipelineReturnsRuns() {
        ledger.createRun("pipeline-1", "tenant-1", "user-1");
        ledger.createRun("pipeline-1", "tenant-1", "user-1");
        ledger.createRun("pipeline-2", "tenant-1", "user-1");

        List<ExecutionRunLedger.ExecutionRun> runs = ledger.getRunsForPipeline("pipeline-1");
        assertEquals(2, runs.size());
    }

    @Test
    @DisplayName("durationMillis returns correct duration for completed run")
    void durationMillisReturnsCorrectDuration() {
        ExecutionRunLedger.ExecutionRun run = ledger.createRun("pipeline-1", "tenant-1", "user-1");
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        ledger.completeRun(run.id(), ExecutionRunLedger.ExecutionRunStatus.COMPLETED);

        ExecutionRunLedger.ExecutionRun updated = store.findById(run.id()).orElseThrow();
        assertTrue(updated.durationMillis() >= 10);
    }

    /**
     * Test store implementation.
     */
    private static class TestStore implements ExecutionRunLedger.ExecutionRunStore {
        private final Map<String, ExecutionRunLedger.ExecutionRun> runs = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void save(ExecutionRunLedger.ExecutionRun run) {
            runs.put(run.id(), run);
        }

        @Override
        public Optional<ExecutionRunLedger.ExecutionRun> findById(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public List<ExecutionRunLedger.ExecutionRun> findByPipelineId(String pipelineId) {
            return runs.values().stream()
                .filter(run -> run.pipelineId().equals(pipelineId))
                .toList();
        }
    }
}
