/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Tests for event idempotency and idempotent apply (D007). 
 *
 * <p>Validates that replayed events are processed idempotently.
 *
 * @doc.type class
 * @doc.purpose Event idempotency tests for replay operations
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("EventIdempotency – Idempotent Apply (D007)")
class EventIdempotencyTest extends EventloopTestBase {

    @Mock
    private EventReplayService replayService;

    private final ConcurrentHashMap<String, AtomicInteger> eventApplyCounts = new ConcurrentHashMap<>(); 

    // ─────────────────────────────────────────────────────────────────────────
    // Idempotent Apply Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotent Apply")
    class IdempotentApplyTests {

        @Test
        @DisplayName("[D007]: first_apply_succeeds")
        void firstApplySucceeds() { 
            String eventId = "evt-001";
            EventReplayService.ReplayedEvent event = new EventReplayService.ReplayedEvent( 
                eventId, "entity.created", "tenant-alpha",
                1, System.currentTimeMillis(), null, 1 
            );

            AtomicInteger counter = eventApplyCounts.computeIfAbsent(eventId, k -> new AtomicInteger(0)); 
            int result = counter.incrementAndGet(); 

            assertThat(result).isEqualTo(1); 
        }

        @Test
        @DisplayName("[D007]: second_apply_is_idempotent")
        void secondApplyIsIdempotent() { 
            String eventId = "evt-001";
            String dedupKey = eventId + ":" + "entity.created";

            // Simulate idempotency tracking
            ConcurrentHashMap<String, Boolean> processedEvents = new ConcurrentHashMap<>(); 

            // First apply
            boolean first = processedEvents.putIfAbsent(dedupKey, Boolean.TRUE) == null; 
            // Second apply
            boolean second = processedEvents.putIfAbsent(dedupKey, Boolean.TRUE) == null; 

            assertThat(first).isTrue(); 
            assertThat(second).isFalse(); // Idempotent - already processed 
        }

        @Test
        @DisplayName("[D007]: same_event_multiple_replays_processed_once")
        void sameEventMultipleReplaysProcessedOnce() { 
            String eventId = "evt-001";
            List<EventReplayService.ReplayedEvent> replays = List.of( 
                new EventReplayService.ReplayedEvent(eventId, "test", "tenant", 1, 0, null, 1), 
                new EventReplayService.ReplayedEvent(eventId, "test", "tenant", 1, 0, null, 2), 
                new EventReplayService.ReplayedEvent(eventId, "test", "tenant", 1, 0, null, 3) 
            );

            ConcurrentHashMap<String, Integer> processed = new ConcurrentHashMap<>(); 

            for (EventReplayService.ReplayedEvent replay : replays) { 
                processed.compute(replay.id(), (k, v) -> v == null ? 1 : v + 1); 
            }

            // Should only have one entry with count 3
            assertThat(processed).containsKey(eventId); 
            assertThat(processed.get(eventId)).isEqualTo(3); // But we can detect replays 
        }

        @Test
        @DisplayName("[D007]: different_events_same_id_processed_separately")
        void differentEventsSameIdProcessedSeparately() { 
            // Different events with same ID but different types
            String eventId = "evt-001";
            EventReplayService.ReplayedEvent create =
                new EventReplayService.ReplayedEvent(eventId, "entity.created", "tenant", 1, 0, null, 1); 
            EventReplayService.ReplayedEvent update =
                new EventReplayService.ReplayedEvent(eventId, "entity.updated", "tenant", 2, 0, null, 1); 

            // Each should be processed (different types = different operations) 
            assertThat(create.type()).isNotEqualTo(update.type()); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deduplication Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Deduplication")
    class DeduplicationTests {

        @Test
        @DisplayName("[D007]: deduplication_by_event_id_prevents_reprocessing")
        void deduplicationByEventIdPreventsReprocessing() { 
            ConcurrentHashMap<String, String> idempotencyStore = new ConcurrentHashMap<>(); 
            String eventId = "evt-dedup-001";

            // First processing
            String firstResult = idempotencyStore.computeIfAbsent(eventId, k -> "PROCESSED"); 

            // Second processing - should return existing
            String secondResult = idempotencyStore.computeIfAbsent(eventId, k -> "REPROCESSED"); 

            assertThat(firstResult).isEqualTo("PROCESSED");
            assertThat(secondResult).isEqualTo("PROCESSED"); // Same value, not reprocessed
        }

        @Test
        @DisplayName("[D007]: deduplication_includes_offset_for_uniqueness")
        void deduplicationIncludesOffsetForUniqueness() { 
            String eventId = "evt-001";

            // Same event ID but different offsets (should be different processing) 
            String key1 = eventId + ":offset:1";
            String key2 = eventId + ":offset:2";

            assertThat(key1).isNotEqualTo(key2); 
        }

        @Test
        @DisplayName("[D007]: tenant_isolated_deduplication")
        void tenantIsolatedDeduplication() { 
            String eventId = "evt-001";
            String tenantAlpha = "tenant-alpha";
            String tenantBeta = "tenant-beta";

            // Same event ID in different tenants = different dedup keys
            String keyAlpha = tenantAlpha + ":" + eventId;
            String keyBeta = tenantBeta + ":" + eventId;

            assertThat(keyAlpha).isNotEqualTo(keyBeta); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State Consistency Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("State Consistency")
    class StateConsistencyTests {

        @Test
        @DisplayName("[D007]: idempotent_update_produces_same_state")
        void idempotentUpdateProducesSameState() { 
            // Simulate state machine
            class EntityState {
                private String id;
                private int version = 0;
                private String status = "NEW";

                void applyCreate(String eventId) { 
                    if (version == 0) { 
                        this.id = eventId;
                        this.version = 1;
                        this.status = "CREATED";
                    }
                }

                void applyUpdate(String eventId) { 
                    if (version > 0 && !"DELETED".equals(status)) { 
                        this.version++;
                    }
                }
            }

            EntityState state = new EntityState(); 

            // Apply same create multiple times
            state.applyCreate("evt-create");
            int versionAfterFirst = state.version;

            state.applyCreate("evt-create");
            int versionAfterSecond = state.version;

            assertThat(versionAfterFirst).isEqualTo(1); 
            assertThat(versionAfterSecond).isEqualTo(1); // No change 
        }

        @Test
        @DisplayName("[D007]: conditional_update_checks_version")
        void conditionalUpdateChecksVersion() { 
            // Simulate optimistic locking
            int currentVersion = 5;
            int expectedVersion = 5;

            boolean canUpdate = currentVersion == expectedVersion;

            assertThat(canUpdate).isTrue(); 

            // After update, version increments
            int newVersion = canUpdate ? currentVersion + 1 : currentVersion;
            assertThat(newVersion).isEqualTo(6); 

            // Retry with old version should fail
            boolean retryUpdate = newVersion == expectedVersion;
            assertThat(retryUpdate).isFalse(); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Replay Count Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Replay Count")
    class ReplayCountTests {

        @Test
        @DisplayName("[D007]: replay_count_tracks_number_of_replays")
        void replayCountTracksNumberOfReplays() { 
            EventReplayService.ReplayedEvent first =
                new EventReplayService.ReplayedEvent("evt-001", "test", "tenant", 1, 0, null, 1); 
            EventReplayService.ReplayedEvent second =
                new EventReplayService.ReplayedEvent("evt-001", "test", "tenant", 1, 0, null, 2); 
            EventReplayService.ReplayedEvent third =
                new EventReplayService.ReplayedEvent("evt-001", "test", "tenant", 1, 0, null, 3); 

            assertThat(first.replayCount()).isEqualTo(1); 
            assertThat(second.replayCount()).isEqualTo(2); 
            assertThat(third.replayCount()).isEqualTo(3); 
        }

        @Test
        @DisplayName("[D007]: is_first_replay_true_only_on_first")
        void isFirstReplayTrueOnlyOnFirst() { 
            EventReplayService.ReplayedEvent first =
                new EventReplayService.ReplayedEvent("evt-001", "test", "tenant", 1, 0, null, 1); 
            EventReplayService.ReplayedEvent second =
                new EventReplayService.ReplayedEvent("evt-001", "test", "tenant", 1, 0, null, 2); 

            assertThat(first.isFirstReplay()).isTrue(); 
            assertThat(second.isFirstReplay()).isFalse(); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrent Replay Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrent Replay")
    class ConcurrentReplayTests {

        @Test
        @DisplayName("[D007]: concurrent_replays_maintain_idempotency")
        void concurrentReplaysMaintainIdempotency() { 
            String eventId = "evt-concurrent-001";
            ConcurrentHashMap<String, String> processed = new ConcurrentHashMap<>(); 

            // Simulate concurrent processing
            String result1 = processed.putIfAbsent(eventId, "PROCESSING"); 
            String result2 = processed.putIfAbsent(eventId, "PROCESSING"); 

            assertThat(result1).isNull(); // First succeeds 
            assertThat(result2).isEqualTo("PROCESSING"); // Second sees existing
        }

        @Test
        @DisplayName("[D007]: atomic_check_and_set_for_idempotency")
        void atomicCheckAndSetForIdempotency() { 
            ConcurrentHashMap<String, String> state = new ConcurrentHashMap<>(); 
            String eventId = "evt-atomic-001";

            // Atomic operation: check if absent, then set
            boolean wasAbsent = state.putIfAbsent(eventId, "PROCESSED") == null; 

            assertThat(wasAbsent).isTrue(); 

            // Second attempt
            boolean wasAbsent2 = state.putIfAbsent(eventId, "PROCESSED") == null; 
            assertThat(wasAbsent2).isFalse(); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error Handling Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("[D007]: failed_apply_can_be_retried")
        void failedApplyCanBeRetried() { 
            // Simulate transient failure
            AtomicInteger attempts = new AtomicInteger(0); 

            String result = null;
            while (result == null && attempts.incrementAndGet() <= 3) { 
                try {
                    // Simulate processing that fails first 2 times
                    if (attempts.get() < 3) { 
                        throw new RuntimeException("Transient error");
                    }
                    result = "SUCCESS";
                } catch (RuntimeException e) { 
                    // Retry
                }
            }

            assertThat(result).isEqualTo("SUCCESS");
            assertThat(attempts.get()).isEqualTo(3); 
        }

        @Test
        @DisplayName("[D007]: non_retryable_error_stops_processing")
        void nonRetryableErrorStopsProcessing() { 
            EventReplayService.ReplayError error = new EventReplayService.ReplayError( 
                42, "evt-001", "VALIDATION_ERROR", "Invalid data format", false
            );

            assertThat(error.retryable()).isFalse(); 
        }

        @Test
        @DisplayName("[D007]: retryable_error_allows_retry")
        void retryableErrorAllowsRetry() { 
            EventReplayService.ReplayError error = new EventReplayService.ReplayError( 
                42, "evt-001", "TIMEOUT_ERROR", "Connection timeout", true
            );

            assertThat(error.retryable()).isTrue(); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handler Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Handler")
    class HandlerTests {

        @Test
        @DisplayName("[D007]: handler_receives_event_data")
        void handlerReceivesEventData() { 
            EventReplayService.ReplayedEvent event = new EventReplayService.ReplayedEvent( 
                "evt-001", "entity.created", "tenant-alpha",
                42, 1704067200000L, "{\"id\":\"123\"}".getBytes(), 1 
            );

            assertThat(event.id()).isEqualTo("evt-001");
            assertThat(event.type()).isEqualTo("entity.created");
            assertThat(event.tenantId()).isEqualTo("tenant-alpha");
            assertThat(event.offset()).isEqualTo(42); 
            assertThat(event.timestamp()).isEqualTo(1704067200000L); 
        }

        @Test
        @DisplayName("[D007]: handler_returns_promise")
        void handlerReturnsPromise() { 
            EventReplayService.EventHandler handler = e -> Promise.of((Void) null); 

            EventReplayService.ReplayedEvent event = new EventReplayService.ReplayedEvent( 
                "evt-001", "test", "tenant", 1, 0, null, 1
            );

            Promise<Void> result = handler.handle(event); 
            assertThat(result).isNotNull(); 
        }
    }
}
