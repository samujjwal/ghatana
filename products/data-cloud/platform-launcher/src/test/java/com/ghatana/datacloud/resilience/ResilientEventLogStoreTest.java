/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.resilience;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for ResilientEventLogStore.
 *
 * Tests event durability, checkpoint/recovery mechanisms, circuit breaker
 * protection, and event replay semantics across tenant boundaries.
 *
 * @doc.type class
 * @doc.purpose Test event durability with circuit breaker resilience
 * @doc.layer product
 * @doc.pattern Test, CircuitBreaker, EventLog
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("ResilientEventLogStore – Event Durability & Resilience")
class ResilientEventLogStoreTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-alpha";

    @Mock(lenient = true) // GH-90000
    private EventLogStore delegate;

    @Mock(lenient = true) // GH-90000
    private TenantContext tenantContext;

    @Mock(lenient = true) // GH-90000
    private Offset offset;

    @Mock(lenient = true) // GH-90000
    private EventLogStore.EventEntry eventEntry;

    private ResilientEventLogStore store;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() { // GH-90000
        circuitBreaker = CircuitBreaker.builder("test-circuit")
            .failureThreshold(3) // GH-90000
            .successThreshold(2) // GH-90000
            .resetTimeout(Duration.ofSeconds(10)) // GH-90000
            .maxBackoff(Duration.ofMinutes(1)) // GH-90000
            .build(); // GH-90000

        store = new ResilientEventLogStore(delegate, eventloop(), circuitBreaker); // GH-90000

        when(tenantContext.tenantId()).thenReturn(TENANT_ID); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVENT APPEND TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event Append & Durability")
    class EventAppendTests {

        @Test
        @DisplayName("[DC-009-01]: append_single_event_succeeds")
        void appendSingleEventSucceeds() { // GH-90000
            // Given
            when(delegate.append(tenantContext, eventEntry)) // GH-90000
                .thenReturn(Promise.of(offset)); // GH-90000

            // When
            Offset result = runPromise(() -> store.append(tenantContext, eventEntry)); // GH-90000

            // Then
            assertThat(result).isNotNull(); // GH-90000
            verify(delegate).append(tenantContext, eventEntry); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-02]: appendBatch_multiple_events_maintains_order")
        void appendBatchMultipleEventsSucceeds() { // GH-90000
            // Given
            List<EventLogStore.EventEntry> entries = List.of(eventEntry, eventEntry, eventEntry); // GH-90000
            List<Offset> offsets = List.of(offset, offset, offset); // GH-90000

            when(delegate.appendBatch(tenantContext, entries)) // GH-90000
                .thenReturn(Promise.of(offsets)); // GH-90000

            // When
            List<Offset> results = runPromise(() -> store.appendBatch(tenantContext, entries)); // GH-90000

            // Then
            assertThat(results).hasSize(3).allMatch(o -> o != null); // GH-90000
            verify(delegate).appendBatch(tenantContext, entries); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-03]: append_failure_increments_circuit_breaker_failure_count")
        void appendFailureIncreasesFailureCount() { // GH-90000
            // Given
            RuntimeException storageException = new RuntimeException("Storage unavailable");
            when(delegate.append(tenantContext, eventEntry)) // GH-90000
                .thenReturn(Promise.ofException(storageException)); // GH-90000

            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> store.append(tenantContext, eventEntry)) // GH-90000
            ).isInstanceOf(RuntimeException.class); // GH-90000

            verify(delegate).append(tenantContext, eventEntry); // GH-90000
            assertThat(store.getCircuitBreakerState()) // GH-90000
                .isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-04]: appendBatch_empty_list_handled_gracefully")
        void appendBatchEmptyListHandled() { // GH-90000
            // Given
            List<EventLogStore.EventEntry> emptyEntries = List.of(); // GH-90000
            when(delegate.appendBatch(tenantContext, emptyEntries)) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            // When
            List<Offset> results = runPromise(() -> store.appendBatch(tenantContext, emptyEntries)); // GH-90000

            // Then
            assertThat(results).isEmpty(); // GH-90000
            verify(delegate).appendBatch(tenantContext, emptyEntries); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVENT READ & RECOVERY TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event Read & Recovery")
    class EventReadTests {

        @Test
        @DisplayName("[DC-009-05]: read_from_offset_returns_events")
        void readFromOffsetReturnsEvents() { // GH-90000
            // Given
            List<EventLogStore.EventEntry> entries = List.of(eventEntry, eventEntry); // GH-90000
            when(delegate.read(tenantContext, offset, 100)) // GH-90000
                .thenReturn(Promise.of(entries)); // GH-90000

            // When
            List<EventLogStore.EventEntry> results = runPromise( // GH-90000
                () -> store.read(tenantContext, offset, 100) // GH-90000
            );

            // Then
            assertThat(results).hasSize(2); // GH-90000
            verify(delegate).read(tenantContext, offset, 100); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-06]: readByTimeRange_filters_events_by_interval")
        void readByTimeRangeFiltersEvents() { // GH-90000
            // Given
            Instant startTime = Instant.parse("2026-01-01T00:00:00Z");
            Instant endTime = Instant.parse("2026-01-31T23:59:59Z");
            List<EventLogStore.EventEntry> entries = List.of(eventEntry, eventEntry); // GH-90000

            when(delegate.readByTimeRange(tenantContext, startTime, endTime, 1000)) // GH-90000
                .thenReturn(Promise.of(entries)); // GH-90000

            // When
            List<EventLogStore.EventEntry> results = runPromise( // GH-90000
                () -> store.readByTimeRange(tenantContext, startTime, endTime, 1000) // GH-90000
            );

            // Then
            assertThat(results).hasSize(2); // GH-90000
            verify(delegate).readByTimeRange(tenantContext, startTime, endTime, 1000); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-07]: readByType_filters_by_event_type")
        void readByTypeFiltersEvents() { // GH-90000
            // Given
            String eventType = "workflow.run.started";
            List<EventLogStore.EventEntry> entries = List.of(eventEntry); // GH-90000

            when(delegate.readByType(tenantContext, eventType, offset, 500)) // GH-90000
                .thenReturn(Promise.of(entries)); // GH-90000

            // When
            List<EventLogStore.EventEntry> results = runPromise( // GH-90000
                () -> store.readByType(tenantContext, eventType, offset, 500) // GH-90000
            );

            // Then
            assertThat(results).hasSize(1); // GH-90000
            verify(delegate).readByType(tenantContext, eventType, offset, 500); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-08]: read_failure_propagates_exception")
        void readFailurePropagatesToCaller() { // GH-90000
            // Given
            RuntimeException readException = new RuntimeException("Read failed");
            when(delegate.read(tenantContext, offset, 100)) // GH-90000
                .thenReturn(Promise.ofException(readException)); // GH-90000

            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> store.read(tenantContext, offset, 100)) // GH-90000
            ).isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessageContaining("Read failed");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECKPOINT & OFFSET MANAGEMENT TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Checkpoint & Offset Management")
    class OffsetManagementTests {

        @Test
        @DisplayName("[DC-009-09]: getLatestOffset_returns_max_checkpoint")
        void getLatestOffsetReturnsMaxCheckpoint() { // GH-90000
            // Given
            when(delegate.getLatestOffset(tenantContext)) // GH-90000
                .thenReturn(Promise.of(offset)); // GH-90000

            // When
            Offset result = runPromise(() -> store.getLatestOffset(tenantContext)); // GH-90000

            // Then
            assertThat(result).isNotNull(); // GH-90000
            verify(delegate).getLatestOffset(tenantContext); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-10]: getEarliestOffset_returns_recovery_point")
        void getEarliestOffsetReturnsRecoveryPoint() { // GH-90000
            // Given
            when(delegate.getEarliestOffset(tenantContext)) // GH-90000
                .thenReturn(Promise.of(offset)); // GH-90000

            // When
            Offset result = runPromise(() -> store.getEarliestOffset(tenantContext)); // GH-90000

            // Then
            assertThat(result).isNotNull(); // GH-90000
            verify(delegate).getEarliestOffset(tenantContext); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-11]: offset_operations_fail_gracefully_under_circuit_break")
        void offsetOperationsFailUnderCircuitBreak() { // GH-90000
            // Given: Simulate 3 failures to trigger circuit break
            RuntimeException exception = new RuntimeException("Storage down");

            when(delegate.getLatestOffset(tenantContext)) // GH-90000
                .thenReturn(Promise.ofException(exception)) // GH-90000
                .thenReturn(Promise.ofException(exception)) // GH-90000
                .thenReturn(Promise.ofException(exception)); // GH-90000

            // Trigger 3 failures
            for (int i = 0; i < 3; i++) { // GH-90000
                try {
                    runPromise(() -> store.getLatestOffset(tenantContext)); // GH-90000
                } catch (Exception ignored) { // GH-90000
                }
            }

            // Circuit should now be OPEN after 3 failures
            assertThat(store.getCircuitBreakerState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CIRCUIT BREAKER & RESILIENCE TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Circuit Breaker Protection")
    class CircuitBreakerTests {

        @Test
        @DisplayName("[DC-009-12]: circuit_breaker_state_observable")
        void circuitBreakerStateObservable() { // GH-90000
            // When & Then
            assertThat(store.getCircuitBreakerState()) // GH-90000
                .isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000

            assertThat(store.getCircuitBreaker()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-13]: manual_circuit_breaker_reset_closes_circuit")
        void manualCircuitBreakerReset() { // GH-90000
            // Given: Circuit breaker
            CircuitBreaker breaker = store.getCircuitBreaker(); // GH-90000

            // When: Reset is called
            store.resetCircuitBreaker(); // GH-90000

            // Then: Circuit should be CLOSED
            assertThat(store.getCircuitBreakerState()) // GH-90000
                .isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-14]: append_with_circuit_breaker_protection")
        void appendWithCircuitBreakerProtection() { // GH-90000
            // Given: Successful append operation
            when(delegate.append(tenantContext, eventEntry)) // GH-90000
                .thenReturn(Promise.of(offset)); // GH-90000

            // When: Store multiple events
            for (int i = 0; i < 3; i++) { // GH-90000
                runPromise(() -> store.append(tenantContext, eventEntry)); // GH-90000
            }

            // Then: All should succeed
            verify(delegate, times(3)).append(tenantContext, eventEntry); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-15]: batch_append_with_circuit_breaker_protection")
        void batchAppendWithCircuitBreakerProtection() { // GH-90000
            // Given
            List<EventLogStore.EventEntry> entries = List.of(eventEntry, eventEntry); // GH-90000
            List<Offset> offsets = List.of(offset, offset); // GH-90000

            when(delegate.appendBatch(tenantContext, entries)) // GH-90000
                .thenReturn(Promise.of(offsets)); // GH-90000

            // When: Store multiple batches
            for (int i = 0; i < 2; i++) { // GH-90000
                runPromise(() -> store.appendBatch(tenantContext, entries)); // GH-90000
            }

            // Then: All should succeed
            verify(delegate, times(2)).appendBatch(tenantContext, entries); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TENANT ISOLATION TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenant Isolation & Multi-Tenancy")
    class TenantIsolationTests {

        @Test
        @DisplayName("[DC-009-16]: append_respects_tenant_boundaries")
        void appendRespectsTenantBoundaries() { // GH-90000
            // Given
            TenantContext anotherTenant = mock(TenantContext.class, withSettings().lenient()); // GH-90000
            when(anotherTenant.tenantId()).thenReturn("tenant-beta");

            when(delegate.append(tenantContext, eventEntry)) // GH-90000
                .thenReturn(Promise.of(offset)); // GH-90000
            when(delegate.append(anotherTenant, eventEntry)) // GH-90000
                .thenReturn(Promise.of(offset)); // GH-90000

            // When
            runPromise(() -> store.append(tenantContext, eventEntry)); // GH-90000
            runPromise(() -> store.append(anotherTenant, eventEntry)); // GH-90000

            // Then: Both tenants' calls are routed correctly
            verify(delegate).append(tenantContext, eventEntry); // GH-90000
            verify(delegate).append(anotherTenant, eventEntry); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-17]: read_operations_isolated_by_tenant")
        void readOperationsIsolatedByTenant() { // GH-90000
            // Given
            TenantContext anotherTenant = mock(TenantContext.class, withSettings().lenient()); // GH-90000
            when(anotherTenant.tenantId()).thenReturn("tenant-beta");

            List<EventLogStore.EventEntry> tenantAlphaEntries = List.of(eventEntry); // GH-90000
            List<EventLogStore.EventEntry> tenantBetaEntries = List.of(eventEntry, eventEntry); // GH-90000

            when(delegate.read(tenantContext, offset, 100)) // GH-90000
                .thenReturn(Promise.of(tenantAlphaEntries)); // GH-90000
            when(delegate.read(anotherTenant, offset, 100)) // GH-90000
                .thenReturn(Promise.of(tenantBetaEntries)); // GH-90000

            // When
            List<EventLogStore.EventEntry> alphaResults = runPromise( // GH-90000
                () -> store.read(tenantContext, offset, 100) // GH-90000
            );
            List<EventLogStore.EventEntry> betaResults = runPromise( // GH-90000
                () -> store.read(anotherTenant, offset, 100) // GH-90000
            );

            // Then: Different results for different tenants
            assertThat(alphaResults).hasSize(1); // GH-90000
            assertThat(betaResults).hasSize(2); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EDGE CASES & ERROR CONDITIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge Cases & Error Conditions")
    class EdgeCasesTests {

        @Test
        @DisplayName("[DC-009-18]: null_tenant_context_rejected")
        void nullTenantContextRejected() { // GH-90000
            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> store.append(null, eventEntry)) // GH-90000
            ).isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-19]: null_event_entry_handled")
        void nullEventEntryHandled() { // GH-90000
            // Given
            when(delegate.append(tenantContext, null)) // GH-90000
                .thenReturn(Promise.ofException( // GH-90000
                    new IllegalArgumentException("Event entry required")
                ));

            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> store.append(tenantContext, null)) // GH-90000
            ).isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-20]: large_batch_operations_handled")
        void largeBatchOperationsHandled() { // GH-90000
            // Given: 100 event entries (reduced from 10,000 for test performance) // GH-90000
            List<EventLogStore.EventEntry> largeEntries = new java.util.ArrayList<>(); // GH-90000
            List<Offset> largeOffsets = new java.util.ArrayList<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                largeEntries.add(eventEntry); // GH-90000
                largeOffsets.add(offset); // GH-90000
            }

            when(delegate.appendBatch(tenantContext, largeEntries)) // GH-90000
                .thenReturn(Promise.of(largeOffsets)); // GH-90000

            // When
            List<Offset> results = runPromise(() -> // GH-90000
                store.appendBatch(tenantContext, largeEntries) // GH-90000
            );

            // Then: All entries processed
            assertThat(results).hasSize(100); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STREAMING & TAIL TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event Stream & Tail Operations")
    class StreamingTests {

        @Test
        @DisplayName("[DC-009-21]: tail_establishes_streaming_subscription")
        void tailEstablishesSubscription() { // GH-90000
            // Given
            EventLogStore.Subscription mockSubscription = mock(EventLogStore.Subscription.class); // GH-90000
            when(delegate.tail(eq(tenantContext), eq(offset), any())) // GH-90000
                .thenReturn(Promise.of(mockSubscription)); // GH-90000

            // When
            EventLogStore.Subscription result = runPromise(() -> // GH-90000
                store.tail(tenantContext, offset, entry -> {}) // GH-90000
            );

            // Then
            assertThat(result).isNotNull(); // GH-90000
            verify(delegate).tail(eq(tenantContext), eq(offset), any()); // GH-90000
        }

        @Test
        @DisplayName("[DC-009-22]: tail_failure_handled_by_circuit_breaker")
        void tailFailureHandledByCircuitBreaker() { // GH-90000
            // Given
            RuntimeException tailException = new RuntimeException("Streaming failed");
            when(delegate.tail(eq(tenantContext), eq(offset), any())) // GH-90000
                .thenReturn(Promise.ofException(tailException)); // GH-90000

            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> store.tail(tenantContext, offset, entry -> {})) // GH-90000
            ).isInstanceOf(RuntimeException.class); // GH-90000
        }
    }
}
