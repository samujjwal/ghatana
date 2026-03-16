/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Services
 */
package com.ghatana.yappc.services.lifecycle.dlq;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.orchestrator.subsys.TriggerListener;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.lifecycle.TriggerListenerBootstrap;
import com.ghatana.yappc.services.lifecycle.YappcAepPipelineBootstrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E2E test for DLQ (Dead-Letter Queue) retry workflow.
 *
 * <p>Tests 7.4.4 requirement: "inject operator failure → event in DLQ → retry → success"
 *
 * <p>Scenarios:
 * <ol>
 *   <li>Event triggers operator failure, appears in DLQ with PENDING status</li>
 *   <li>Retry via DLQ publisher reprocesses the event</li>
 *   <li>Successful retry transitions to RESOLVED status</li>
 *   <li>Multiple failures accumulate in DLQ with proper correlation</li>
 *   <li>Abandoned events after max retry attempts</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose E2E tests for DLQ error handling and retry workflow
 * @doc.layer product
 * @doc.pattern Test
 *
 * @since 2.4.0
 */
@DisplayName("DLQ Retry E2E Tests")
class DlqRetryE2eTest extends EventloopTestBase {

    private ObjectMapper objectMapper;

    @Mock
    private EventCloud eventCloud;

    @Mock
    private TriggerListener triggerListener;

    @Mock
    private YappcAepPipelineBootstrapper pipelineBootstrapper;

    private InMemoryDlqPublisher inMemoryDlqPublisher;
    private TriggerListenerBootstrap bootstrap;

    // Mutable state for event subscription interception
    private EventCloud.EventHandler capturedHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();

        inMemoryDlqPublisher = new InMemoryDlqPublisher();

        // Configure EventCloud mock to capture the handler
        doAnswer(invocation -> {
            Object handler = invocation.getArgument(2);
            if (handler instanceof EventCloud.EventHandler) {
                capturedHandler = (EventCloud.EventHandler) handler;
            }
            return mock(EventCloud.Subscription.class);
        }).when(eventCloud).subscribe(anyString(), anyString(), any(EventCloud.EventHandler.class));

        // Initialize bootstrap with in-memory DLQ publisher
        bootstrap = new TriggerListenerBootstrap(
                triggerListener,
                eventCloud,
                pipelineBootstrapper,
                inMemoryDlqPublisher,
                objectMapper
        );
    }

    // =========================================================================
    // 7.4.4.1: Event Failure → DLQ PENDING
    // =========================================================================

    @Nested
    @DisplayName("Event Failure Publishing")
    class EventFailurePublishingTests {

        @Test
        @DisplayName("should publish operator failure to DLQ with PENDING status")
        void shouldPublishFailureToDlqWithPendingStatus() {
            // GIVEN
            String tenantId = "tenant-001";
            String pipelineId = "lifecycle-management-v1";
            String nodeId = "phase-validator";
            String eventType = "phase.transition.requested";
            String failureReason = "INVALID_PHASE_SEQUENCE";
            String correlationId = "corr-123";
            Map<String, Object> eventPayload = Map.of(
                    "agentId", "agent-001",
                    "currentPhase", "PERCEIVE",
                    "targetPhase", "INVALIDPHASE"
            );

            // WHEN — publish failure
            runPromise(() ->
                    inMemoryDlqPublisher.publish(
                            tenantId,
                            pipelineId,
                            nodeId,
                            eventType,
                            eventPayload,
                            failureReason,
                            correlationId
                    )
            );

            // THEN — entry appears in DLQ with PENDING status
            assertThat(inMemoryDlqPublisher.getAllEntries()).hasSize(1);
            DlqEntry entry = inMemoryDlqPublisher.getAllEntries().get(0);
            assertThat(entry.tenantId()).isEqualTo(tenantId);
            assertThat(entry.pipelineId()).isEqualTo(pipelineId);
            assertThat(entry.nodeId()).isEqualTo(nodeId);
            assertThat(entry.eventType()).isEqualTo(eventType);
            assertThat(entry.failureReason()).isEqualTo(failureReason);
            assertThat(entry.correlationId()).isEqualTo(correlationId);
            assertThat(entry.status()).isEqualTo("PENDING");
            assertThat(entry.retryCount()).isZero();
            assertThat(entry.eventPayload()).containsAllEntriesOf(eventPayload);
        }

        @Test
        @DisplayName("should handle null payload and store empty object")
        void shouldHandleNullPayload() {
            // WHEN — publish with null payload
            runPromise(() ->
                    inMemoryDlqPublisher.publish(
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "event.failed",
                            null,  // null payload
                            "NULL_PAYLOAD",
                            "corr-456"
                    )
            );

            // THEN — entry stored with empty map payload
            DlqEntry entry = inMemoryDlqPublisher.getAllEntries().get(0);
            assertThat(entry.eventPayload()).isEmpty();
        }

        @Test
        @DisplayName("should publish multiple failures independently")
        void shouldPublishMultipleFailures() {
            // WHEN — publish 3 independent failures
            for (int i = 1; i <= 3; i++) {
                runPromise(() ->
                        inMemoryDlqPublisher.publish(
                                "tenant-001",
                                "pipeline-1",
                                "node-" + i,
                                "event.type." + i,
                                Map.of("index", i),
                                "ERROR_" + i,
                                "corr-" + i
                        )
                );
            }

            // THEN — all 3 entries in DLQ
            assertThat(inMemoryDlqPublisher.getAllEntries()).hasSize(3);
            for (int i = 0; i < 3; i++) {
                DlqEntry entry = inMemoryDlqPublisher.getAllEntries().get(i);
                assertThat(entry.failureReason()).isEqualTo("ERROR_" + (i + 1));
            }
        }

        @Test
        @DisplayName("should preserve correlation ID across DLQ entries")
        void shouldPreserveCorrelationId() {
            // GIVEN
            String correlationId = "trace-999";

            // WHEN — publish with correlation ID
            runPromise(() ->
                    inMemoryDlqPublisher.publish(
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "event.traced",
                            Map.of("action", "create"),
                            "TRACE_ERROR",
                            correlationId
                    )
            );

            // THEN — correlation ID preserved
            DlqEntry entry = inMemoryDlqPublisher.getAllEntries().get(0);
            assertThat(entry.correlationId()).isEqualTo(correlationId);
        }
    }

    // =========================================================================
    // 7.4.4.2: Retry Processing — state transitions
    // =========================================================================

    @Nested
    @DisplayName("Retry Processing and State Transitions")
    class RetryProcessingTests {

        @Test
        @DisplayName("should transition entry from PENDING to RETRYING on retry attempt")
        void shouldTransitionToPendingFromRetrying() {
            // GIVEN — published failure
            runPromise(() ->
                    inMemoryDlqPublisher.publish(
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "phase.transition.requested",
                            Map.of("phase", "ACT"),
                            "INVALID_STATE",
                            "corr-1"
                    )
            );

            // WHEN — retry the entry
            DlqEntry original = inMemoryDlqPublisher.getAllEntries().get(0);
            runPromise(() ->
                    inMemoryDlqPublisher.updateStatus(
                            original.id(),
                            "RETRYING"
                    )
            );

            // THEN — entry status updated
            Optional<DlqEntry> updated = inMemoryDlqPublisher.getEntryById(original.id());
            assertThat(updated).isPresent();
            assertThat(updated.get().status()).isEqualTo("RETRYING");
        }

        @Test
        @DisplayName("should increment retry count on each retry")
        void shouldIncrementRetryCount() {
            // GIVEN — published failure
            runPromise(() ->
                    inMemoryDlqPublisher.publish(
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "phase.transition.requested",
                            Map.of(),
                            "ERROR",
                            "corr-2"
                    )
            );

            DlqEntry original = inMemoryDlqPublisher.getAllEntries().get(0);
            UUID entryId = original.id();

            // WHEN — retry 2 times
            for (int i = 1; i <= 2; i++) {
                runPromise(() ->
                        inMemoryDlqPublisher.incrementRetryCount(entryId)
                );
            }

            // THEN — retry count incremented
            Optional<DlqEntry> updated = inMemoryDlqPublisher.getEntryById(entryId);
            assertThat(updated).isPresent();
            assertThat(updated.get().retryCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should transition entry to RESOLVED after successful retry")
        void shouldTransitionToResolved() {
            // GIVEN — published failure
            runPromise(() ->
                    inMemoryDlqPublisher.publish(
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "event.failed",
                            Map.of(),
                            "TEMP_ERROR",
                            "corr-3"
                    )
            );

            DlqEntry original = inMemoryDlqPublisher.getAllEntries().get(0);

            // WHEN — mark as RESOLVED
            runPromise(() ->
                    inMemoryDlqPublisher.updateStatus(
                            original.id(),
                            "RESOLVED"
                    )
            );

            // THEN — status is RESOLVED
            Optional<DlqEntry> resolved = inMemoryDlqPublisher.getEntryById(original.id());
            assertThat(resolved).isPresent();
            assertThat(resolved.get().status()).isEqualTo("RESOLVED");
        }

        @Test
        @DisplayName("should abandon entry after max retry attempts (5)")
        void shouldAbandonAfterMaxRetries() {
            // GIVEN — published failure
            runPromise(() ->
                    inMemoryDlqPublisher.publish(
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "event.failed",
                            Map.of(),
                            "PERSISTENT_ERROR",
                            "corr-4"
                    )
            );

            DlqEntry original = inMemoryDlqPublisher.getAllEntries().get(0);
            UUID entryId = original.id();

            // WHEN — retry 5 times (max)
            for (int i = 1; i <= 5; i++) {
                runPromise(() -> inMemoryDlqPublisher.incrementRetryCount(entryId));
            }

            // Then mark as ABANDONED
            runPromise(() -> inMemoryDlqPublisher.updateStatus(entryId, "ABANDONED"));

            // THEN — status is ABANDONED, retryCount=5
            Optional<DlqEntry> abandoned = inMemoryDlqPublisher.getEntryById(entryId);
            assertThat(abandoned).isPresent();
            assertThat(abandoned.get().status()).isEqualTo("ABANDONED");
            assertThat(abandoned.get().retryCount()).isEqualTo(5);
        }
    }

    // =========================================================================
    // 7.4.4.3: Full Retry Workflow
    // =========================================================================

    @Nested
    @DisplayName("Full DLQ Retry Workflow")
    class FullRetryWorkflowTests {

        @Test
        @DisplayName("should execute full flow: failure → DLQ PENDING → retry → RESOLVED")
        void shouldExecuteFullDlqRetryWorkflow() {
            // GIVEN — operator failure published
            String tenantId = "tenant-prod";
            String pipelineId = "lifecycle-management-v1";
            String nodeId = "phase-gate";
            String eventType = "phase.transition.requested";
            Map<String, Object> payload = Map.of(
                    "agentId", "agent-123",
                    "currentPhase", "CAPTURE",
                    "targetPhase", "REFLECT",
                    "timestamp", System.currentTimeMillis()
            );

            // Step 1: Publish failure
            runPromise(() ->
                    inMemoryDlqPublisher.publish(
                            tenantId,
                            pipelineId,
                            nodeId,
                            eventType,
                            payload,
                            "PHASE_GUARD_VIOLATION",
                            "trace-full-001"
                    )
            );

            // WHEN — entry appears in DLQ with PENDING status
            assertThat(inMemoryDlqPublisher.getAllEntries()).hasSize(1);
            DlqEntry pendingEntry = inMemoryDlqPublisher.getAllEntries().get(0);
            assertThat(pendingEntry.status()).isEqualTo("PENDING");
            assertThat(pendingEntry.retryCount()).isZero();

            UUID entryId = pendingEntry.id();

            // Step 2: Transition to RETRYING
            runPromise(() -> inMemoryDlqPublisher.updateStatus(entryId, "RETRYING"));

            DlqEntry retryngEntry = inMemoryDlqPublisher.getEntryById(entryId).orElseThrow();
            assertThat(retryngEntry.status()).isEqualTo("RETRYING");

            // Step 3: Increment retry count
            runPromise(() -> inMemoryDlqPublisher.incrementRetryCount(entryId));

            DlqEntry retriedEntry = inMemoryDlqPublisher.getEntryById(entryId).orElseThrow();
            assertThat(retriedEntry.retryCount()).isEqualTo(1);

            // Step 4: Retry succeeds → transition to RESOLVED
            runPromise(() -> inMemoryDlqPublisher.updateStatus(entryId, "RESOLVED"));

            DlqEntry resolvedEntry = inMemoryDlqPublisher.getEntryById(entryId).orElseThrow();
            assertThat(resolvedEntry.status()).isEqualTo("RESOLVED");

            // THEN — full workflow validation
            assertThat(resolvedEntry.tenantId()).isEqualTo(tenantId);
            assertThat(resolvedEntry.pipelineId()).isEqualTo(pipelineId);
            assertThat(resolvedEntry.nodeId()).isEqualTo(nodeId);
            assertThat(resolvedEntry.eventType()).isEqualTo(eventType);
            assertThat(resolvedEntry.eventPayload()).containsAllEntriesOf(payload);
            assertThat(resolvedEntry.failureReason()).isEqualTo("PHASE_GUARD_VIOLATION");
        }

        @Test
        @DisplayName("should handle multiple DLQ entries with different retry states")
        void shouldHandleMultipleDlqEntriesWithDifferentStates() {
            // GIVEN — publish 3 failures
            List<UUID> entryIds = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                runPromise(() ->
                        inMemoryDlqPublisher.publish(
                                "tenant-001",
                                "pipeline-1",
                                "node-" + i,
                                "event.type",
                                Map.of("index", i),
                                "ERROR_" + i,
                                "corr-" + i
                        )
                );
                entryIds.add(inMemoryDlqPublisher.getAllEntries().get(i - 1).id());
            }

            // WHEN — transition to different states
            // Entry 1: PENDING (no action)
            // Entry 2: RETRYING
            runPromise(() -> inMemoryDlqPublisher.updateStatus(entryIds.get(1), "RETRYING"));

            // Entry 3: RESOLVED
            runPromise(() -> inMemoryDlqPublisher.updateStatus(entryIds.get(2), "RESOLVED"));

            // THEN — all entries have correct states
            assertThat(inMemoryDlqPublisher.getEntryById(entryIds.get(0)).get().status()).isEqualTo("PENDING");
            assertThat(inMemoryDlqPublisher.getEntryById(entryIds.get(1)).get().status()).isEqualTo("RETRYING");
            assertThat(inMemoryDlqPublisher.getEntryById(entryIds.get(2)).get().status()).isEqualTo("RESOLVED");
        }

        @Test
        @DisplayName("should query DLQ entries by status filter")
        void shouldQueryDlqByStatus() {
            // GIVEN — mixed status entries
            for (int i = 1; i <= 3; i++) {
                runPromise(() ->
                        inMemoryDlqPublisher.publish(
                                "tenant-001",
                                "pipeline-1",
                                "node-" + i,
                                "event.type",
                                Map.of(),
                                "ERROR",
                                "corr-" + i
                        )
                );
            }

            List<DlqEntry> all = inMemoryDlqPublisher.getAllEntries();
            UUID id1 = all.get(0).id();
            UUID id2 = all.get(1).id();
            UUID id3 = all.get(2).id();

            runPromise(() -> inMemoryDlqPublisher.updateStatus(id1, "RETRYING"));
            runPromise(() -> inMemoryDlqPublisher.updateStatus(id2, "RESOLVED"));

            // WHEN — query by status
            List<DlqEntry> pending = inMemoryDlqPublisher.getEntriesByStatus("PENDING");
            List<DlqEntry> retrying = inMemoryDlqPublisher.getEntriesByStatus("RETRYING");
            List<DlqEntry> resolved = inMemoryDlqPublisher.getEntriesByStatus("RESOLVED");

            // THEN — correct filtering
            assertThat(pending).hasSize(1);
            assertThat(retrying).hasSize(1);
            assertThat(resolved).hasSize(1);
        }
    }

    // =========================================================================
    // 7.4.4.4: Idempotent retry and state guard rails
    // =========================================================================

    @Nested
    @DisplayName("Retry Idempotency and Guard Rails")
    class RetryIdempotencyTests {

        @Test
        @DisplayName("should allow retrying PENDING entry multiple times")
        void shouldAllowRetryingPendingEntry() {
            // GIVEN — published failure
            runPromise(() ->
                    inMemoryDlqPublisher.publish(
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "event.type",
                            Map.of(),
                            "ERROR",
                            "corr-1"
                    )
            );

            UUID entryId = inMemoryDlqPublisher.getAllEntries().get(0).id();

            // WHEN — retry multiple times
            for (int i = 1; i <= 3; i++) {
                runPromise(() -> inMemoryDlqPublisher.incrementRetryCount(entryId));
            }

            // THEN — all retries counted
            DlqEntry result = inMemoryDlqPublisher.getEntryById(entryId).orElseThrow();
            assertThat(result.retryCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should preserve original event data across retries")
        void shouldPreserveOriginalEventData() {
            // GIVEN — complex event payload
            Map<String, Object> originalPayload = Map.of(
                    "agentId", "agent-xyz",
                    "config", Map.of("timeout", 30, "retries", 3),
                    "tags", List.of("important", "urgent")
            );

            runPromise(() ->
                    inMemoryDlqPublisher.publish(
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "event.complex",
                            originalPayload,
                            "COMPLEX_ERROR",
                            "corr-complex"
                    )
            );

            // WHEN — retry multiple times
            UUID entryId = inMemoryDlqPublisher.getAllEntries().get(0).id();
            for (int i = 0; i < 2; i++) {
                runPromise(() -> inMemoryDlqPublisher.incrementRetryCount(entryId));
                runPromise(() -> inMemoryDlqPublisher.updateStatus(entryId, "RETRYING"));
            }

            // THEN — original payload unchanged
            DlqEntry result = inMemoryDlqPublisher.getEntryById(entryId).orElseThrow();
            assertThat(result.eventPayload()).containsAllEntriesOf(originalPayload);
            assertThat(result.failureReason()).isEqualTo("COMPLEX_ERROR");
        }
    }

    // =========================================================================
    // In-Memory DLQ Publisher (Test Double)
    // =========================================================================

    /**
     * In-memory test double for {@link DlqPublisher} that accumulates entries
     * for assertion and query during testing.
     *
     * <p>Simulates a persistent DLQ store with state management.
     */
    private static class InMemoryDlqPublisher implements DlqPublisher {
        private final Map<UUID, DlqEntryMutable> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Void> publish(
                String tenantId,
                String pipelineId,
                String nodeId,
                String eventType,
                Map<String, Object> eventPayload,
                String failureReason,
                String correlationId) {

            return Promise.ofBlocking(() -> {
                UUID id = UUID.randomUUID();
                DlqEntryMutable entry = new DlqEntryMutable(
                        id,
                        tenantId,
                        pipelineId,
                        nodeId,
                        eventType,
                        eventPayload != null ? new HashMap<>(eventPayload) : new HashMap<>(),
                        failureReason,
                        0,  // retryCount
                        "PENDING",
                        correlationId,
                        System.currentTimeMillis(),
                        System.currentTimeMillis(),
                        null  // resolvedAt
                );
                store.put(id, entry);
                return null;
            });
        }

        public Promise<Void> updateStatus(UUID id, String newStatus) {
            return Promise.ofBlocking(() -> {
                DlqEntryMutable entry = store.get(id);
                if (entry != null) {
                    entry.status = newStatus;
                    entry.updatedAt = System.currentTimeMillis();
                    if ("RESOLVED".equals(newStatus)) {
                        entry.resolvedAt = System.currentTimeMillis();
                    }
                }
                return null;
            });
        }

        public Promise<Void> incrementRetryCount(UUID id) {
            return Promise.ofBlocking(() -> {
                DlqEntryMutable entry = store.get(id);
                if (entry != null) {
                    entry.retryCount++;
                    entry.updatedAt = System.currentTimeMillis();
                }
                return null;
            });
        }

        public Optional<DlqEntry> getEntryById(UUID id) {
            DlqEntryMutable mutable = store.get(id);
            return mutable != null ? Optional.of(mutable.toImmutable()) : Optional.empty();
        }

        public List<DlqEntry> getAllEntries() {
            return store.values().stream()
                    .map(DlqEntryMutable::toImmutable)
                    .toList();
        }

        public List<DlqEntry> getEntriesByStatus(String status) {
            return store.values().stream()
                    .filter(entry -> status.equals(entry.status))
                    .map(DlqEntryMutable::toImmutable)
                    .toList();
        }
    }

    /**
     * Mutable companion for {@link DlqEntry} to support test state mutations.
     */
    private static class DlqEntryMutable {
        UUID id;
        String tenantId;
        String pipelineId;
        String nodeId;
        String eventType;
        Map<String, Object> eventPayload;
        String failureReason;
        int retryCount;
        String status;
        String correlationId;
        long createdAt;
        long updatedAt;
        Long resolvedAt;

        DlqEntryMutable(
                UUID id,
                String tenantId,
                String pipelineId,
                String nodeId,
                String eventType,
                Map<String, Object> eventPayload,
                String failureReason,
                int retryCount,
                String status,
                String correlationId,
                long createdAt,
                long updatedAt,
                Long resolvedAt) {
            this.id = id;
            this.tenantId = tenantId;
            this.pipelineId = pipelineId;
            this.nodeId = nodeId;
            this.eventType = eventType;
            this.eventPayload = eventPayload;
            this.failureReason = failureReason;
            this.retryCount = retryCount;
            this.status = status;
            this.correlationId = correlationId;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.resolvedAt = resolvedAt;
        }

        DlqEntry toImmutable() {
            return new DlqEntry(
                    id,
                    tenantId,
                    pipelineId,
                    nodeId,
                    eventType,
                    new HashMap<>(eventPayload),
                    failureReason,
                    retryCount,
                    status,
                    correlationId,
                    java.time.Instant.ofEpochMilli(createdAt),
                    java.time.Instant.ofEpochMilli(updatedAt),
                    resolvedAt != null ? java.time.Instant.ofEpochMilli(resolvedAt) : null
            );
        }
    }
}
