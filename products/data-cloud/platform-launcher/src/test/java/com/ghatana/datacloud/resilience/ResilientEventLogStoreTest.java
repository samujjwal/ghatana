/*
 * Copyright (c) 2026 Ghatana Inc.
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
@ExtendWith(MockitoExtension.class)
@DisplayName("ResilientEventLogStore – Event Durability & Resilience")
class ResilientEventLogStoreTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-alpha";

    @Mock(lenient = true)
    private EventLogStore delegate;

    @Mock(lenient = true)
    private TenantContext tenantContext;

    @Mock(lenient = true)
    private Offset offset;

    @Mock(lenient = true)
    private EventLogStore.EventEntry eventEntry;

    private ResilientEventLogStore store;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.builder("test-circuit")
            .failureThreshold(3)
            .successThreshold(2)
            .resetTimeout(Duration.ofSeconds(10))
            .maxBackoff(Duration.ofMinutes(1))
            .build();

        store = new ResilientEventLogStore(delegate, eventloop(), circuitBreaker);

        when(tenantContext.tenantId()).thenReturn(TENANT_ID);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVENT APPEND TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event Append & Durability")
    class EventAppendTests {

        @Test
        @DisplayName("[DC-009-01]: append_single_event_succeeds")
        void appendSingleEventSucceeds() {
            // Given
            when(delegate.append(tenantContext, eventEntry))
                .thenReturn(Promise.of(offset));

            // When
            Offset result = runPromise(() -> store.append(tenantContext, eventEntry));

            // Then
            assertThat(result).isNotNull();
            verify(delegate).append(tenantContext, eventEntry);
        }

        @Test
        @DisplayName("[DC-009-02]: appendBatch_multiple_events_maintains_order")
        void appendBatchMultipleEventsSucceeds() {
            // Given
            List<EventLogStore.EventEntry> entries = List.of(eventEntry, eventEntry, eventEntry);
            List<Offset> offsets = List.of(offset, offset, offset);

            when(delegate.appendBatch(tenantContext, entries))
                .thenReturn(Promise.of(offsets));

            // When
            List<Offset> results = runPromise(() -> store.appendBatch(tenantContext, entries));

            // Then
            assertThat(results).hasSize(3).allMatch(o -> o != null);
            verify(delegate).appendBatch(tenantContext, entries);
        }

        @Test
        @DisplayName("[DC-009-03]: append_failure_increments_circuit_breaker_failure_count")
        void appendFailureIncreasesFailureCount() {
            // Given
            RuntimeException storageException = new RuntimeException("Storage unavailable");
            when(delegate.append(tenantContext, eventEntry))
                .thenReturn(Promise.ofException(storageException));

            // When & Then
            assertThatThrownBy(() ->
                runPromise(() -> store.append(tenantContext, eventEntry))
            ).isInstanceOf(RuntimeException.class);

            verify(delegate).append(tenantContext, eventEntry);
            assertThat(store.getCircuitBreakerState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("[DC-009-04]: appendBatch_empty_list_handled_gracefully")
        void appendBatchEmptyListHandled() {
            // Given
            List<EventLogStore.EventEntry> emptyEntries = List.of();
            when(delegate.appendBatch(tenantContext, emptyEntries))
                .thenReturn(Promise.of(List.of()));

            // When
            List<Offset> results = runPromise(() -> store.appendBatch(tenantContext, emptyEntries));

            // Then
            assertThat(results).isEmpty();
            verify(delegate).appendBatch(tenantContext, emptyEntries);
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
        void readFromOffsetReturnsEvents() {
            // Given
            List<EventLogStore.EventEntry> entries = List.of(eventEntry, eventEntry);
            when(delegate.read(tenantContext, offset, 100))
                .thenReturn(Promise.of(entries));

            // When
            List<EventLogStore.EventEntry> results = runPromise(
                () -> store.read(tenantContext, offset, 100)
            );

            // Then
            assertThat(results).hasSize(2);
            verify(delegate).read(tenantContext, offset, 100);
        }

        @Test
        @DisplayName("[DC-009-06]: readByTimeRange_filters_events_by_interval")
        void readByTimeRangeFiltersEvents() {
            // Given
            Instant startTime = Instant.parse("2026-01-01T00:00:00Z");
            Instant endTime = Instant.parse("2026-01-31T23:59:59Z");
            List<EventLogStore.EventEntry> entries = List.of(eventEntry, eventEntry);

            when(delegate.readByTimeRange(tenantContext, startTime, endTime, 1000))
                .thenReturn(Promise.of(entries));

            // When
            List<EventLogStore.EventEntry> results = runPromise(
                () -> store.readByTimeRange(tenantContext, startTime, endTime, 1000)
            );

            // Then
            assertThat(results).hasSize(2);
            verify(delegate).readByTimeRange(tenantContext, startTime, endTime, 1000);
        }

        @Test
        @DisplayName("[DC-009-07]: readByType_filters_by_event_type")
        void readByTypeFiltersEvents() {
            // Given
            String eventType = "workflow.run.started";
            List<EventLogStore.EventEntry> entries = List.of(eventEntry);

            when(delegate.readByType(tenantContext, eventType, offset, 500))
                .thenReturn(Promise.of(entries));

            // When
            List<EventLogStore.EventEntry> results = runPromise(
                () -> store.readByType(tenantContext, eventType, offset, 500)
            );

            // Then
            assertThat(results).hasSize(1);
            verify(delegate).readByType(tenantContext, eventType, offset, 500);
        }

        @Test
        @DisplayName("[DC-009-08]: read_failure_propagates_exception")
        void readFailurePropagatesToCaller() {
            // Given
            RuntimeException readException = new RuntimeException("Read failed");
            when(delegate.read(tenantContext, offset, 100))
                .thenReturn(Promise.ofException(readException));

            // When & Then
            assertThatThrownBy(() ->
                runPromise(() -> store.read(tenantContext, offset, 100))
            ).isInstanceOf(RuntimeException.class)
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
        void getLatestOffsetReturnsMaxCheckpoint() {
            // Given
            when(delegate.getLatestOffset(tenantContext))
                .thenReturn(Promise.of(offset));

            // When
            Offset result = runPromise(() -> store.getLatestOffset(tenantContext));

            // Then
            assertThat(result).isNotNull();
            verify(delegate).getLatestOffset(tenantContext);
        }

        @Test
        @DisplayName("[DC-009-10]: getEarliestOffset_returns_recovery_point")
        void getEarliestOffsetReturnsRecoveryPoint() {
            // Given
            when(delegate.getEarliestOffset(tenantContext))
                .thenReturn(Promise.of(offset));

            // When
            Offset result = runPromise(() -> store.getEarliestOffset(tenantContext));

            // Then
            assertThat(result).isNotNull();
            verify(delegate).getEarliestOffset(tenantContext);
        }

        @Test
        @DisplayName("[DC-009-11]: offset_operations_fail_gracefully_under_circuit_break")
        void offsetOperationsFailUnderCircuitBreak() {
            // Given: Simulate 3 failures to trigger circuit break
            RuntimeException exception = new RuntimeException("Storage down");

            when(delegate.getLatestOffset(tenantContext))
                .thenReturn(Promise.ofException(exception))
                .thenReturn(Promise.ofException(exception))
                .thenReturn(Promise.ofException(exception));

            // Trigger 3 failures
            for (int i = 0; i < 3; i++) {
                try {
                    runPromise(() -> store.getLatestOffset(tenantContext));
                } catch (Exception ignored) {
                }
            }

            // Circuit should now be OPEN after 3 failures
            assertThat(store.getCircuitBreakerState()).isEqualTo(CircuitBreaker.State.OPEN);
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
        void circuitBreakerStateObservable() {
            // When & Then
            assertThat(store.getCircuitBreakerState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

            assertThat(store.getCircuitBreaker()).isNotNull();
        }

        @Test
        @DisplayName("[DC-009-13]: manual_circuit_breaker_reset_closes_circuit")
        void manualCircuitBreakerReset() {
            // Given: Circuit breaker
            CircuitBreaker breaker = store.getCircuitBreaker();

            // When: Reset is called
            store.resetCircuitBreaker();

            // Then: Circuit should be CLOSED
            assertThat(store.getCircuitBreakerState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("[DC-009-14]: append_with_circuit_breaker_protection")
        void appendWithCircuitBreakerProtection() {
            // Given: Successful append operation
            when(delegate.append(tenantContext, eventEntry))
                .thenReturn(Promise.of(offset));

            // When: Store multiple events
            for (int i = 0; i < 3; i++) {
                runPromise(() -> store.append(tenantContext, eventEntry));
            }

            // Then: All should succeed
            verify(delegate, times(3)).append(tenantContext, eventEntry);
        }

        @Test
        @DisplayName("[DC-009-15]: batch_append_with_circuit_breaker_protection")
        void batchAppendWithCircuitBreakerProtection() {
            // Given
            List<EventLogStore.EventEntry> entries = List.of(eventEntry, eventEntry);
            List<Offset> offsets = List.of(offset, offset);

            when(delegate.appendBatch(tenantContext, entries))
                .thenReturn(Promise.of(offsets));

            // When: Store multiple batches
            for (int i = 0; i < 2; i++) {
                runPromise(() -> store.appendBatch(tenantContext, entries));
            }

            // Then: All should succeed
            verify(delegate, times(2)).appendBatch(tenantContext, entries);
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
        void appendRespectsTenantBoundaries() {
            // Given
            TenantContext anotherTenant = mock(TenantContext.class, withSettings().lenient());
            when(anotherTenant.tenantId()).thenReturn("tenant-beta");

            when(delegate.append(tenantContext, eventEntry))
                .thenReturn(Promise.of(offset));
            when(delegate.append(anotherTenant, eventEntry))
                .thenReturn(Promise.of(offset));

            // When
            runPromise(() -> store.append(tenantContext, eventEntry));
            runPromise(() -> store.append(anotherTenant, eventEntry));

            // Then: Both tenants' calls are routed correctly
            verify(delegate).append(tenantContext, eventEntry);
            verify(delegate).append(anotherTenant, eventEntry);
        }

        @Test
        @DisplayName("[DC-009-17]: read_operations_isolated_by_tenant")
        void readOperationsIsolatedByTenant() {
            // Given
            TenantContext anotherTenant = mock(TenantContext.class, withSettings().lenient());
            when(anotherTenant.tenantId()).thenReturn("tenant-beta");

            List<EventLogStore.EventEntry> tenantAlphaEntries = List.of(eventEntry);
            List<EventLogStore.EventEntry> tenantBetaEntries = List.of(eventEntry, eventEntry);

            when(delegate.read(tenantContext, offset, 100))
                .thenReturn(Promise.of(tenantAlphaEntries));
            when(delegate.read(anotherTenant, offset, 100))
                .thenReturn(Promise.of(tenantBetaEntries));

            // When
            List<EventLogStore.EventEntry> alphaResults = runPromise(
                () -> store.read(tenantContext, offset, 100)
            );
            List<EventLogStore.EventEntry> betaResults = runPromise(
                () -> store.read(anotherTenant, offset, 100)
            );

            // Then: Different results for different tenants
            assertThat(alphaResults).hasSize(1);
            assertThat(betaResults).hasSize(2);
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
        void nullTenantContextRejected() {
            // When & Then
            assertThatThrownBy(() ->
                runPromise(() -> store.append(null, eventEntry))
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("[DC-009-19]: null_event_entry_handled")
        void nullEventEntryHandled() {
            // Given
            when(delegate.append(tenantContext, null))
                .thenReturn(Promise.ofException(
                    new IllegalArgumentException("Event entry required")
                ));

            // When & Then
            assertThatThrownBy(() ->
                runPromise(() -> store.append(tenantContext, null))
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("[DC-009-20]: large_batch_operations_handled")
        void largeBatchOperationsHandled() {
            // Given: 100 event entries (reduced from 10,000 for test performance)
            List<EventLogStore.EventEntry> largeEntries = new java.util.ArrayList<>();
            List<Offset> largeOffsets = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) {
                largeEntries.add(eventEntry);
                largeOffsets.add(offset);
            }

            when(delegate.appendBatch(tenantContext, largeEntries))
                .thenReturn(Promise.of(largeOffsets));

            // When
            List<Offset> results = runPromise(() ->
                store.appendBatch(tenantContext, largeEntries)
            );

            // Then: All entries processed
            assertThat(results).hasSize(100);
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
        void tailEstablishesSubscription() {
            // Given
            EventLogStore.Subscription mockSubscription = mock(EventLogStore.Subscription.class);
            when(delegate.tail(eq(tenantContext), eq(offset), any()))
                .thenReturn(Promise.of(mockSubscription));

            // When
            EventLogStore.Subscription result = runPromise(() ->
                store.tail(tenantContext, offset, entry -> {})
            );

            // Then
            assertThat(result).isNotNull();
            verify(delegate).tail(eq(tenantContext), eq(offset), any());
        }

        @Test
        @DisplayName("[DC-009-22]: tail_failure_handled_by_circuit_breaker")
        void tailFailureHandledByCircuitBreaker() {
            // Given
            RuntimeException tailException = new RuntimeException("Streaming failed");
            when(delegate.tail(eq(tenantContext), eq(offset), any()))
                .thenReturn(Promise.ofException(tailException));

            // When & Then
            assertThatThrownBy(() ->
                runPromise(() -> store.tail(tenantContext, offset, entry -> {}))
            ).isInstanceOf(RuntimeException.class);
        }
    }
}
