/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import com.ghatana.appplatform.eventstore.saga.SagaDefinition;
import com.ghatana.appplatform.eventstore.saga.SagaStep;
import com.ghatana.appplatform.eventstore.saga.SagaStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DtcSagaPolicies} (K17-007).
 *
 * <p>Tests confirm that all three DTC saga definitions are correctly specified
 * and can be registered into the K-05 {@link SagaStore} on startup.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DtcSagaPolicies (K17-007)")
class DtcSagaPoliciesTest {

    @Mock
    private SagaStore sagaStore;

    // ── dtc_dvpSaga_registered ────────────────────────────────────────────────

    @Test
    @DisplayName("dtc_dvpSaga_registered: DVP settlement saga has correct type, version, and 4 steps")
    void dtc_dvpSaga_registered() {
        SagaDefinition dvp = DtcSagaPolicies.dvpSettlement();

        assertThat(dvp.sagaType()).isEqualTo(DtcSagaPolicies.DVP_SETTLEMENT);
        assertThat(dvp.version()).isEqualTo(DtcSagaPolicies.POLICY_VERSION);
        assertThat(dvp.totalSteps()).isEqualTo(4);

        // Verify step order and event types
        SagaStep lock    = dvp.stepAt(0);
        SagaStep deliver = dvp.stepAt(1);
        SagaStep pay     = dvp.stepAt(2);
        SagaStep confirm = dvp.stepAt(3);

        assertThat(lock.stepName()).isEqualTo("lock-assets");
        assertThat(lock.actionEventType()).isEqualTo("dtc.dvp.lock.requested");
        assertThat(lock.completionEventType()).isEqualTo("dtc.dvp.lock.completed");
        assertThat(lock.hasCompensation()).isTrue();
        assertThat(lock.compensationEventType()).isEqualTo("dtc.dvp.lock.reversed");

        assertThat(deliver.stepName()).isEqualTo("deliver-securities");
        assertThat(deliver.hasCompensation()).isTrue();

        assertThat(pay.stepName()).isEqualTo("pay-cash");
        assertThat(pay.hasCompensation()).isTrue();

        // Confirm is idempotent — no compensation
        assertThat(confirm.stepName()).isEqualTo("confirm-settlement");
        assertThat(confirm.hasCompensation()).isFalse();
        assertThat(confirm.mandatory()).isTrue();
    }

    // ── dtc_fundTransferSaga_registered ──────────────────────────────────────

    @Test
    @DisplayName("dtc_fundTransferSaga_registered: fund transfer saga has correct type, version, and 3 steps")
    void dtc_fundTransferSaga_registered() {
        SagaDefinition transfer = DtcSagaPolicies.fundTransfer();

        assertThat(transfer.sagaType()).isEqualTo(DtcSagaPolicies.FUND_TRANSFER);
        assertThat(transfer.version()).isEqualTo(DtcSagaPolicies.POLICY_VERSION);
        assertThat(transfer.totalSteps()).isEqualTo(3);

        SagaStep debit  = transfer.stepAt(0);
        SagaStep credit = transfer.stepAt(1);
        SagaStep notify = transfer.stepAt(2);

        assertThat(debit.stepName()).isEqualTo("debit-source");
        assertThat(debit.actionEventType()).isEqualTo("dtc.transfer.debit.requested");
        assertThat(debit.hasCompensation()).isTrue();
        assertThat(debit.compensationEventType()).isEqualTo("dtc.transfer.debit.reversed");

        assertThat(credit.stepName()).isEqualTo("credit-destination");
        assertThat(credit.hasCompensation()).isTrue();
        assertThat(credit.compensationEventType()).isEqualTo("dtc.transfer.credit.reversed");

        // Notify is best-effort — no compensation, not mandatory
        assertThat(notify.stepName()).isEqualTo("notify-parties");
        assertThat(notify.hasCompensation()).isFalse();
        assertThat(notify.mandatory()).isFalse();
    }

    @Test
    @DisplayName("dtc_fundTransferSaga_registered: corporate action saga has correct type and 3 steps")
    void dtc_corporateActionSaga_registered() {
        SagaDefinition ca = DtcSagaPolicies.corporateAction();

        assertThat(ca.sagaType()).isEqualTo(DtcSagaPolicies.CORPORATE_ACTION);
        assertThat(ca.version()).isEqualTo(DtcSagaPolicies.POLICY_VERSION);
        assertThat(ca.totalSteps()).isEqualTo(3);

        SagaStep announce   = ca.stepAt(0);
        SagaStep record     = ca.stepAt(1);
        SagaStep distribute = ca.stepAt(2);

        // Announce is informational — not mandatory, no compensation
        assertThat(announce.stepName()).isEqualTo("announce-event");
        assertThat(announce.mandatory()).isFalse();
        assertThat(announce.hasCompensation()).isFalse();

        assertThat(record.stepName()).isEqualTo("record-entitlements");
        assertThat(record.hasCompensation()).isTrue();
        assertThat(record.compensationEventType()).isEqualTo("dtc.ca.record.reversed");

        assertThat(distribute.stepName()).isEqualTo("distribute-proceeds");
        assertThat(distribute.hasCompensation()).isTrue();
        assertThat(distribute.compensationEventType()).isEqualTo("dtc.ca.distribute.reversed");
    }

    // ── dtc_invalidPolicy_rejected ────────────────────────────────────────────

    @Test
    @DisplayName("dtc_invalidPolicy_rejected: SagaDefinition rejects empty step list")
    void dtc_invalidPolicy_rejected() {
        assertThatThrownBy(() ->
            new SagaDefinition("dtc.bad-saga", 1, "invalid", List.of())
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("at least one step");
    }

    @Test
    @DisplayName("dtc_invalidPolicy_rejected: SagaDefinition rejects null sagaType")
    void dtc_invalidPolicy_nullType_rejected() {
        assertThatThrownBy(() ->
            new SagaDefinition(null, 1, "bad", List.of(SagaStep.of("s", 0, "a", "b")))
        ).isInstanceOf(NullPointerException.class);
    }

    // ── dtc_policyVersioning ──────────────────────────────────────────────────

    @Test
    @DisplayName("dtc_policyVersioning: all policies have the expected version number")
    void dtc_policyVersioning() {
        List<SagaDefinition> all = DtcSagaPolicies.allDefinitions();
        assertThat(all).hasSize(3);
        assertThat(all).allMatch(d -> d.version() == DtcSagaPolicies.POLICY_VERSION);
    }

    @Test
    @DisplayName("dtc_policyVersioning: allDefinitions returns all three distinct saga types")
    void dtc_policyVersioning_distinctTypes() {
        List<SagaDefinition> all = DtcSagaPolicies.allDefinitions();

        assertThat(all).extracting(SagaDefinition::sagaType)
            .containsExactlyInAnyOrder(
                DtcSagaPolicies.DVP_SETTLEMENT,
                DtcSagaPolicies.FUND_TRANSFER,
                DtcSagaPolicies.CORPORATE_ACTION
            );
    }

    // ── dtc_startupRegistration ───────────────────────────────────────────────

    @Test
    @DisplayName("dtc_startupRegistration: registerAll calls saveDefinition exactly 3 times")
    void dtc_startupRegistration() {
        DtcSagaPolicies.registerAll(sagaStore);

        verify(sagaStore, times(3)).saveDefinition(org.mockito.ArgumentMatchers.any(SagaDefinition.class));
    }

    @Test
    @DisplayName("dtc_startupRegistration: registerAll saves all three DTC policy types")
    void dtc_startupRegistration_correctTypes() {
        List<SagaDefinition> captured = new java.util.ArrayList<>();

        // Capture arguments manually via answer
        org.mockito.Mockito.doAnswer(inv -> {
            captured.add(inv.getArgument(0));
            return null;
        }).when(sagaStore).saveDefinition(org.mockito.ArgumentMatchers.any());

        DtcSagaPolicies.registerAll(sagaStore);

        assertThat(captured).extracting(SagaDefinition::sagaType)
            .containsExactlyInAnyOrder(
                DtcSagaPolicies.DVP_SETTLEMENT,
                DtcSagaPolicies.FUND_TRANSFER,
                DtcSagaPolicies.CORPORATE_ACTION
            );
    }

    @Test
    @DisplayName("dtc_startupRegistration: registerAll rejects null store")
    void dtc_startupRegistration_nullStoreRejected() {
        assertThatThrownBy(() -> DtcSagaPolicies.registerAll(null))
            .isInstanceOf(NullPointerException.class);
    }
}
