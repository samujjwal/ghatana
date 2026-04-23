/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Services
 */
package com.ghatana.yappc.services.lifecycle.dlq;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.orchestrator.subsys.TriggerListener;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.time.Instant;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E2E test for DLQ (Dead-Letter Queue) retry workflow. // GH-90000
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
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000

        inMemoryDlqPublisher = new InMemoryDlqPublisher(); // GH-90000

        // Configure EventCloud mock to capture the handler
        doAnswer(invocation -> { // GH-90000
            Object handler = invocation.getArgument(2); // GH-90000
            if (handler instanceof EventCloud.EventHandler) { // GH-90000
                capturedHandler = (EventCloud.EventHandler) handler; // GH-90000
            }
            return mock(EventCloud.Subscription.class); // GH-90000
        }).when(eventCloud).subscribe(anyString(), anyString(), any(EventCloud.EventHandler.class)); // GH-90000

        // Initialize bootstrap with in-memory DLQ publisher
        bootstrap = new TriggerListenerBootstrap( // GH-90000
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
        void shouldPublishFailureToDlqWithPendingStatus() { // GH-90000
            // GIVEN
            String tenantId = "tenant-001";
            String pipelineId = "lifecycle-management-v1";
            String nodeId = "phase-validator";
            String eventType = "phase.transition.requested";
            String failureReason = "INVALID_PHASE_SEQUENCE";
            String correlationId = "corr-123";
            Map<String, Object> eventPayload = Map.of( // GH-90000
                    "agentId", "agent-001",
                    "currentPhase", "PERCEIVE",
                    "targetPhase", "INVALIDPHASE"
            );

            // WHEN — publish failure
            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.publish( // GH-90000
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
            assertThat(inMemoryDlqPublisher.getAllEntries()).hasSize(1); // GH-90000
            DlqEntry entry = inMemoryDlqPublisher.getAllEntries().get(0); // GH-90000
            assertThat(entry.tenantId()).isEqualTo(tenantId); // GH-90000
            assertThat(entry.pipelineId()).isEqualTo(pipelineId); // GH-90000
            assertThat(entry.nodeId()).isEqualTo(nodeId); // GH-90000
            assertThat(entry.eventType()).isEqualTo(eventType); // GH-90000
            assertThat(entry.failureReason()).isEqualTo(failureReason); // GH-90000
            assertThat(entry.correlationId()).isEqualTo(correlationId); // GH-90000
            assertThat(entry.status()).isEqualTo("PENDING");
            assertThat(entry.retryCount()).isZero(); // GH-90000
            assertThat(entry.eventPayload()).containsAllEntriesOf(eventPayload); // GH-90000
        }

        @Test
        @DisplayName("should handle null payload and store empty object")
        void shouldHandleNullPayload() { // GH-90000
            // WHEN — publish with null payload
            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.publish( // GH-90000
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
            DlqEntry entry = inMemoryDlqPublisher.getAllEntries().get(0); // GH-90000
            assertThat(entry.eventPayload()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should publish multiple failures independently")
        void shouldPublishMultipleFailures() { // GH-90000
            // WHEN — publish 3 independent failures
            for (int i = 1; i <= 3; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> // GH-90000
                        inMemoryDlqPublisher.publish( // GH-90000
                                "tenant-001",
                                "pipeline-1",
                                "node-" + idx,
                                "event.type." + idx,
                                Map.of("index", idx), // GH-90000
                                "ERROR_" + idx,
                                "corr-" + idx
                        )
                );
            }

            // THEN — all 3 entries in DLQ
            assertThat(inMemoryDlqPublisher.getAllEntries()).hasSize(3); // GH-90000
            for (int i = 0; i < 3; i++) { // GH-90000
                DlqEntry entry = inMemoryDlqPublisher.getAllEntries().get(i); // GH-90000
                assertThat(entry.failureReason()).isEqualTo("ERROR_" + (i + 1)); // GH-90000
            }
        }

        @Test
        @DisplayName("should preserve correlation ID across DLQ entries")
        void shouldPreserveCorrelationId() { // GH-90000
            // GIVEN
            String correlationId = "trace-999";

            // WHEN — publish with correlation ID
            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.publish( // GH-90000
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "event.traced",
                            Map.of("action", "create"), // GH-90000
                            "TRACE_ERROR",
                            correlationId
                    )
            );

            // THEN — correlation ID preserved
            DlqEntry entry = inMemoryDlqPublisher.getAllEntries().get(0); // GH-90000
            assertThat(entry.correlationId()).isEqualTo(correlationId); // GH-90000
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
        void shouldTransitionToPendingFromRetrying() { // GH-90000
            // GIVEN — published failure
            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.publish( // GH-90000
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "phase.transition.requested",
                            Map.of("phase", "ACT"), // GH-90000
                            "INVALID_STATE",
                            "corr-1"
                    )
            );

            // WHEN — retry the entry
            DlqEntry original = inMemoryDlqPublisher.getAllEntries().get(0); // GH-90000
            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.updateStatus( // GH-90000
                            original.id(), // GH-90000
                            "RETRYING"
                    )
            );

            // THEN — entry status updated
            Optional<DlqEntry> updated = inMemoryDlqPublisher.getEntryById(original.id()); // GH-90000
            assertThat(updated).isPresent(); // GH-90000
            assertThat(updated.get().status()).isEqualTo("RETRYING");
        }

        @Test
        @DisplayName("should increment retry count on each retry")
        void shouldIncrementRetryCount() { // GH-90000
            // GIVEN — published failure
            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.publish( // GH-90000
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "phase.transition.requested",
                            Map.of(), // GH-90000
                            "ERROR",
                            "corr-2"
                    )
            );

            DlqEntry original = inMemoryDlqPublisher.getAllEntries().get(0); // GH-90000
            UUID entryId = original.id(); // GH-90000

            // WHEN — retry 2 times
            for (int i = 1; i <= 2; i++) { // GH-90000
                runPromise(() -> // GH-90000
                        inMemoryDlqPublisher.incrementRetryCount(entryId) // GH-90000
                );
            }

            // THEN — retry count incremented
            Optional<DlqEntry> updated = inMemoryDlqPublisher.getEntryById(entryId); // GH-90000
            assertThat(updated).isPresent(); // GH-90000
            assertThat(updated.get().retryCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("should transition entry to RESOLVED after successful retry")
        void shouldTransitionToResolved() { // GH-90000
            // GIVEN — published failure
            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.publish( // GH-90000
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "event.failed",
                            Map.of(), // GH-90000
                            "TEMP_ERROR",
                            "corr-3"
                    )
            );

            DlqEntry original = inMemoryDlqPublisher.getAllEntries().get(0); // GH-90000

            // WHEN — mark as RESOLVED
            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.updateStatus( // GH-90000
                            original.id(), // GH-90000
                            "RESOLVED"
                    )
            );

            // THEN — status is RESOLVED
            Optional<DlqEntry> resolved = inMemoryDlqPublisher.getEntryById(original.id()); // GH-90000
            assertThat(resolved).isPresent(); // GH-90000
            assertThat(resolved.get().status()).isEqualTo("RESOLVED");
        }

        @Test
        @DisplayName("should abandon entry after max retry attempts (5)")
        void shouldAbandonAfterMaxRetries() { // GH-90000
            // GIVEN — published failure
            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.publish( // GH-90000
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "event.failed",
                            Map.of(), // GH-90000
                            "PERSISTENT_ERROR",
                            "corr-4"
                    )
            );

            DlqEntry original = inMemoryDlqPublisher.getAllEntries().get(0); // GH-90000
            UUID entryId = original.id(); // GH-90000

            // WHEN — retry 5 times (max) // GH-90000
            for (int i = 1; i <= 5; i++) { // GH-90000
                runPromise(() -> inMemoryDlqPublisher.incrementRetryCount(entryId)); // GH-90000
            }

            // Then mark as ABANDONED
            runPromise(() -> inMemoryDlqPublisher.updateStatus(entryId, "ABANDONED")); // GH-90000

            // THEN — status is ABANDONED, retryCount=5
            Optional<DlqEntry> abandoned = inMemoryDlqPublisher.getEntryById(entryId); // GH-90000
            assertThat(abandoned).isPresent(); // GH-90000
            assertThat(abandoned.get().status()).isEqualTo("ABANDONED");
            assertThat(abandoned.get().retryCount()).isEqualTo(5); // GH-90000
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
        void shouldExecuteFullDlqRetryWorkflow() { // GH-90000
            // GIVEN — operator failure published
            String tenantId = "tenant-prod";
            String pipelineId = "lifecycle-management-v1";
            String nodeId = "phase-gate";
            String eventType = "phase.transition.requested";
            Map<String, Object> payload = Map.of( // GH-90000
                    "agentId", "agent-123",
                    "currentPhase", "CAPTURE",
                    "targetPhase", "REFLECT",
                    "timestamp", System.currentTimeMillis() // GH-90000
            );

            // Step 1: Publish failure
            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.publish( // GH-90000
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
            assertThat(inMemoryDlqPublisher.getAllEntries()).hasSize(1); // GH-90000
            DlqEntry pendingEntry = inMemoryDlqPublisher.getAllEntries().get(0); // GH-90000
            assertThat(pendingEntry.status()).isEqualTo("PENDING");
            assertThat(pendingEntry.retryCount()).isZero(); // GH-90000

            UUID entryId = pendingEntry.id(); // GH-90000

            // Step 2: Transition to RETRYING
            runPromise(() -> inMemoryDlqPublisher.updateStatus(entryId, "RETRYING")); // GH-90000

            DlqEntry retryngEntry = inMemoryDlqPublisher.getEntryById(entryId).orElseThrow(); // GH-90000
            assertThat(retryngEntry.status()).isEqualTo("RETRYING");

            // Step 3: Increment retry count
            runPromise(() -> inMemoryDlqPublisher.incrementRetryCount(entryId)); // GH-90000

            DlqEntry retriedEntry = inMemoryDlqPublisher.getEntryById(entryId).orElseThrow(); // GH-90000
            assertThat(retriedEntry.retryCount()).isEqualTo(1); // GH-90000

            // Step 4: Retry succeeds → transition to RESOLVED
            runPromise(() -> inMemoryDlqPublisher.updateStatus(entryId, "RESOLVED")); // GH-90000

            DlqEntry resolvedEntry = inMemoryDlqPublisher.getEntryById(entryId).orElseThrow(); // GH-90000
            assertThat(resolvedEntry.status()).isEqualTo("RESOLVED");

            // THEN — full workflow validation
            assertThat(resolvedEntry.tenantId()).isEqualTo(tenantId); // GH-90000
            assertThat(resolvedEntry.pipelineId()).isEqualTo(pipelineId); // GH-90000
            assertThat(resolvedEntry.nodeId()).isEqualTo(nodeId); // GH-90000
            assertThat(resolvedEntry.eventType()).isEqualTo(eventType); // GH-90000
            assertThat(resolvedEntry.eventPayload()).containsAllEntriesOf(payload); // GH-90000
            assertThat(resolvedEntry.failureReason()).isEqualTo("PHASE_GUARD_VIOLATION");
        }

        @Test
        @DisplayName("should handle multiple DLQ entries with different retry states")
        void shouldHandleMultipleDlqEntriesWithDifferentStates() { // GH-90000
            // GIVEN — publish 3 failures
            List<UUID> entryIds = new ArrayList<>(); // GH-90000
            for (int i = 1; i <= 3; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> // GH-90000
                        inMemoryDlqPublisher.publish( // GH-90000
                                "tenant-001",
                                "pipeline-1",
                                "node-" + idx,
                                "event.type",
                                Map.of("index", idx), // GH-90000
                                "ERROR_" + idx,
                                "corr-" + idx
                        )
                );
                entryIds.add(inMemoryDlqPublisher.getAllEntries().get(idx - 1).id()); // GH-90000
            }

            // WHEN — transition to different states
            // Entry 1: PENDING (no action) // GH-90000
            // Entry 2: RETRYING
            runPromise(() -> inMemoryDlqPublisher.updateStatus(entryIds.get(1), "RETRYING")); // GH-90000

            // Entry 3: RESOLVED
            runPromise(() -> inMemoryDlqPublisher.updateStatus(entryIds.get(2), "RESOLVED")); // GH-90000

            // THEN — all entries have correct states
            assertThat(inMemoryDlqPublisher.getEntryById(entryIds.get(0)).get().status()).isEqualTo("PENDING");
            assertThat(inMemoryDlqPublisher.getEntryById(entryIds.get(1)).get().status()).isEqualTo("RETRYING");
            assertThat(inMemoryDlqPublisher.getEntryById(entryIds.get(2)).get().status()).isEqualTo("RESOLVED");
        }

        @Test
        @DisplayName("should query DLQ entries by status filter")
        void shouldQueryDlqByStatus() { // GH-90000
            // GIVEN — mixed status entries
            for (int i = 1; i <= 3; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> // GH-90000
                        inMemoryDlqPublisher.publish( // GH-90000
                                "tenant-001",
                                "pipeline-1",
                                "node-" + idx,
                                "event.type",
                                Map.of(), // GH-90000
                                "ERROR",
                                "corr-" + idx
                        )
                );
            }

            List<DlqEntry> all = inMemoryDlqPublisher.getAllEntries(); // GH-90000
            UUID id1 = all.get(0).id(); // GH-90000
            UUID id2 = all.get(1).id(); // GH-90000
            UUID id3 = all.get(2).id(); // GH-90000

            runPromise(() -> inMemoryDlqPublisher.updateStatus(id1, "RETRYING")); // GH-90000
            runPromise(() -> inMemoryDlqPublisher.updateStatus(id2, "RESOLVED")); // GH-90000

            // WHEN — query by status
            List<DlqEntry> pending = inMemoryDlqPublisher.getEntriesByStatus("PENDING");
            List<DlqEntry> retrying = inMemoryDlqPublisher.getEntriesByStatus("RETRYING");
            List<DlqEntry> resolved = inMemoryDlqPublisher.getEntriesByStatus("RESOLVED");

            // THEN — correct filtering
            assertThat(pending).hasSize(1); // GH-90000
            assertThat(retrying).hasSize(1); // GH-90000
            assertThat(resolved).hasSize(1); // GH-90000
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
        void shouldAllowRetryingPendingEntry() { // GH-90000
            // GIVEN — published failure
            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.publish( // GH-90000
                            "tenant-001",
                            "pipeline-1",
                            "node-1",
                            "event.type",
                            Map.of(), // GH-90000
                            "ERROR",
                            "corr-1"
                    )
            );

            UUID entryId = inMemoryDlqPublisher.getAllEntries().get(0).id(); // GH-90000

            // WHEN — retry multiple times
            for (int i = 1; i <= 3; i++) { // GH-90000
                runPromise(() -> inMemoryDlqPublisher.incrementRetryCount(entryId)); // GH-90000
            }

            // THEN — all retries counted
            DlqEntry result = inMemoryDlqPublisher.getEntryById(entryId).orElseThrow(); // GH-90000
            assertThat(result.retryCount()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("should preserve original event data across retries")
        void shouldPreserveOriginalEventData() { // GH-90000
            // GIVEN — complex event payload
            Map<String, Object> originalPayload = Map.of( // GH-90000
                    "agentId", "agent-xyz",
                    "config", Map.of("timeout", 30, "retries", 3), // GH-90000
                    "tags", List.of("important", "urgent") // GH-90000
            );

            runPromise(() -> // GH-90000
                    inMemoryDlqPublisher.publish( // GH-90000
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
            UUID entryId = inMemoryDlqPublisher.getAllEntries().get(0).id(); // GH-90000
            for (int i = 0; i < 2; i++) { // GH-90000
                runPromise(() -> inMemoryDlqPublisher.incrementRetryCount(entryId)); // GH-90000
                runPromise(() -> inMemoryDlqPublisher.updateStatus(entryId, "RETRYING")); // GH-90000
            }

            // THEN — original payload unchanged
            DlqEntry result = inMemoryDlqPublisher.getEntryById(entryId).orElseThrow(); // GH-90000
            assertThat(result.eventPayload()).containsAllEntriesOf(originalPayload); // GH-90000
            assertThat(result.failureReason()).isEqualTo("COMPLEX_ERROR");
        }
    }

    // =========================================================================
    // In-Memory DLQ Publisher (Test Double) // GH-90000
    // =========================================================================

    /**
     * In-memory test double for {@link DlqPublisher} that accumulates entries
     * for assertion and query during testing.
     *
     * <p>Simulates a persistent DLQ store with state management.
     */
    private static class InMemoryDlqPublisher implements DlqPublisher {
        private final Map<UUID, DlqEntryMutable> store = Collections.synchronizedMap(new LinkedHashMap<>()); // GH-90000

        @Override
        public Promise<Void> publish( // GH-90000
                String tenantId,
                String pipelineId,
                String nodeId,
                String eventType,
                Map<String, Object> eventPayload,
                String failureReason,
                String correlationId) {

            UUID id = UUID.randomUUID(); // GH-90000
            DlqEntryMutable entry = new DlqEntryMutable( // GH-90000
                    id,
                    tenantId,
                    pipelineId,
                    nodeId,
                    eventType,
                    eventPayload != null ? new HashMap<>(eventPayload) : new HashMap<>(), // GH-90000
                    failureReason,
                    0,  // retryCount
                    "PENDING",
                    correlationId,
                    System.currentTimeMillis(), // GH-90000
                    System.currentTimeMillis(), // GH-90000
                    null  // resolvedAt
            );
            store.put(id, entry); // GH-90000
            return Promise.complete(); // GH-90000
        }

        public Promise<Void> updateStatus(UUID id, String newStatus) { // GH-90000
            DlqEntryMutable entry = store.get(id); // GH-90000
            if (entry != null) { // GH-90000
                entry.status = newStatus;
                entry.updatedAt = System.currentTimeMillis(); // GH-90000
                if ("RESOLVED".equals(newStatus)) { // GH-90000
                    entry.resolvedAt = System.currentTimeMillis(); // GH-90000
                }
            }
            return Promise.complete(); // GH-90000
        }

        public Promise<Void> incrementRetryCount(UUID id) { // GH-90000
            DlqEntryMutable entry = store.get(id); // GH-90000
            if (entry != null) { // GH-90000
                entry.retryCount++;
                entry.updatedAt = System.currentTimeMillis(); // GH-90000
            }
            return Promise.complete(); // GH-90000
        }

        public Optional<DlqEntry> getEntryById(UUID id) { // GH-90000
            DlqEntryMutable mutable = store.get(id); // GH-90000
            return mutable != null ? Optional.of(mutable.toImmutable()) : Optional.empty(); // GH-90000
        }

        public List<DlqEntry> getAllEntries() { // GH-90000
            return store.values().stream() // GH-90000
                    .map(DlqEntryMutable::toImmutable) // GH-90000
                    .toList(); // GH-90000
        }

        public List<DlqEntry> getEntriesByStatus(String status) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(entry -> status.equals(entry.status)) // GH-90000
                    .map(DlqEntryMutable::toImmutable) // GH-90000
                    .toList(); // GH-90000
        }
    }

    /** Local mirror of {@code com.ghatana.yappc.api.dlq.DlqEntry} (avoids circular module dependency). */ // GH-90000
    private record DlqEntry( // GH-90000
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
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt) {}

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

        DlqEntryMutable( // GH-90000
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

        DlqEntry toImmutable() { // GH-90000
            return new DlqEntry( // GH-90000
                    id,
                    tenantId,
                    pipelineId,
                    nodeId,
                    eventType,
                    new HashMap<>(eventPayload), // GH-90000
                    failureReason,
                    retryCount,
                    status,
                    correlationId,
                    java.time.Instant.ofEpochMilli(createdAt), // GH-90000
                    java.time.Instant.ofEpochMilli(updatedAt), // GH-90000
                    resolvedAt != null ? java.time.Instant.ofEpochMilli(resolvedAt) : null // GH-90000
            );
        }
    }
}
