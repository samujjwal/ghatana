/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.compliance.service;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.appplatform.compliance.domain.*;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LockInPeriodService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for lock-in period rule evaluation (D07-004, D07-005)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LockInPeriodService — Unit Tests")
class LockInPeriodServiceTest extends EventloopTestBase {

    @Mock private LockInPeriodService.LockInStore lockInStore;

    private LockInPeriodService service;

    private static final String CLIENT    = "client-001";
    private static final String INSTR     = "instr-NEPSE-NICA";
    private static final String TODAY_BS  = "2081-10-15";

    @BeforeEach
    void setUp() {
        when(lockInStore.getTodayBs()).thenReturn(TODAY_BS);
        service = new LockInPeriodService(lockInStore, Executors.newSingleThreadExecutor());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ComplianceCheckRequest sellRequest(BigDecimal quantity) {
        return new ComplianceCheckRequest(
                "order-1", CLIENT, INSTR, "acct-1", "NPL",
                "SELL", quantity, new BigDecimal("500.00"),
                quantity.multiply(new BigDecimal("500.00")), "VERIFIED", 10);
    }

    private LockInRecord activeLockIn(BigDecimal qty) {
        return new LockInRecord("lock-1", CLIENT, INSTR, qty,
                "2081-01-01", "2081-12-31",
                LockInType.IPO, Instant.now());
    }

    // ─── No lock-in tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("no active lock-in records — PASS")
    void noActiveLockIns_pass() {
        when(lockInStore.findActive(eq(CLIENT), eq(INSTR), eq(TODAY_BS)))
                .thenReturn(Promise.of(List.of()));

        var detail = runPromise(() -> Promise.of(service.evaluate(sellRequest(new BigDecimal("100")))));

        assertThat(detail.ruleId()).isEqualTo("LOCK_IN_CHECK");
        assertThat(detail.result()).isEqualTo(ComplianceStatus.PASS);
        assertThat(detail.reason()).isNull();
    }

    // ─── Lock-in restriction tests ─────────────────────────────────────────────

    @Test
    @DisplayName("locked qty covers sold qty — FAIL with reason")
    void lockedQtyExceedsAvailable_fail() {
        // Position = 100, locked = 80, available = 20, requested = 50
        when(lockInStore.findActive(eq(CLIENT), eq(INSTR), eq(TODAY_BS)))
                .thenReturn(Promise.of(List.of(activeLockIn(new BigDecimal("80")))));
        when(lockInStore.getCurrentPosition(eq(CLIENT), eq(INSTR)))
                .thenReturn(Promise.of(new BigDecimal("100")));

        var detail = runPromise(() -> Promise.of(service.evaluate(sellRequest(new BigDecimal("50")))));

        assertThat(detail.ruleId()).isEqualTo("LOCK_IN_CHECK");
        assertThat(detail.result()).isEqualTo(ComplianceStatus.FAIL);
        assertThat(detail.reason()).isNotBlank();
        assertThat(detail.reason()).contains("available");
        assertThat(detail.reason()).contains("50"); // requested qty in reason
    }

    @Test
    @DisplayName("sufficient available shares after lock-in deduction — PASS")
    void sufficientAvailableShares_pass() {
        // Position = 200, locked = 80, available = 120, requested = 100
        when(lockInStore.findActive(eq(CLIENT), eq(INSTR), eq(TODAY_BS)))
                .thenReturn(Promise.of(List.of(activeLockIn(new BigDecimal("80")))));
        when(lockInStore.getCurrentPosition(eq(CLIENT), eq(INSTR)))
                .thenReturn(Promise.of(new BigDecimal("200")));

        var detail = runPromise(() -> Promise.of(service.evaluate(sellRequest(new BigDecimal("100")))));

        assertThat(detail.ruleId()).isEqualTo("LOCK_IN_CHECK");
        assertThat(detail.result()).isEqualTo(ComplianceStatus.PASS);
    }

    @Test
    @DisplayName("exactly at available limit — PASS (boundary)")
    void exactlyAtLimit_pass() {
        // Position = 100, locked = 0, available = 100, requesting exactly 100
        when(lockInStore.findActive(eq(CLIENT), eq(INSTR), eq(TODAY_BS)))
                .thenReturn(Promise.of(List.of(activeLockIn(BigDecimal.ZERO))));
        when(lockInStore.getCurrentPosition(eq(CLIENT), eq(INSTR)))
                .thenReturn(Promise.of(new BigDecimal("100")));

        var detail = runPromise(() -> Promise.of(service.evaluate(sellRequest(new BigDecimal("100")))));

        assertThat(detail.result()).isEqualTo(ComplianceStatus.PASS);
    }

    @Test
    @DisplayName("multiple lock-in records — total locked qty aggregated correctly")
    void multipleLockIns_aggregated() {
        // Two lock-ins of 40 each = 80 locked; position=100, available=20, requesting 50 → FAIL
        var lock1 = new LockInRecord("lock-1", CLIENT, INSTR, new BigDecimal("40"),
                "2081-01-01", "2081-12-31", LockInType.IPO, Instant.now());
        var lock2 = new LockInRecord("lock-2", CLIENT, INSTR, new BigDecimal("40"),
                "2081-01-01", "2081-12-31", LockInType.IPO, Instant.now());

        when(lockInStore.findActive(eq(CLIENT), eq(INSTR), eq(TODAY_BS)))
                .thenReturn(Promise.of(List.of(lock1, lock2)));
        when(lockInStore.getCurrentPosition(eq(CLIENT), eq(INSTR)))
                .thenReturn(Promise.of(new BigDecimal("100")));

        var detail = runPromise(() -> Promise.of(service.evaluate(sellRequest(new BigDecimal("50")))));

        assertThat(detail.result()).isEqualTo(ComplianceStatus.FAIL);
    }

    @Test
    @DisplayName("ruleId is always LOCK_IN_CHECK regardless of outcome")
    void ruleId_alwaysLockInCheck() {
        when(lockInStore.findActive(eq(CLIENT), eq(INSTR), eq(TODAY_BS)))
                .thenReturn(Promise.of(List.of()));

        var detail = runPromise(() -> Promise.of(service.evaluate(sellRequest(new BigDecimal("10")))));

        assertThat(detail.ruleId()).isEqualTo("LOCK_IN_CHECK");
    }
}
