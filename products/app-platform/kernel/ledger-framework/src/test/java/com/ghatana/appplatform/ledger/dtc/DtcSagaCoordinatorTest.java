/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import com.ghatana.appplatform.eventstore.saga.SagaInstance;
import com.ghatana.appplatform.eventstore.saga.SagaOrchestrator;
import com.ghatana.appplatform.eventstore.saga.SagaState;
import com.ghatana.appplatform.eventstore.saga.SagaStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DtcSagaCoordinator} (STORY-K17-008).
 *
 * <p>Verifies that DTC correctly delegates to the K-05 saga engine, maps state
 * transitions, and carries DTC-specific enrichment through the saga lifecycle.
 *
 * @doc.type class
 * @doc.purpose Unit tests for DTC saga coordination layer using K-05 engine (K17-008)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DtcSagaCoordinator — K-05 saga integration and state mapping")
class DtcSagaCoordinatorTest {

    @Mock SagaOrchestrator orchestrator;
    @Mock SagaStore sagaStore;

    private DtcSagaCoordinator coordinator;

    private static final String TENANT          = "tenant-dtc-001";
    private static final String SETTLEMENT_ID   = "settle-abc-001";
    private static final String BATCH_ID        = "batch-2026-001";
    private static final String COUNTERPARTY_ID = "cpty-broker-007";

    @BeforeEach
    void setUp() {
        coordinator = new DtcSagaCoordinator(orchestrator, sagaStore);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SagaInstance stubInstance(String sagaId, SagaState state) {
        return new SagaInstance(sagaId, DtcSagaPolicies.DVP_SETTLEMENT, 1,
                TENANT, SETTLEMENT_ID, state, 0, 0, null, Instant.now(), Instant.now());
    }

    private DtcSagaCoordinator.DtcSettlementContext settlementCtx() {
        return new DtcSagaCoordinator.DtcSettlementContext(
                TENANT, SETTLEMENT_ID, BATCH_ID, COUNTERPARTY_ID,
                Map.of("reporting_obligation", true));
    }

    // ── AC1: Trigger saga via K-05 ────────────────────────────────────────────

    @Test
    @DisplayName("dtc_triggerSaga_viaK05: startDvpSettlement triggers K-05 startSaga with DVP type")
    void dtc_triggerSaga_viaK05() {
        SagaInstance instance = stubInstance("saga-dvp-001", SagaState.STEP_PENDING);
        when(orchestrator.startSaga(
                eq(DtcSagaPolicies.DVP_SETTLEMENT), eq(TENANT), eq(SETTLEMENT_ID)))
                .thenReturn(instance);

        DtcSagaCoordinator.DtcSagaHandle handle =
                coordinator.startDvpSettlement(settlementCtx());

        assertThat(handle.sagaId()).isEqualTo("saga-dvp-001");
        assertThat(handle.transactionState())
                .isEqualTo(DtcSagaCoordinator.DtcTransactionState.IN_PROGRESS);
        verify(orchestrator).startSaga(DtcSagaPolicies.DVP_SETTLEMENT, TENANT, SETTLEMENT_ID);
    }

    @Test
    @DisplayName("dtc_triggerSaga_viaK05: startFundTransfer uses FUND_TRANSFER saga type")
    void dtc_triggerSaga_fundTransfer_viaK05() {
        SagaInstance instance = stubInstance("saga-ft-001", SagaState.STEP_PENDING);
        when(orchestrator.startSaga(
                eq(DtcSagaPolicies.FUND_TRANSFER), eq(TENANT), eq(SETTLEMENT_ID)))
                .thenReturn(instance);

        DtcSagaCoordinator.DtcSagaHandle handle =
                coordinator.startFundTransfer(settlementCtx());

        assertThat(handle.sagaId()).isEqualTo("saga-ft-001");
        verify(orchestrator).startSaga(DtcSagaPolicies.FUND_TRANSFER, TENANT, SETTLEMENT_ID);
    }

    // ── AC2: Compensating state update ────────────────────────────────────────

    @Test
    @DisplayName("dtc_compensating_stateUpdate: step failure maps to COMPENSATING and notifies listeners")
    void dtc_compensating_stateUpdate() {
        List<DtcSagaCoordinator.DtcSagaHandle> captured = new ArrayList<>();
        coordinator.addListener(captured::add);

        DtcSagaCoordinator.DtcSagaHandle handle =
                coordinator.onSagaCompensating("saga-001", "step-2 failed: timeout", settlementCtx());

        assertThat(handle.transactionState())
                .isEqualTo(DtcSagaCoordinator.DtcTransactionState.COMPENSATING);
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).transactionState())
                .isEqualTo(DtcSagaCoordinator.DtcTransactionState.COMPENSATING);
        verify(orchestrator).onStepFailed("saga-001", "step-2 failed: timeout");
    }

    @Test
    @DisplayName("dtc_compensating_stateUpdate: multiple listeners all notified on state change")
    void dtc_compensating_multipleListeners() {
        List<DtcSagaCoordinator.DtcSagaHandle> first  = new ArrayList<>();
        List<DtcSagaCoordinator.DtcSagaHandle> second = new ArrayList<>();
        coordinator.addListener(first::add);
        coordinator.addListener(second::add);

        coordinator.onSagaCompensating("saga-001", "fail", settlementCtx());

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
    }

    // ── AC3: K-05 restart reconciliation ─────────────────────────────────────

    @Test
    @DisplayName("dtc_k05Restart_reconcile: reconcileInProgressSagas returns RECONCILED handles")
    void dtc_k05Restart_reconcile() {
        SagaInstance s1 = stubInstance("saga-001", SagaState.STEP_PENDING);
        SagaInstance s2 = stubInstance("saga-002", SagaState.COMPENSATING);
        when(sagaStore.findInstance("saga-001")).thenReturn(Optional.of(s1));
        when(sagaStore.findInstance("saga-002")).thenReturn(Optional.of(s2));

        List<DtcSagaCoordinator.DtcSagaHandle> handles =
                coordinator.reconcileInProgressSagas(List.of("saga-001", "saga-002"));

        assertThat(handles).hasSize(2);
        assertThat(handles).allMatch(
                h -> h.transactionState() == DtcSagaCoordinator.DtcTransactionState.RECONCILED,
                "all reconciled handles must be in RECONCILED state");
        // Saga IDs preserved
        assertThat(handles.stream().map(DtcSagaCoordinator.DtcSagaHandle::sagaId).toList())
                .containsExactlyInAnyOrder("saga-001", "saga-002");
    }

    @Test
    @DisplayName("dtc_k05Restart_reconcile: saga not found in store is silently skipped")
    void dtc_k05Restart_reconcile_missingSkipped() {
        when(sagaStore.findInstance("saga-gone")).thenReturn(Optional.empty());

        List<DtcSagaCoordinator.DtcSagaHandle> handles =
                coordinator.reconcileInProgressSagas(List.of("saga-gone"));

        assertThat(handles).isEmpty();
    }

    // ── AC4: batch linkage ────────────────────────────────────────────────────

    @Test
    @DisplayName("dtc_batchLinkage: batchId is carried through the saga handle")
    void dtc_batchLinkage() {
        SagaInstance instance = stubInstance("saga-batch-001", SagaState.STEP_PENDING);
        when(orchestrator.startSaga(any(), any(), any())).thenReturn(instance);

        DtcSagaCoordinator.DtcSagaHandle handle = coordinator.startDvpSettlement(settlementCtx());

        assertThat(handle.context().batchId()).isEqualTo(BATCH_ID);
    }

    // ── AC5: counterparty enrichment ──────────────────────────────────────────

    @Test
    @DisplayName("dtc_counterpartyEnrichment: counterpartyId is preserved in the saga handle")
    void dtc_counterpartyEnrichment() {
        SagaInstance instance = stubInstance("saga-cpty-001", SagaState.STEP_PENDING);
        when(orchestrator.startSaga(any(), any(), any())).thenReturn(instance);

        DtcSagaCoordinator.DtcSagaHandle handle = coordinator.startDvpSettlement(settlementCtx());

        assertThat(handle.context().counterpartyId()).isEqualTo(COUNTERPARTY_ID);
    }

    @Test
    @DisplayName("dtc_counterpartyEnrichment: regulatory flags are preserved")
    void dtc_counterpartyEnrichment_regulatoryFlags() {
        SagaInstance instance = stubInstance("saga-reg-001", SagaState.STEP_PENDING);
        when(orchestrator.startSaga(any(), any(), any())).thenReturn(instance);

        DtcSagaCoordinator.DtcSagaHandle handle = coordinator.startDvpSettlement(settlementCtx());

        assertThat(handle.context().regulatoryFlags())
                .containsEntry("reporting_obligation", true);
    }
}
