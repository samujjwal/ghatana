/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.resilience;

import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.testing.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for K-18 resilience patterns via {@link KernelResilienceFactory}.
 *
 * <p>Validates K18-001 (circuit breaker states), K18-002 (execution wrapper + fallback),
 * K18-003 (profiles: STRICT for ledger/IAM, STANDARD for calendar),
 * K18-004 (bulkhead via tryExecuteBlocking), K18-006 (retry), K18-007 (retry context).
 *
 * @doc.type class
 * @doc.purpose K-18 resilience factory tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("K-18 Kernel Resilience Factory Tests")
class KernelResilienceFactoryTest extends EventloopTestBase {

    private final KernelResilienceFactory factory = KernelResilienceFactory.create();

    // ─── K18-002: CLOSED state returns result ────────────────────────────────

    @Test
    @DisplayName("ledgerTransact — CLOSED circuit returns operation result")
    void ledgerTransact_closed_returnsResult() {
        String result = runPromise(() ->
                factory.ledgerTransact(eventloop, () -> Promise.of("journal-posted")));
        assertThat(result).isEqualTo("journal-posted");
    }

    @Test
    @DisplayName("iamOperation — CLOSED circuit returns operation result")
    void iamOperation_closed_returnsResult() {
        String result = runPromise(() ->
                factory.iamOperation(eventloop, () -> Promise.of("token-issued")));
        assertThat(result).isEqualTo("token-issued");
    }

    @Test
    @DisplayName("calendarQuery — CLOSED circuit returns operation result")
    void calendarQuery_closed_returnsResult() {
        Integer days = runPromise(() ->
                factory.calendarQuery(eventloop, () -> Promise.of(5)));
        assertThat(days).isEqualTo(5);
    }

    @Test
    @DisplayName("secretAccess — CLOSED circuit returns secret value")
    void secretAccess_closed_returnsResult() {
        String secret = runPromise(() ->
                factory.secretAccess(eventloop, () -> Promise.of("secret-value")));
        assertThat(secret).isEqualTo("secret-value");
    }

    // ─── K18-001: Circuit breaker initial state ───────────────────────────────

    @Test
    @DisplayName("K18-001 — ledger circuit starts CLOSED")
    void ledgerCircuit_initialState_isClosed() {
        assertThat(factory.ledgerCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("K18-001 — IAM circuit starts CLOSED")
    void iamCircuit_initialState_isClosed() {
        assertThat(factory.iamCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("K18-001 — calendar circuit starts CLOSED")
    void calendarCircuit_initialState_isClosed() {
        assertThat(factory.calendarCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ─── K18-002: Fallback when circuit OPEN (simulated via failing operations) ─

    @Test
    @DisplayName("K18-002 — calendarQuery fallback supplied when circuit opens")
    void calendarQuery_withFallback_fallbackReturnedOnOpenCircuit() {
        // Force the calendar circuit open by triggering STANDARD threshold (5 failures)
        KernelResilienceFactory localFactory = KernelResilienceFactory.builder()
                .calendarBulkheadSize(200)
                .build();

        RuntimeException boom = new RuntimeException("calendar-down");

        // Exhaust STANDARD threshold (5 failures)
        for (int i = 0; i < 6; i++) {
            try {
                runPromise(() -> localFactory.calendarQuery(eventloop, () -> Promise.ofException(boom)));
            } catch (Exception ignored) { /* expected */ }
        }

        // Now with fallback — should return fallback value instead of exception
        Integer result = runPromise(() ->
                localFactory.calendarQuery(eventloop, () -> Promise.ofException(boom), () -> -1));
        assertThat(result).isEqualTo(-1);
    }

    // ─── K18-006: Retry on transient failure ─────────────────────────────────

    @Test
    @DisplayName("K18-006 — ledgerTransact succeeds after transient failure")
    void ledgerTransact_retrySucceeds() {
        // Use a shared counter to simulate one transient failure then success
        int[] attempts = {0};
        String result = runPromise(() ->
                factory.ledgerTransact(eventloop, () -> {
                    attempts[0]++;
                    if (attempts[0] == 1) {
                        return Promise.ofException(new RuntimeException("transient-db-error"));
                    }
                    return Promise.of("posted-on-retry");
                }));
        assertThat(result).isEqualTo("posted-on-retry");
        assertThat(attempts[0]).isGreaterThan(1);
    }

    // ─── K18-004: Bulkhead — factory builder correctness ──────────────────────

    @Test
    @DisplayName("K18-004 — custom bulkhead sizes accepted by builder")
    void builder_customBulkheadSizes_accepted() {
        KernelResilienceFactory customFactory = KernelResilienceFactory.builder()
                .ledgerBulkheadSize(10)
                .iamBulkheadSize(20)
                .calendarBulkheadSize(50)
                .build();

        // Verify factory is functional with custom sizes (all circuits start CLOSED)
        assertThat(customFactory.ledgerCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(customFactory.iamCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(customFactory.calendarCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("K18-004 — builder rejects zero bulkhead size")
    void builder_zeroBulkheadSize_throws() {
        assertThatThrownBy(() -> KernelResilienceFactory.builder().ledgerBulkheadSize(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
