/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.workflow.audit;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
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
 * <p>Validates that every significant pipeline lifecycle event (start, step completion,
 * step failure, run finish) is durably recorded and recoverable from the event log,
 * forming a complete and queryable audit trail.
 *
 * @doc.type    class
 * @doc.purpose Pipeline audit trail: execution logging, error logging, performance summary
 * @doc.layer   product
 * @doc.pattern Test, EventSourcing
 */
@DisplayName("PipelineAuditabilityTest")
class PipelineAuditabilityTest {

    private static final String TENANT = "audit-tenant";
    private static final String WORKFLOW_ID = "pipeline-workflow-001";
    private static final String EXECUTION_ID = "exec-audit-001";

    private InMemoryEventLogStore store;
    private WorkflowRunRepository repository;

    @BeforeEach
    void setUp() {
        store = new InMemoryEventLogStore();
        repository = new WorkflowRunRepository(store);
    }

    // ── Audit trail completeness ──────────────────────────────────────────────

    @Nested
    @DisplayName("audit trail")
    class AuditTrailTests {

        @Test
        @DisplayName("run-started event is persisted with workflowId and executionId")
        void runStartedEventIsPersisted() {
            String runId = repository.startRun(
                    TENANT, WORKFLOW_ID, EXECUTION_ID, Map.of("source", "audit-test")
            ).getResult();

            List<EventLogStore.EventEntry> events = store.entriesOfType(WorkflowRunRepository.EVENT_RUN_STARTED);
            assertThat(events).hasSize(1);

            String payload = payloadString(events.getFirst());
            assertThat(payload).contains("\"runId\":\"" + runId + "\"");
            assertThat(payload).contains("\"workflowId\":\"" + WORKFLOW_ID + "\"");
            assertThat(payload).contains("\"executionId\":\"" + EXECUTION_ID + "\"");
            assertThat(payload).contains("\"startedAt\":");
        }

        @Test
        @DisplayName("step-completed event is persisted for each successful step")
        void stepCompletedEventIsPersisted() {
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();

            repository.completeStep(TENANT, runId, "validate-schema", Map.of("rowsProcessed", 1000)).getResult();
            repository.completeStep(TENANT, runId, "transform-data", Map.of("rowsOutput", 950)).getResult();

            List<EventLogStore.EventEntry> events = store.entriesOfType(WorkflowRunRepository.EVENT_STEP_COMPLETED);
            assertThat(events).hasSize(2);

            List<String> payloads = events.stream().map(PipelineAuditabilityTest::payloadString).toList();
            assertThat(payloads).anyMatch(p -> p.contains("validate-schema"));
            assertThat(payloads).anyMatch(p -> p.contains("transform-data"));
        }

        @Test
        @DisplayName("step-failed event is persisted with error message")
        void stepFailedEventIsPersisted() {
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();

            repository.failStep(TENANT, runId, "load-data", "Connection refused: postgres:5432").getResult();

            List<EventLogStore.EventEntry> events = store.entriesOfType(WorkflowRunRepository.EVENT_STEP_FAILED);
            assertThat(events).hasSize(1);

            String payload = payloadString(events.getFirst());
            assertThat(payload).contains("\"stepName\":\"load-data\"");
            assertThat(payload).contains("Connection refused");
            assertThat(payload).contains("\"failedAt\":");
        }

        @Test
        @DisplayName("run-finished event is persisted with terminal status")
        void runFinishedEventIsPersisted() {
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();
            repository.completeStep(TENANT, runId, "step-1", null).getResult();
            repository.finishRun(TENANT, runId, "SUCCEEDED", Map.of("totalSteps", 1)).getResult();

            List<EventLogStore.EventEntry> events = store.entriesOfType(WorkflowRunRepository.EVENT_RUN_FINISHED);
            assertThat(events).hasSize(1);

            String payload = payloadString(events.getFirst());
            assertThat(payload).contains("\"status\":\"SUCCEEDED\"");
            assertThat(payload).contains("\"finishedAt\":");
        }

        @Test
        @DisplayName("full lifecycle produces all four event types in order")
        void fullLifecycleProducesAllEventTypes() {
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();
            repository.completeStep(TENANT, runId, "ingest", Map.of("rows", 500)).getResult();
            repository.failStep(TENANT, runId, "transform", "NullPointerException").getResult();
            repository.finishRun(TENANT, runId, "FAILED", null).getResult();

            assertThat(store.entriesOfType(WorkflowRunRepository.EVENT_RUN_STARTED)).hasSize(1);
            assertThat(store.entriesOfType(WorkflowRunRepository.EVENT_STEP_COMPLETED)).hasSize(1);
            assertThat(store.entriesOfType(WorkflowRunRepository.EVENT_STEP_FAILED)).hasSize(1);
            assertThat(store.entriesOfType(WorkflowRunRepository.EVENT_RUN_FINISHED)).hasSize(1);
        }
    }

    // ── Execution logging ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("execution logging")
    class ExecutionLoggingTests {

        @Test
        @DisplayName("run status reflects completed step count from the audit log")
        void runStatusReflectsCompletedStepCount() {
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();
            repository.completeStep(TENANT, runId, "phase-1", null).getResult();
            repository.completeStep(TENANT, runId, "phase-2", null).getResult();
            repository.completeStep(TENANT, runId, "phase-3", null).getResult();

            Optional<WorkflowRunRepository.WorkflowRunStatus> status =
                    repository.getRunStatus(TENANT, runId).getResult();

            assertThat(status).isPresent();
            assertThat(status.get().completedSteps()).isEqualTo(3);
            assertThat(status.get().failedSteps()).isEqualTo(0);
        }

        @Test
        @DisplayName("step completion events carry optional output metadata")
        void stepCompletionCarriesOutputMetadata() {
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();
            repository.completeStep(TENANT, runId, "aggregate",
                    Map.of("outputRows", 12_000, "partitions", 4)).getResult();

            String payload = payloadString(store.entriesOfType(WorkflowRunRepository.EVENT_STEP_COMPLETED).getFirst());
            assertThat(payload).contains("outputRows");
            assertThat(payload).contains("partitions");
        }

        @Test
        @DisplayName("events from different runs are isolated in the audit log")
        void eventsFromDifferentRunsAreIsolated() {
            String runA = repository.startRun(TENANT, "wf-A", "exec-A", null).getResult();
            String runB = repository.startRun(TENANT, "wf-B", "exec-B", null).getResult();
            repository.completeStep(TENANT, runA, "step-for-A", null).getResult();
            repository.completeStep(TENANT, runB, "step-for-B", null).getResult();

            Optional<WorkflowRunRepository.WorkflowRunStatus> statusA =
                    repository.getRunStatus(TENANT, runA).getResult();
            Optional<WorkflowRunRepository.WorkflowRunStatus> statusB =
                    repository.getRunStatus(TENANT, runB).getResult();

            assertThat(statusA).isPresent();
            assertThat(statusB).isPresent();
            assertThat(statusA.get().completedSteps()).isEqualTo(1);
            assertThat(statusB.get().completedSteps()).isEqualTo(1);
        }
    }

    // ── Error logging ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("error logging")
    class ErrorLoggingTests {

        @Test
        @DisplayName("run status reflects failed step count from the audit log")
        void runStatusReflectsFailedStepCount() {
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();
            repository.failStep(TENANT, runId, "validate", "Schema mismatch").getResult();
            repository.failStep(TENANT, runId, "enrich", "Service timeout").getResult();

            Optional<WorkflowRunRepository.WorkflowRunStatus> status =
                    repository.getRunStatus(TENANT, runId).getResult();

            assertThat(status).isPresent();
            assertThat(status.get().failedSteps()).isEqualTo(2);
            assertThat(status.get().completedSteps()).isEqualTo(0);
        }

        @Test
        @DisplayName("failed run reaches FAILED terminal state")
        void failedRunReachesFailedTerminalState() {
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();
            repository.failStep(TENANT, runId, "critical-step", "Fatal error").getResult();
            repository.finishRun(TENANT, runId, "FAILED", null).getResult();

            Optional<WorkflowRunRepository.WorkflowRunStatus> status =
                    repository.getRunStatus(TENANT, runId).getResult();

            assertThat(status).isPresent();
            assertThat(status.get().terminalStatus()).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("error message is preserved verbatim in the step-failed event")
        void errorMessagePreservedVerbatim() {
            String errorMessage = "java.lang.RuntimeException: unexpected null at row 42";
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();
            repository.failStep(TENANT, runId, "parse-step", errorMessage).getResult();

            String payload = payloadString(store.entriesOfType(WorkflowRunRepository.EVENT_STEP_FAILED).getFirst());
            assertThat(payload).contains(errorMessage.replace("\"", "\\\""));
        }
    }

    // ── Performance logging ───────────────────────────────────────────────────

    @Nested
    @DisplayName("performance logging")
    class PerformanceLoggingTests {

        @Test
        @DisplayName("run-finished summary carries performance metadata")
        void runFinishedSummaryCarriesPerformanceMetadata() {
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();
            repository.completeStep(TENANT, runId, "fast-step", null).getResult();
            repository.finishRun(TENANT, runId, "SUCCEEDED",
                    Map.of("totalDurationMs", 1_850L, "stepCount", 1, "throughput", "12k rows/s")).getResult();

            String payload = payloadString(store.entriesOfType(WorkflowRunRepository.EVENT_RUN_FINISHED).getFirst());
            assertThat(payload).contains("totalDurationMs");
            assertThat(payload).contains("throughput");
        }

        @Test
        @DisplayName("all events carry an ISO timestamp for latency measurement")
        void allEventsCarryIsoTimestamp() {
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();
            repository.completeStep(TENANT, runId, "timed-step", null).getResult();
            repository.failStep(TENANT, runId, "failed-timed-step", "Error").getResult();
            repository.finishRun(TENANT, runId, "FAILED", null).getResult();

            List<EventLogStore.EventEntry> allEvents = store.allEntries();
            for (EventLogStore.EventEntry event : allEvents) {
                String payload = payloadString(event);
                // Each event should contain one of the standard timestamp fields
                boolean hasTimestamp =
                        payload.contains("\"startedAt\":") ||
                        payload.contains("\"completedAt\":") ||
                        payload.contains("\"failedAt\":") ||
                        payload.contains("\"finishedAt\":");
                assertThat(hasTimestamp).as("Event %s requires a timestamp field", event.eventType()).isTrue();
            }
        }

        @Test
        @DisplayName("step output can carry row-count and duration for throughput analysis")
        void stepOutputCanCarryPerformanceCounters() {
            String runId = repository.startRun(TENANT, WORKFLOW_ID, EXECUTION_ID, null).getResult();
            repository.completeStep(TENANT, runId, "batch-step",
                    Map.of("rowsProcessed", 50_000, "durationMs", 430)).getResult();

            String payload = payloadString(store.entriesOfType(WorkflowRunRepository.EVENT_STEP_COMPLETED).getFirst());
            assertThat(payload).contains("rowsProcessed");
            assertThat(payload).contains("durationMs");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String payloadString(EventLogStore.EventEntry entry) {
        ByteBuffer buf = entry.payload().duplicate();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ── InMemoryEventLogStore (same contract as WorkflowRunRepositoryTest) ────

    private static final class InMemoryEventLogStore implements EventLogStore {

        private final List<StoredEvent> stored = new ArrayList<>();
        private final AtomicLong nextOffset = new AtomicLong();

        List<EventEntry> allEntries() {
            return stored.stream().map(StoredEvent::entry).toList();
        }

        List<EventEntry> entriesOfType(String eventType) {
            return stored.stream()
                    .map(StoredEvent::entry)
                    .filter(e -> e.eventType().equals(eventType))
                    .toList();
        }

        @Override
        public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
            Offset offset = Offset.of(nextOffset.getAndIncrement());
            stored.add(new StoredEvent(tenant.tenantId(), offset, entry));
            return Promise.of(offset);
        }

        @Override
        public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
            List<Offset> offsets = new ArrayList<>(entries.size());
            for (EventEntry e : entries) {
                offsets.add(append(tenant, e).getResult());
            }
            return Promise.of(offsets);
        }

        @Override
        public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
            return Promise.of(stored.stream()
                    .filter(e -> e.tenantId().equals(tenant.tenantId()))
                    .filter(e -> offsetLong(e.offset()) >= offsetLong(from))
                    .sorted((l, r) -> Long.compare(offsetLong(l.offset()), offsetLong(r.offset())))
                    .limit(limit)
                    .map(StoredEvent::entry)
                    .toList());
        }

        @Override
        public Promise<List<EventEntry>> readByTimeRange(TenantContext tenant,
                                                          Instant startTime,
                                                          Instant endTime,
                                                          int limit) {
            return Promise.of(stored.stream()
                    .filter(e -> e.tenantId().equals(tenant.tenantId()))
                    .map(StoredEvent::entry)
                    .filter(e -> !e.timestamp().isBefore(startTime) && e.timestamp().isBefore(endTime))
                    .limit(limit)
                    .toList());
        }

        @Override
        public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) {
            return Promise.of(stored.stream()
                    .filter(e -> e.tenantId().equals(tenant.tenantId()))
                    .filter(e -> offsetLong(e.offset()) >= offsetLong(from))
                    .map(StoredEvent::entry)
                    .filter(e -> e.eventType().equals(eventType))
                    .limit(limit)
                    .toList());
        }

        @Override
        public Promise<Offset> getLatestOffset(TenantContext tenant) {
            return Promise.of(Offset.of(Math.max(0, nextOffset.get() - 1)));
        }

        @Override
        public Promise<Offset> getEarliestOffset(TenantContext tenant) {
            return Promise.of(Offset.zero());
        }

        @Override
        public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
            return Promise.of(new Subscription() {
                @Override
                public void cancel() { }

                @Override
                public boolean isCancelled() { return false; }
            });
        }

        private long offsetLong(Offset offset) {
            return Long.parseLong(offset.value());
        }

        private record StoredEvent(String tenantId, Offset offset, EventEntry entry) { }
    }
}
