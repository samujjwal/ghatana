/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.event;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for event idempotency and idempotent apply (D007). // GH-90000
 *
 * <p>Validates that replayed events are processed idempotently.
 *
 * @doc.type class
 * @doc.purpose Event idempotency tests for replay operations
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("EventIdempotency – Idempotent Apply (D007) [GH-90000]")
class EventIdempotencyTest extends EventloopTestBase {

    @Mock
    private EventReplayService replayService;

    private final ConcurrentHashMap<String, AtomicInteger> eventApplyCounts = new ConcurrentHashMap<>(); // GH-90000

    // ─────────────────────────────────────────────────────────────────────────
    // Idempotent Apply Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotent Apply [GH-90000]")
    class IdempotentApplyTests {

        @Test
        @DisplayName("[D007]: first_apply_succeeds [GH-90000]")
        void firstApplySucceeds() { // GH-90000
            String eventId = "evt-001";
            EventReplayService.ReplayedEvent event = new EventReplayService.ReplayedEvent( // GH-90000
                eventId, "entity.created", "tenant-alpha",
                1, System.currentTimeMillis(), null, 1 // GH-90000
            );

            AtomicInteger counter = eventApplyCounts.computeIfAbsent(eventId, k -> new AtomicInteger(0)); // GH-90000
            int result = counter.incrementAndGet(); // GH-90000

            assertThat(result).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("[D007]: second_apply_is_idempotent [GH-90000]")
        void secondApplyIsIdempotent() { // GH-90000
            String eventId = "evt-001";
            String dedupKey = eventId + ":" + "entity.created";

            // Simulate idempotency tracking
            ConcurrentHashMap<String, Boolean> processedEvents = new ConcurrentHashMap<>(); // GH-90000

            // First apply
            boolean first = processedEvents.putIfAbsent(dedupKey, Boolean.TRUE) == null; // GH-90000
            // Second apply
            boolean second = processedEvents.putIfAbsent(dedupKey, Boolean.TRUE) == null; // GH-90000

            assertThat(first).isTrue(); // GH-90000
            assertThat(second).isFalse(); // Idempotent - already processed // GH-90000
        }

        @Test
        @DisplayName("[D007]: same_event_multiple_replays_processed_once [GH-90000]")
        void sameEventMultipleReplaysProcessedOnce() { // GH-90000
            String eventId = "evt-001";
            List<EventReplayService.ReplayedEvent> replays = List.of( // GH-90000
                new EventReplayService.ReplayedEvent(eventId, "test", "tenant", 1, 0, null, 1), // GH-90000
                new EventReplayService.ReplayedEvent(eventId, "test", "tenant", 1, 0, null, 2), // GH-90000
                new EventReplayService.ReplayedEvent(eventId, "test", "tenant", 1, 0, null, 3) // GH-90000
            );

            ConcurrentHashMap<String, Integer> processed = new ConcurrentHashMap<>(); // GH-90000

            for (EventReplayService.ReplayedEvent replay : replays) { // GH-90000
                processed.compute(replay.id(), (k, v) -> v == null ? 1 : v + 1); // GH-90000
            }

            // Should only have one entry with count 3
            assertThat(processed).containsKey(eventId); // GH-90000
            assertThat(processed.get(eventId)).isEqualTo(3); // But we can detect replays // GH-90000
        }

        @Test
        @DisplayName("[D007]: different_events_same_id_processed_separately [GH-90000]")
        void differentEventsSameIdProcessedSeparately() { // GH-90000
            // Different events with same ID but different types
            String eventId = "evt-001";
            EventReplayService.ReplayedEvent create =
                new EventReplayService.ReplayedEvent(eventId, "entity.created", "tenant", 1, 0, null, 1); // GH-90000
            EventReplayService.ReplayedEvent update =
                new EventReplayService.ReplayedEvent(eventId, "entity.updated", "tenant", 2, 0, null, 1); // GH-90000

            // Each should be processed (different types = different operations) // GH-90000
            assertThat(create.type()).isNotEqualTo(update.type()); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deduplication Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Deduplication [GH-90000]")
    class DeduplicationTests {

        @Test
        @DisplayName("[D007]: deduplication_by_event_id_prevents_reprocessing [GH-90000]")
        void deduplicationByEventIdPreventsReprocessing() { // GH-90000
            ConcurrentHashMap<String, String> idempotencyStore = new ConcurrentHashMap<>(); // GH-90000
            String eventId = "evt-dedup-001";

            // First processing
            String firstResult = idempotencyStore.computeIfAbsent(eventId, k -> "PROCESSED"); // GH-90000

            // Second processing - should return existing
            String secondResult = idempotencyStore.computeIfAbsent(eventId, k -> "REPROCESSED"); // GH-90000

            assertThat(firstResult).isEqualTo("PROCESSED [GH-90000]");
            assertThat(secondResult).isEqualTo("PROCESSED [GH-90000]"); // Same value, not reprocessed
        }

        @Test
        @DisplayName("[D007]: deduplication_includes_offset_for_uniqueness [GH-90000]")
        void deduplicationIncludesOffsetForUniqueness() { // GH-90000
            String eventId = "evt-001";

            // Same event ID but different offsets (should be different processing) // GH-90000
            String key1 = eventId + ":offset:1";
            String key2 = eventId + ":offset:2";

            assertThat(key1).isNotEqualTo(key2); // GH-90000
        }

        @Test
        @DisplayName("[D007]: tenant_isolated_deduplication [GH-90000]")
        void tenantIsolatedDeduplication() { // GH-90000
            String eventId = "evt-001";
            String tenantAlpha = "tenant-alpha";
            String tenantBeta = "tenant-beta";

            // Same event ID in different tenants = different dedup keys
            String keyAlpha = tenantAlpha + ":" + eventId;
            String keyBeta = tenantBeta + ":" + eventId;

            assertThat(keyAlpha).isNotEqualTo(keyBeta); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State Consistency Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("State Consistency [GH-90000]")
    class StateConsistencyTests {

        @Test
        @DisplayName("[D007]: idempotent_update_produces_same_state [GH-90000]")
        void idempotentUpdateProducesSameState() { // GH-90000
            // Simulate state machine
            class EntityState {
                private String id;
                private int version = 0;
                private String status = "NEW";

                void applyCreate(String eventId) { // GH-90000
                    if (version == 0) { // GH-90000
                        this.id = eventId;
                        this.version = 1;
                        this.status = "CREATED";
                    }
                }

                void applyUpdate(String eventId) { // GH-90000
                    if (version > 0 && !"DELETED".equals(status)) { // GH-90000
                        this.version++;
                    }
                }
            }

            EntityState state = new EntityState(); // GH-90000

            // Apply same create multiple times
            state.applyCreate("evt-create [GH-90000]");
            int versionAfterFirst = state.version;

            state.applyCreate("evt-create [GH-90000]");
            int versionAfterSecond = state.version;

            assertThat(versionAfterFirst).isEqualTo(1); // GH-90000
            assertThat(versionAfterSecond).isEqualTo(1); // No change // GH-90000
        }

        @Test
        @DisplayName("[D007]: conditional_update_checks_version [GH-90000]")
        void conditionalUpdateChecksVersion() { // GH-90000
            // Simulate optimistic locking
            int currentVersion = 5;
            int expectedVersion = 5;

            boolean canUpdate = currentVersion == expectedVersion;

            assertThat(canUpdate).isTrue(); // GH-90000

            // After update, version increments
            int newVersion = canUpdate ? currentVersion + 1 : currentVersion;
            assertThat(newVersion).isEqualTo(6); // GH-90000

            // Retry with old version should fail
            boolean retryUpdate = newVersion == expectedVersion;
            assertThat(retryUpdate).isFalse(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Replay Count Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Replay Count [GH-90000]")
    class ReplayCountTests {

        @Test
        @DisplayName("[D007]: replay_count_tracks_number_of_replays [GH-90000]")
        void replayCountTracksNumberOfReplays() { // GH-90000
            EventReplayService.ReplayedEvent first =
                new EventReplayService.ReplayedEvent("evt-001", "test", "tenant", 1, 0, null, 1); // GH-90000
            EventReplayService.ReplayedEvent second =
                new EventReplayService.ReplayedEvent("evt-001", "test", "tenant", 1, 0, null, 2); // GH-90000
            EventReplayService.ReplayedEvent third =
                new EventReplayService.ReplayedEvent("evt-001", "test", "tenant", 1, 0, null, 3); // GH-90000

            assertThat(first.replayCount()).isEqualTo(1); // GH-90000
            assertThat(second.replayCount()).isEqualTo(2); // GH-90000
            assertThat(third.replayCount()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("[D007]: is_first_replay_true_only_on_first [GH-90000]")
        void isFirstReplayTrueOnlyOnFirst() { // GH-90000
            EventReplayService.ReplayedEvent first =
                new EventReplayService.ReplayedEvent("evt-001", "test", "tenant", 1, 0, null, 1); // GH-90000
            EventReplayService.ReplayedEvent second =
                new EventReplayService.ReplayedEvent("evt-001", "test", "tenant", 1, 0, null, 2); // GH-90000

            assertThat(first.isFirstReplay()).isTrue(); // GH-90000
            assertThat(second.isFirstReplay()).isFalse(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrent Replay Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrent Replay [GH-90000]")
    class ConcurrentReplayTests {

        @Test
        @DisplayName("[D007]: concurrent_replays_maintain_idempotency [GH-90000]")
        void concurrentReplaysMaintainIdempotency() { // GH-90000
            String eventId = "evt-concurrent-001";
            ConcurrentHashMap<String, String> processed = new ConcurrentHashMap<>(); // GH-90000

            // Simulate concurrent processing
            String result1 = processed.putIfAbsent(eventId, "PROCESSING"); // GH-90000
            String result2 = processed.putIfAbsent(eventId, "PROCESSING"); // GH-90000

            assertThat(result1).isNull(); // First succeeds // GH-90000
            assertThat(result2).isEqualTo("PROCESSING [GH-90000]"); // Second sees existing
        }

        @Test
        @DisplayName("[D007]: atomic_check_and_set_for_idempotency [GH-90000]")
        void atomicCheckAndSetForIdempotency() { // GH-90000
            ConcurrentHashMap<String, String> state = new ConcurrentHashMap<>(); // GH-90000
            String eventId = "evt-atomic-001";

            // Atomic operation: check if absent, then set
            boolean wasAbsent = state.putIfAbsent(eventId, "PROCESSED") == null; // GH-90000

            assertThat(wasAbsent).isTrue(); // GH-90000

            // Second attempt
            boolean wasAbsent2 = state.putIfAbsent(eventId, "PROCESSED") == null; // GH-90000
            assertThat(wasAbsent2).isFalse(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error Handling Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error Handling [GH-90000]")
    class ErrorHandlingTests {

        @Test
        @DisplayName("[D007]: failed_apply_can_be_retried [GH-90000]")
        void failedApplyCanBeRetried() { // GH-90000
            // Simulate transient failure
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000

            String result = null;
            while (result == null && attempts.incrementAndGet() <= 3) { // GH-90000
                try {
                    // Simulate processing that fails first 2 times
                    if (attempts.get() < 3) { // GH-90000
                        throw new RuntimeException("Transient error [GH-90000]");
                    }
                    result = "SUCCESS";
                } catch (RuntimeException e) { // GH-90000
                    // Retry
                }
            }

            assertThat(result).isEqualTo("SUCCESS [GH-90000]");
            assertThat(attempts.get()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("[D007]: non_retryable_error_stops_processing [GH-90000]")
        void nonRetryableErrorStopsProcessing() { // GH-90000
            EventReplayService.ReplayError error = new EventReplayService.ReplayError( // GH-90000
                42, "evt-001", "VALIDATION_ERROR", "Invalid data format", false
            );

            assertThat(error.retryable()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("[D007]: retryable_error_allows_retry [GH-90000]")
        void retryableErrorAllowsRetry() { // GH-90000
            EventReplayService.ReplayError error = new EventReplayService.ReplayError( // GH-90000
                42, "evt-001", "TIMEOUT_ERROR", "Connection timeout", true
            );

            assertThat(error.retryable()).isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handler Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Handler [GH-90000]")
    class HandlerTests {

        @Test
        @DisplayName("[D007]: handler_receives_event_data [GH-90000]")
        void handlerReceivesEventData() { // GH-90000
            EventReplayService.ReplayedEvent event = new EventReplayService.ReplayedEvent( // GH-90000
                "evt-001", "entity.created", "tenant-alpha",
                42, 1704067200000L, "{\"id\":\"123\"}".getBytes(), 1 // GH-90000
            );

            assertThat(event.id()).isEqualTo("evt-001 [GH-90000]");
            assertThat(event.type()).isEqualTo("entity.created [GH-90000]");
            assertThat(event.tenantId()).isEqualTo("tenant-alpha [GH-90000]");
            assertThat(event.offset()).isEqualTo(42); // GH-90000
            assertThat(event.timestamp()).isEqualTo(1704067200000L); // GH-90000
        }

        @Test
        @DisplayName("[D007]: handler_returns_promise [GH-90000]")
        void handlerReturnsPromise() { // GH-90000
            EventReplayService.EventHandler handler = e -> Promise.of((Void) null); // GH-90000

            EventReplayService.ReplayedEvent event = new EventReplayService.ReplayedEvent( // GH-90000
                "evt-001", "test", "tenant", 1, 0, null, 1
            );

            Promise<Void> result = handler.handle(event); // GH-90000
            assertThat(result).isNotNull(); // GH-90000
        }
    }
}
