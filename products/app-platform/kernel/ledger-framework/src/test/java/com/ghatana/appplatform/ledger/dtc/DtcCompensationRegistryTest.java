/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import com.ghatana.appplatform.ledger.dtc.DtcCompensationRegistry.CompensationCallback;
import com.ghatana.appplatform.ledger.dtc.DtcCompensationRegistry.CompensationResult;
import com.ghatana.appplatform.ledger.dtc.DtcCompensationRegistry.ManualReviewAlert;
import com.ghatana.appplatform.ledger.dtc.DtcSagaCoordinator.DtcSettlementContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DtcCompensationRegistry} (STORY-K17-009).
 *
 * <p>Covers:
 * <ul>
 *   <li>DVP lock-reversal callback invocation</li>
 *   <li>Fund credit-back callback invocation</li>
 *   <li>DTC-level idempotency guard (no double-reversal on K-05 retry)</li>
 *   <li>Manual review alert when COMPENSATION_FAILED received</li>
 *   <li>Audit marker in result message</li>
 * </ul>
 */
@DisplayName("DtcCompensationRegistry — K17-009")
class DtcCompensationRegistryTest {

    private static final String TENANT   = "TENANT-DTC-001";
    private static final String SAGA_ID  = "SAGA-DVP-XYZ-001";
    private static final String SETTLE   = "SETTLE-001";

    private DtcCompensationRegistry registry;
    private DtcSettlementContext ctx;

    @BeforeEach
    void setUp() {
        registry = new DtcCompensationRegistry();
        ctx = new DtcSettlementContext(
            TENANT, SETTLE, "BATCH-001", "CPTY-NSCCB", Map.of("sro", "SEBON"));
    }

    // ── AC1: DVP lock-reversal callback invoked by K-05 ──────────────────────

    @Test
    @DisplayName("dtc_lockReversal_callback — DVP lock-reversal callback succeeds")
    void dtc_lockReversal_callback() {
        AtomicInteger callCount = new AtomicInteger();
        registry.register(DtcSagaPolicies.DVP_LOCK_REVERSED,
            (sagaId, stepName, payload) -> {
                callCount.incrementAndGet();
                assertThat(sagaId).isEqualTo(SAGA_ID);
                assertThat(stepName).isEqualTo("lock-assets");
                return CompensationResult.ok("Securities unlocked: sagaId=" + sagaId);
            });

        CompensationResult result = registry.invoke(
            SAGA_ID, "lock-assets", DtcSagaPolicies.DVP_LOCK_REVERSED,
            Map.of("instrumentId", "NABIL", "quantity", 500));

        assertThat(result.success()).isTrue();
        assertThat(result.idempotent()).isFalse();
        assertThat(result.message()).contains("Securities unlocked");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("dtc_creditBack_callback — Fund credit-back callback invoked correctly")
    void dtc_creditBack_callback() {
        AtomicInteger callCount = new AtomicInteger();
        registry.register(DtcSagaPolicies.FUND_CREDIT_REVERSED,
            (sagaId, stepName, payload) -> {
                callCount.incrementAndGet();
                assertThat(stepName).isEqualTo("credit-destination");
                return CompensationResult.ok("Credit-back posted: account=" + payload.get("accountId"));
            });

        CompensationResult result = registry.invoke(
            SAGA_ID, "credit-destination", DtcSagaPolicies.FUND_CREDIT_REVERSED,
            Map.of("accountId", "ACC-DEST-88", "amount", "50000"));

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Credit-back posted");
        assertThat(callCount.get()).isEqualTo(1);
    }

    // ── AC2: Idempotency — no double-reversal on K-05 retry ──────────────────

    @Test
    @DisplayName("dtc_compensation_idempotent — Second invocation returns cached result")
    void dtc_compensation_idempotent() {
        AtomicInteger callCount = new AtomicInteger();
        registry.register(DtcSagaPolicies.DVP_LOCK_REVERSED,
            (sagaId, stepName, payload) -> {
                callCount.incrementAndGet();
                return CompensationResult.ok("Lock reversed on first call");
            });

        // First call executes the callback
        CompensationResult first = registry.invoke(
            SAGA_ID, "lock-assets", DtcSagaPolicies.DVP_LOCK_REVERSED, Map.of());
        // Second call (K-05 retry) must NOT re-execute the callback
        CompensationResult second = registry.invoke(
            SAGA_ID, "lock-assets", DtcSagaPolicies.DVP_LOCK_REVERSED, Map.of());

        assertThat(first.idempotent()).isFalse();
        assertThat(second.idempotent()).isTrue();
        assertThat(second.success()).isTrue();
        assertThat(second.message()).isEqualTo("Lock reversed on first call");
        assertThat(callCount.get()).isEqualTo(1); // callback executed ONCE only
    }

    @Test
    @DisplayName("dtc_compensation_idempotent_differentStep — Same saga, different step: separate")
    void dtc_compensation_idempotent_differentStep() {
        registry.register(DtcSagaPolicies.DVP_LOCK_REVERSED,
            (sagaId, stepName, payload) -> CompensationResult.ok("lock reversed"));
        registry.register(DtcSagaPolicies.DVP_DELIVER_REVERSED,
            (sagaId, stepName, payload) -> CompensationResult.ok("deliver reversed"));

        CompensationResult lockResult = registry.invoke(
            SAGA_ID, "lock-assets", DtcSagaPolicies.DVP_LOCK_REVERSED, Map.of());
        CompensationResult deliverResult = registry.invoke(
            SAGA_ID, "deliver-securities", DtcSagaPolicies.DVP_DELIVER_REVERSED, Map.of());

        // Both are fresh executions (different step names = different idempotency keys)
        assertThat(lockResult.idempotent()).isFalse();
        assertThat(deliverResult.idempotent()).isFalse();
    }

    @Test
    @DisplayName("dtc_compensation_failedNotCached — Failed result not cached; retry executes again")
    void dtc_compensation_failedNotCached() {
        AtomicInteger callCount = new AtomicInteger();
        registry.register(DtcSagaPolicies.DVP_LOCK_REVERSED, (sagaId, stepName, payload) -> {
            int n = callCount.incrementAndGet();
            if (n == 1) return CompensationResult.failure("Transient error");
            return CompensationResult.ok("Recovered");
        });

        CompensationResult first = registry.invoke(
            SAGA_ID, "lock-assets", DtcSagaPolicies.DVP_LOCK_REVERSED, Map.of());
        CompensationResult second = registry.invoke(
            SAGA_ID, "lock-assets", DtcSagaPolicies.DVP_LOCK_REVERSED, Map.of());

        assertThat(first.success()).isFalse();
        assertThat(second.success()).isTrue();
        assertThat(second.idempotent()).isFalse(); // Freshly executed (failure wasn't cached)
        assertThat(callCount.get()).isEqualTo(2);
    }

    // ── AC3: Manual review alert ──────────────────────────────────────────────

    @Test
    @DisplayName("dtc_compensationFailed_manualAlert — Alert raised with regulatory context")
    void dtc_compensationFailed_manualAlert() {
        List<ManualReviewAlert> alerts = new ArrayList<>();
        registry.addManualReviewListener(alerts::add);

        registry.onCompensationFailed(
            SAGA_ID, "lock-assets", Map.of("instrumentId", "NABIL"), ctx);

        assertThat(alerts).hasSize(1);
        ManualReviewAlert alert = alerts.get(0);
        assertThat(alert.sagaId()).isEqualTo(SAGA_ID);
        assertThat(alert.stepName()).isEqualTo("lock-assets");
        assertThat(alert.context()).isEqualTo(ctx);
        assertThat(alert.context().regulatoryFlags()).containsKey("sro");
        assertThat(alert.reason()).contains("manual intervention required");
    }

    @Test
    @DisplayName("dtc_compensationFailed_multipleListeners — All listeners notified")
    void dtc_compensationFailed_multipleListeners() {
        List<ManualReviewAlert> listenerA = new ArrayList<>();
        List<ManualReviewAlert> listenerB = new ArrayList<>();
        registry.addManualReviewListener(listenerA::add);
        registry.addManualReviewListener(listenerB::add);

        registry.onCompensationFailed(
            SAGA_ID, "lock-assets", Map.of(), ctx);

        assertThat(listenerA).hasSize(1);
        assertThat(listenerB).hasSize(1);
    }

    @Test
    @DisplayName("dtc_compensation_audit_logged — Result message contains audit marker")
    void dtc_compensation_audit_logged() {
        registry.register(DtcSagaPolicies.FUND_DEBIT_REVERSED,
            (sagaId, stepName, payload) ->
                CompensationResult.ok("Debit reversed; sagaId=" + sagaId + "; step=" + stepName));

        CompensationResult result = registry.invoke(
            SAGA_ID, "debit-source", DtcSagaPolicies.FUND_DEBIT_REVERSED,
            Map.of("accountId", "ACC-SRC-01", "amount", "10000"));

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("sagaId=" + SAGA_ID);
        assertThat(result.message()).contains("step=debit-source");
    }

    @Test
    @DisplayName("dtc_noCallback_registered — Returns failure gracefully")
    void dtc_noCallback_registered() {
        CompensationResult result = registry.invoke(
            SAGA_ID, "lock-assets", "dtc.unknown.event", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("No DTC compensation callback registered");
    }
}
