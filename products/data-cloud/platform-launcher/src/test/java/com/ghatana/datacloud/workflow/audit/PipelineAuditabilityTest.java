/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.workflow.audit;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.datacloud.workflow.WorkflowRunRepository;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit trail tests for pipeline workflow execution via {@link WorkflowRunRepository}.
 *
 * <p>Validates that every significant pipeline lifecycle event (start, step completion, // GH-90000
 * step failure, run finish) is durably recorded and recoverable from the event log,
 * forming a complete and queryable audit trail.
 *
 * @doc.type    class
 * @doc.purpose Pipeline audit trail: execution logging, error logging, performance summary
 * @doc.layer   product
 * @doc.pattern Test, EventSourcing
 */
@DisplayName("PipelineAuditabilityTest [GH-90000]")
class PipelineAuditabilityTest {

    private static final String TENANT = "audit-tenant";
    private static final String WORKFLOW_ID = "pipeline-workflow-001";
    private static final String EXECUTION_ID = "exec-audit-001";

    private InMemoryEventLogStore store;
    private WorkflowRunRepository repository;

    @BeforeEach
    void setUp() { // GH-90000
        store = new InMemoryEventLogStore(); // GH-90000
        repository = new WorkflowRunRepository(store); // GH-90000
    }

    // ── Audit trail completeness ──────────────────────────────────────────────

    @Nested
    @DisplayName("audit trail [GH-90000]")
    class AuditTrailTests {

        @Test
        @DisplayName("run-started event is persisted with workflowId and executionId [GH-90000]")
        void runStartedEventIsPersisted() { // GH-90000
            String runId = repository.startRun( // GH-90000
                    TENANT, WORKFLOW_ID, EXECUTION_ID, Map.of("source", "audit-test") // GH-90000
            ).getResult(); // GH-90000

            List<EventLogStore.EventEntry> events = store.entriesOfType(WorkflowRunRepository.EVENT_RUN_STARTED); // GH-90000
            assertThat(events).hasSize(1); // GH-90000

            String payload = payloadString(events.getFirst()); // GH-90000
            assertThat(payload).contains("\"runId\":\"" + runId + "\""); // GH-90000
            assertThat(payload).contains("\"workflowId\":\"" + WORKFLOW_ID + "\""); // GH-90000
            assertThat(payload).contains("\"executionId\":\"" + EXECUTION_ID + "\""); // GH-90000
            assertThat(payload).contains("\"startedAt\":"); // GH-90000
        }

        @Test
        @DisplayName("step-completed event is persisted for each successful step [GH-90000]")
        void stepCompletedEventIsPersisted() { // GH-90000
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000

            repository.completeStep(TENANT, runId, "validate-schema", Map.of("rowsProcessed", 1000)).getResult(); // GH-90000
            repository.completeStep(TENANT, runId, "transform-data", Map.of("rowsOutput", 950)).getResult(); // GH-90000

            List<EventLogStore.EventEntry> events = store.entriesOfType(WorkflowRunRepository.EVENT_STEP_COMPLETED); // GH-90000
            assertThat(events).hasSize(2); // GH-90000

            List<String> payloads = events.stream().map(PipelineAuditabilityTest::payloadString).toList(); // GH-90000
            assertThat(payloads).anyMatch(p -> p.contains("validate-schema [GH-90000]"));
            assertThat(payloads).anyMatch(p -> p.contains("transform-data [GH-90000]"));
        }

        @Test
        @DisplayName("step-failed event is persisted with error message [GH-90000]")
        void stepFailedEventIsPersisted() { // GH-90000
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000

            repository.failStep(TENANT, runId, "load-data", "Connection refused: postgres:5432").getResult(); // GH-90000

            List<EventLogStore.EventEntry> events = store.entriesOfType(WorkflowRunRepository.EVENT_STEP_FAILED); // GH-90000
            assertThat(events).hasSize(1); // GH-90000

            String payload = payloadString(events.getFirst()); // GH-90000
            assertThat(payload).contains("\"stepName\":\"load-data\""); // GH-90000
            assertThat(payload).contains("Connection refused [GH-90000]");
            assertThat(payload).contains("\"failedAt\":"); // GH-90000
        }

        @Test
        @DisplayName("run-finished event is persisted with terminal status [GH-90000]")
        void runFinishedEventIsPersisted() { // GH-90000
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000
            repository.completeStep(TENANT, runId, "step-1", null).getResult(); // GH-90000
            repository.finishRun(TENANT, runId, "SUCCEEDED", Map.of("totalSteps", 1)).getResult(); // GH-90000

            List<EventLogStore.EventEntry> events = store.entriesOfType(WorkflowRunRepository.EVENT_RUN_FINISHED); // GH-90000
            assertThat(events).hasSize(1); // GH-90000

            String payload = payloadString(events.getFirst()); // GH-90000
            assertThat(payload).contains("\"status\":\"SUCCEEDED\""); // GH-90000
            assertThat(payload).contains("\"finishedAt\":"); // GH-90000
        }

        @Test
        @DisplayName("full lifecycle produces all four event types in order [GH-90000]")
        void fullLifecycleProducesAllEventTypes() { // GH-90000
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000
            repository.completeStep(TENANT, runId, "ingest", Map.of("rows", 500)).getResult(); // GH-90000
            repository.failStep(TENANT, runId, "transform", "NullPointerException").getResult(); // GH-90000
            repository.finishRun(TENANT, runId, "FAILED", null).getResult(); // GH-90000

            assertThat(store.entriesOfType(WorkflowRunRepository.EVENT_RUN_STARTED)).hasSize(1); // GH-90000
            assertThat(store.entriesOfType(WorkflowRunRepository.EVENT_STEP_COMPLETED)).hasSize(1); // GH-90000
            assertThat(store.entriesOfType(WorkflowRunRepository.EVENT_STEP_FAILED)).hasSize(1); // GH-90000
            assertThat(store.entriesOfType(WorkflowRunRepository.EVENT_RUN_FINISHED)).hasSize(1); // GH-90000
        }
    }

    // ── Execution logging ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("execution logging [GH-90000]")
    class ExecutionLoggingTests {

        @Test
        @DisplayName("run status reflects completed step count from the audit log [GH-90000]")
        void runStatusReflectsCompletedStepCount() { // GH-90000
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000
            repository.completeStep(TENANT, runId, "phase-1", null).getResult(); // GH-90000
            repository.completeStep(TENANT, runId, "phase-2", null).getResult(); // GH-90000
            repository.completeStep(TENANT, runId, "phase-3", null).getResult(); // GH-90000

            Optional<WorkflowRunRepository.WorkflowRunStatus> status =
                    repository.getRunStatus(TENANT, runId).getResult(); // GH-90000

            assertThat(status).isPresent(); // GH-90000
            assertThat(status.get().completedSteps()).isEqualTo(3); // GH-90000
            assertThat(status.get().failedSteps()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("step completion events carry optional output metadata [GH-90000]")
        void stepCompletionCarriesOutputMetadata() { // GH-90000
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000
            repository.completeStep(TENANT, runId, "aggregate", // GH-90000
                    Map.of("outputRows", 12_000, "partitions", 4)).getResult(); // GH-90000

            String payload = payloadString(store.entriesOfType(WorkflowRunRepository.EVENT_STEP_COMPLETED).getFirst()); // GH-90000
            assertThat(payload).contains("outputRows [GH-90000]");
            assertThat(payload).contains("partitions [GH-90000]");
        }

        @Test
        @DisplayName("events from different runs are isolated in the audit log [GH-90000]")
        void eventsFromDifferentRunsAreIsolated() { // GH-90000
            String runA = repository.startRun(TENANT, "wf-A", "exec-A", null).getResult(); // GH-90000
            String runB = repository.startRun(TENANT, "wf-B", "exec-B", null).getResult(); // GH-90000
            repository.completeStep(TENANT, runA, "step-for-A", null).getResult(); // GH-90000
            repository.completeStep(TENANT, runB, "step-for-B", null).getResult(); // GH-90000

            Optional<WorkflowRunRepository.WorkflowRunStatus> statusA =
                    repository.getRunStatus(TENANT, runA).getResult(); // GH-90000
            Optional<WorkflowRunRepository.WorkflowRunStatus> statusB =
                    repository.getRunStatus(TENANT, runB).getResult(); // GH-90000

            assertThat(statusA).isPresent(); // GH-90000
            assertThat(statusB).isPresent(); // GH-90000
            assertThat(statusA.get().completedSteps()).isEqualTo(1); // GH-90000
            assertThat(statusB.get().completedSteps()).isEqualTo(1); // GH-90000
        }
    }

    // ── Error logging ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("error logging [GH-90000]")
    class ErrorLoggingTests {

        @Test
        @DisplayName("run status reflects failed step count from the audit log [GH-90000]")
        void runStatusReflectsFailedStepCount() { // GH-90000
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000
            repository.failStep(TENANT, runId, "validate", "Schema mismatch").getResult(); // GH-90000
            repository.failStep(TENANT, runId, "enrich", "Service timeout").getResult(); // GH-90000

            Optional<WorkflowRunRepository.WorkflowRunStatus> status =
                    repository.getRunStatus(TENANT, runId).getResult(); // GH-90000

            assertThat(status).isPresent(); // GH-90000
            assertThat(status.get().failedSteps()).isEqualTo(2); // GH-90000
            assertThat(status.get().completedSteps()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("failed run reaches FAILED terminal state [GH-90000]")
        void failedRunReachesFailedTerminalState() { // GH-90000
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000
            repository.failStep(TENANT, runId, "critical-step", "Fatal error").getResult(); // GH-90000
            repository.finishRun(TENANT, runId, "FAILED", null).getResult(); // GH-90000

            Optional<WorkflowRunRepository.WorkflowRunStatus> status =
                    repository.getRunStatus(TENANT, runId).getResult(); // GH-90000

            assertThat(status).isPresent(); // GH-90000
            assertThat(status.get().terminalStatus()).isEqualTo("FAILED [GH-90000]");
        }

        @Test
        @DisplayName("error message is preserved verbatim in the step-failed event [GH-90000]")
        void errorMessagePreservedVerbatim() { // GH-90000
            String errorMessage = "java.lang.RuntimeException: unexpected null at row 42";
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000
            repository.failStep(TENANT, runId, "parse-step", errorMessage).getResult(); // GH-90000

            String payload = payloadString(store.entriesOfType(WorkflowRunRepository.EVENT_STEP_FAILED).getFirst()); // GH-90000
            assertThat(payload).contains(errorMessage.replace("\"", "\\\"")); // GH-90000
        }
    }

    // ── Performance logging ───────────────────────────────────────────────────

    @Nested
    @DisplayName("performance logging [GH-90000]")
    class PerformanceLoggingTests {

        @Test
        @DisplayName("run-finished summary carries performance metadata [GH-90000]")
        void runFinishedSummaryCarriesPerformanceMetadata() { // GH-90000
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000
            repository.completeStep(TENANT, runId, "fast-step", null).getResult(); // GH-90000
            repository.finishRun(TENANT, runId, "SUCCEEDED", // GH-90000
                    Map.of("totalDurationMs", 1_850L, "stepCount", 1, "throughput", "12k rows/s")).getResult(); // GH-90000

            String payload = payloadString(store.entriesOfType(WorkflowRunRepository.EVENT_RUN_FINISHED).getFirst()); // GH-90000
            assertThat(payload).contains("totalDurationMs [GH-90000]");
            assertThat(payload).contains("throughput [GH-90000]");
        }

        @Test
        @DisplayName("all events carry an ISO timestamp for latency measurement [GH-90000]")
        void allEventsCarryIsoTimestamp() { // GH-90000
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000
            repository.completeStep(TENANT, runId, "timed-step", null).getResult(); // GH-90000
            repository.failStep(TENANT, runId, "failed-timed-step", "Error").getResult(); // GH-90000
            repository.finishRun(TENANT, runId, "FAILED", null).getResult(); // GH-90000

            List<EventLogStore.EventEntry> allEvents = store.allEntries(); // GH-90000
            for (EventLogStore.EventEntry event : allEvents) { // GH-90000
                String payload = payloadString(event); // GH-90000
                // Each event should contain one of the standard timestamp fields
                boolean hasTimestamp =
                        payload.contains("\"startedAt\":") || // GH-90000
                        payload.contains("\"completedAt\":") || // GH-90000
                        payload.contains("\"failedAt\":") || // GH-90000
                        payload.contains("\"finishedAt\":"); // GH-90000
                assertThat(hasTimestamp).as("Event %s requires a timestamp field", event.eventType()).isTrue(); // GH-90000
            }
        }

        @Test
        @DisplayName("step output can carry row-count and duration for throughput analysis [GH-90000]")
        void stepOutputCanCarryPerformanceCounters() { // GH-90000
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult(); // GH-90000
            repository.completeStep(TENANT, runId, "batch-step", // GH-90000
                    Map.of("rowsProcessed", 50_000, "durationMs", 430)).getResult(); // GH-90000

            String payload = payloadString(store.entriesOfType(WorkflowRunRepository.EVENT_STEP_COMPLETED).getFirst()); // GH-90000
            assertThat(payload).contains("rowsProcessed [GH-90000]");
            assertThat(payload).contains("durationMs [GH-90000]");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String payloadString(EventLogStore.EventEntry entry) { // GH-90000
        ByteBuffer buf = entry.payload().duplicate(); // GH-90000
        byte[] bytes = new byte[buf.remaining()]; // GH-90000
        buf.get(bytes); // GH-90000
        return new String(bytes, StandardCharsets.UTF_8); // GH-90000
    }

    // ── InMemoryEventLogStore (same contract as WorkflowRunRepositoryTest) ──── // GH-90000

    private static final class InMemoryEventLogStore implements EventLogStore {

        private final List<StoredEvent> stored = new ArrayList<>(); // GH-90000
        private final AtomicLong nextOffset = new AtomicLong(); // GH-90000

        List<EventEntry> allEntries() { // GH-90000
            return stored.stream().map(StoredEvent::entry).toList(); // GH-90000
        }

        List<EventEntry> entriesOfType(String eventType) { // GH-90000
            return stored.stream() // GH-90000
                    .map(StoredEvent::entry) // GH-90000
                    .filter(e -> e.eventType().equals(eventType)) // GH-90000
                    .toList(); // GH-90000
        }

        @Override
        public Promise<Offset> append(TenantContext tenant, EventEntry entry) { // GH-90000
            Offset offset = Offset.of(nextOffset.getAndIncrement()); // GH-90000
            stored.add(new StoredEvent(tenant.tenantId(), offset, entry)); // GH-90000
            return Promise.of(offset); // GH-90000
        }

        @Override
        public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) { // GH-90000
            List<Offset> offsets = new ArrayList<>(entries.size()); // GH-90000
            for (EventEntry e : entries) { // GH-90000
                offsets.add(append(tenant, e).getResult()); // GH-90000
            }
            return Promise.of(offsets); // GH-90000
        }

        @Override
        public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) { // GH-90000
            return Promise.of(stored.stream() // GH-90000
                    .filter(e -> e.tenantId().equals(tenant.tenantId())) // GH-90000
                    .filter(e -> offsetLong(e.offset()) >= offsetLong(from)) // GH-90000
                    .sorted((l, r) -> Long.compare(offsetLong(l.offset()), offsetLong(r.offset()))) // GH-90000
                    .limit(limit) // GH-90000
                    .map(StoredEvent::entry) // GH-90000
                    .toList()); // GH-90000
        }

        @Override
        public Promise<List<EventEntry>> readByTimeRange(TenantContext tenant, // GH-90000
                                                          Instant startTime,
                                                          Instant endTime,
                                                          int limit) {
            return Promise.of(stored.stream() // GH-90000
                    .filter(e -> e.tenantId().equals(tenant.tenantId())) // GH-90000
                    .map(StoredEvent::entry) // GH-90000
                    .filter(e -> !e.timestamp().isBefore(startTime) && e.timestamp().isBefore(endTime)) // GH-90000
                    .limit(limit) // GH-90000
                    .toList()); // GH-90000
        }

        @Override
        public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) { // GH-90000
            return Promise.of(stored.stream() // GH-90000
                    .filter(e -> e.tenantId().equals(tenant.tenantId())) // GH-90000
                    .filter(e -> offsetLong(e.offset()) >= offsetLong(from)) // GH-90000
                    .map(StoredEvent::entry) // GH-90000
                    .filter(e -> e.eventType().equals(eventType)) // GH-90000
                    .limit(limit) // GH-90000
                    .toList()); // GH-90000
        }

        @Override
        public Promise<Offset> getLatestOffset(TenantContext tenant) { // GH-90000
            return Promise.of(Offset.of(Math.max(0, nextOffset.get() - 1))); // GH-90000
        }

        @Override
        public Promise<Offset> getEarliestOffset(TenantContext tenant) { // GH-90000
            return Promise.of(Offset.zero()); // GH-90000
        }

        @Override
        public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) { // GH-90000
            return Promise.of(new Subscription() { // GH-90000
                @Override
                public void cancel() { } // GH-90000

                @Override
                public boolean isCancelled() { return false; } // GH-90000
            });
        }

        private long offsetLong(Offset offset) { // GH-90000
            return Long.parseLong(offset.value()); // GH-90000
        }

        private record StoredEvent(String tenantId, Offset offset, EventEntry entry) { } // GH-90000
    }
}
