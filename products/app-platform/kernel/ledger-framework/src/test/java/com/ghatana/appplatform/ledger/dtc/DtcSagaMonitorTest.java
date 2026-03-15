/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import com.ghatana.appplatform.ledger.dtc.DtcSagaCoordinator.DtcSettlementContext;
import com.ghatana.appplatform.ledger.dtc.DtcSagaMonitor.ClearingHouseListener;
import com.ghatana.appplatform.ledger.dtc.DtcSagaMonitor.ComplianceNotificationListener;
import com.ghatana.appplatform.ledger.dtc.DtcSagaMonitor.DashboardSnapshot;
import com.ghatana.appplatform.ledger.dtc.DtcSagaMonitor.DtcSagaStepTimedOut;
import com.ghatana.appplatform.ledger.dtc.DtcSagaMonitor.SagaSettlementFailed;
import com.ghatana.appplatform.ledger.dtc.DtcSagaMonitor.SrnAlertListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DtcSagaMonitor} (STORY-K17-010).
 *
 * <p>Covers:
 * <ul>
 *   <li>DVP step timeout → SRN alert (AC1)</li>
 *   <li>Global SLA timeout → SETTLEMENT_FAILED + clearing-house notification (AC2)</li>
 *   <li>Dashboard snapshot: active sagas by type, SLA breaches, compensation queue (AC3)</li>
 *   <li>Fund-transfer timeout → compliance notification</li>
 *   <li>Stuck saga alerting</li>
 * </ul>
 */
@DisplayName("DtcSagaMonitor — K17-010 DTC saga execution monitoring")
class DtcSagaMonitorTest {

    private static final String TENANT      = "TENANT-DTC-001";
    private static final String SETTLE_001  = "SETTLE-001";
    private static final String SETTLE_002  = "SETTLE-002";
    private static final String SETTLE_003  = "SETTLE-003";
    private static final String SAGA_DVP1   = "SAGA-DVP-001";
    private static final String SAGA_DVP2   = "SAGA-DVP-002";
    private static final String SAGA_FT     = "SAGA-FT-001";

    private DtcSagaMonitor monitor;
    private DtcSettlementContext dvpCtx;
    private DtcSettlementContext ftCtx;

    @BeforeEach
    void setUp() {
        monitor = new DtcSagaMonitor();
        dvpCtx = new DtcSettlementContext(TENANT, SETTLE_001, "BATCH-A", "CPTY-NSCCB",
                Map.of("sro", "SEBON"));
        ftCtx  = new DtcSettlementContext(TENANT, SETTLE_002, null, "CPTY-BOK", Map.of());
    }

    // ── AC1: DVP step timeout → SRN alert ────────────────────────────────────

    @Test
    @DisplayName("dtc_stepTimeout_srnAlert — DVP lock timeout raises SRN alert")
    void dtc_stepTimeout_srnAlert() {
        List<DtcSagaStepTimedOut> srnAlerts = new ArrayList<>();
        monitor.addSrnAlertListener(srnAlerts::add);
        monitor.trackSaga(SAGA_DVP1, DtcSagaPolicies.DVP_SETTLEMENT, dvpCtx);

        monitor.onStepTimeout(SAGA_DVP1, "lock-assets");

        assertThat(srnAlerts).hasSize(1);
        DtcSagaStepTimedOut alert = srnAlerts.get(0);
        assertThat(alert.sagaId()).isEqualTo(SAGA_DVP1);
        assertThat(alert.stepName()).isEqualTo("lock-assets");
        assertThat(alert.context()).isEqualTo(dvpCtx);
    }

    @Test
    @DisplayName("dtc_stepTimeout_srnAlert_multipleDvpSagas — Each DVP saga triggers own SRN alert")
    void dtc_stepTimeout_srnAlert_multipleDvpSagas() {
        List<DtcSagaStepTimedOut> alerts = new ArrayList<>();
        monitor.addSrnAlertListener(alerts::add);

        DtcSettlementContext dvpCtx2 = new DtcSettlementContext(
            TENANT, SETTLE_003, "BATCH-B", "CPTY-SEBON", Map.of());
        monitor.trackSaga(SAGA_DVP1, DtcSagaPolicies.DVP_SETTLEMENT, dvpCtx);
        monitor.trackSaga(SAGA_DVP2, DtcSagaPolicies.DVP_SETTLEMENT, dvpCtx2);

        monitor.onStepTimeout(SAGA_DVP1, "lock-assets");
        monitor.onStepTimeout(SAGA_DVP2, "deliver-securities");

        assertThat(alerts).hasSize(2);
        assertThat(alerts).extracting(DtcSagaStepTimedOut::sagaId)
            .containsExactly(SAGA_DVP1, SAGA_DVP2);
    }

    // ── AC2: Global timeout → SETTLEMENT_FAILED + clearing-house notification ─

    @Test
    @DisplayName("dtc_globalTimeout_clearingNotified — Global SLA breach notifies clearing house")
    void dtc_globalTimeout_clearingNotified() {
        List<SagaSettlementFailed> failedEvents = new ArrayList<>();
        monitor.addClearingHouseListener(failedEvents::add);
        monitor.trackSaga(SAGA_DVP1, DtcSagaPolicies.DVP_SETTLEMENT, dvpCtx);

        monitor.onGlobalTimeout(SAGA_DVP1);

        assertThat(failedEvents).hasSize(1);
        SagaSettlementFailed event = failedEvents.get(0);
        assertThat(event.sagaId()).isEqualTo(SAGA_DVP1);
        assertThat(event.sagaType()).isEqualTo(DtcSagaPolicies.DVP_SETTLEMENT);
        assertThat(event.reason()).containsIgnoringCase("SLA exceeded");
    }

    @Test
    @DisplayName("dtc_globalTimeout_clearingNotified_fundTransfer — FT global timeout notifies clearing")
    void dtc_globalTimeout_clearingNotified_fundTransfer() {
        List<SagaSettlementFailed> events = new ArrayList<>();
        monitor.addClearingHouseListener(events::add);
        monitor.trackSaga(SAGA_FT, DtcSagaPolicies.FUND_TRANSFER, ftCtx);

        monitor.onGlobalTimeout(SAGA_FT);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).sagaType()).isEqualTo(DtcSagaPolicies.FUND_TRANSFER);
    }

    // ── AC3: Dashboard snapshot ───────────────────────────────────────────────

    @Test
    @DisplayName("dtc_dashboard_activeSagas — Dashboard shows active sagas grouped by type")
    void dtc_dashboard_activeSagas() {
        monitor.trackSaga(SAGA_DVP1, DtcSagaPolicies.DVP_SETTLEMENT, dvpCtx);
        monitor.trackSaga(SAGA_DVP2, DtcSagaPolicies.DVP_SETTLEMENT,
            new DtcSettlementContext(TENANT, SETTLE_003, null, null, Map.of()));
        monitor.trackSaga(SAGA_FT, DtcSagaPolicies.FUND_TRANSFER, ftCtx);

        DashboardSnapshot snapshot = monitor.getDashboardSnapshot();

        assertThat(snapshot.totalActiveSagas()).isEqualTo(3);
        assertThat(snapshot.activeSagasByType())
            .containsEntry(DtcSagaPolicies.DVP_SETTLEMENT, 2L)
            .containsEntry(DtcSagaPolicies.FUND_TRANSFER, 1L);
    }

    @Test
    @DisplayName("dtc_dashboard_complianceQueue — Settlement failures add to compensation queue")
    void dtc_dashboard_complianceQueue() {
        monitor.addClearingHouseListener(e -> {}); // no-op listener
        monitor.trackSaga(SAGA_DVP1, DtcSagaPolicies.DVP_SETTLEMENT, dvpCtx);
        monitor.trackSaga(SAGA_FT, DtcSagaPolicies.FUND_TRANSFER, ftCtx);

        // Trigger global timeout on both sagas
        monitor.onGlobalTimeout(SAGA_DVP1);
        monitor.onGlobalTimeout(SAGA_FT);

        DashboardSnapshot snapshot = monitor.getDashboardSnapshot();
        assertThat(snapshot.compensationQueueDepth()).isEqualTo(2);
    }

    @Test
    @DisplayName("dtc_alerting_stuckSaga — Step breach tracked in dashboard SLA count")
    void dtc_alerting_stuckSaga() {
        monitor.addSrnAlertListener(e -> {}); // no-op
        monitor.trackSaga(SAGA_DVP1, DtcSagaPolicies.DVP_SETTLEMENT, dvpCtx);

        monitor.onStepTimeout(SAGA_DVP1, "lock-assets");
        monitor.onStepTimeout(SAGA_DVP1, "deliver-securities");

        DashboardSnapshot snapshot = monitor.getDashboardSnapshot();
        assertThat(snapshot.totalStepBreaches()).isEqualTo(2);
    }

    @Test
    @DisplayName("dtc_completedSaga_removedFromDashboard — Completed sagas excluded from counts")
    void dtc_completedSaga_removedFromDashboard() {
        monitor.trackSaga(SAGA_DVP1, DtcSagaPolicies.DVP_SETTLEMENT, dvpCtx);
        monitor.trackSaga(SAGA_FT, DtcSagaPolicies.FUND_TRANSFER, ftCtx);

        monitor.completeSaga(SAGA_DVP1);

        DashboardSnapshot snapshot = monitor.getDashboardSnapshot();
        assertThat(snapshot.totalActiveSagas()).isEqualTo(1);
        assertThat(snapshot.activeSagasByType())
            .doesNotContainKey(DtcSagaPolicies.DVP_SETTLEMENT);
    }

    @Test
    @DisplayName("dtc_fundTransfer_complianceNotification — FT step timeout raises compliance alert")
    void dtc_fundTransfer_complianceNotification() {
        List<DtcSagaStepTimedOut> complianceAlerts = new ArrayList<>();
        List<DtcSagaStepTimedOut> srnAlerts = new ArrayList<>();
        monitor.addComplianceListener(complianceAlerts::add);
        monitor.addSrnAlertListener(srnAlerts::add);
        monitor.trackSaga(SAGA_FT, DtcSagaPolicies.FUND_TRANSFER, ftCtx);

        monitor.onStepTimeout(SAGA_FT, "debit-source");

        // Fund-transfer timeout → compliance (not SRN)
        assertThat(complianceAlerts).hasSize(1);
        assertThat(srnAlerts).isEmpty();
        assertThat(complianceAlerts.get(0).stepName()).isEqualTo("debit-source");
    }
}
