/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * DTC saga execution monitor built on top of K-05-F05 lifecycle events (STORY-K17-010).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Receive per-step timeout events fired by the K-05 engine and apply DTC-level
 *       escalation rules (DVP step timeout → SRN alert; fund-transfer timeout →
 *       compliance notification).</li>
 *   <li>Receive global saga SLA timeout events and mark transactions as
 *       {@code SETTLEMENT_FAILED}, notifying the clearing house.</li>
 *   <li>Maintain an in-memory dashboard view: active sagas grouped by type, step SLA
 *       breaches, counterparty-level failure rates, and regulatory compensation queue
 *       depth.</li>
 * </ul>
 *
 * <h2>Integration with K-05 (K05-019)</h2>
 * <p>The K-05 engine calls {@link #onStepTimeout} and {@link #onGlobalTimeout} when
 * configured timeouts are exceeded. DTC does not reimplement timeout tracking; it only
 * reacts to K-05 events.
 *
 * <h2>Dashboard</h2>
 * <p>Call {@link #getDashboardSnapshot()} to obtain a point-in-time read of:
 * saga counts by type, SLA breaches, and compensation queue depth.
 *
 * @doc.type class
 * @doc.purpose DTC saga execution monitoring with timeout escalation and dashboard (K17-010)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DtcSagaMonitor {

    // ─── Active saga registry ─────────────────────────────────────────────────

    /** Active sagas: sagaId → entry. ConcurrentHashMap for thread-safe updates. */
    private final Map<String, ActiveSagaEntry> activeSagas = new ConcurrentHashMap<>();

    /** Listeners for SRN alerts (DVP step timeouts). */
    private final List<SrnAlertListener> srnAlertListeners = new CopyOnWriteArrayList<>();

    /** Listeners for compliance notifications (fund-transfer timeouts, failures). */
    private final List<ComplianceNotificationListener> complianceListeners =
            new CopyOnWriteArrayList<>();

    /** Listeners for clearing-house notifications (settlement failures). */
    private final List<ClearingHouseListener> clearingHouseListeners =
            new CopyOnWriteArrayList<>();

    /** Regulatory compensation queue: sagaId of sagas waiting for escalated compensation. */
    private final List<String> compensationQueue = new CopyOnWriteArrayList<>();

    // ─── Saga lifecycle tracking ──────────────────────────────────────────────

    /**
     * Registers a new active DTC saga in the monitor.
     *
     * <p>Called at saga start so the dashboard can track active counts.
     *
     * @param sagaId   K-05 saga identifier; not null
     * @param sagaType DTC saga type (e.g. {@link DtcSagaPolicies#DVP_SETTLEMENT}); not null
     * @param ctx      DTC settlement context; not null
     */
    public void trackSaga(String sagaId, String sagaType,
                          DtcSagaCoordinator.DtcSettlementContext ctx) {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(sagaType, "sagaType");
        Objects.requireNonNull(ctx, "ctx");
        activeSagas.put(sagaId, new ActiveSagaEntry(sagaId, sagaType, ctx, Instant.now(),
                new ArrayList<>(), false));
    }

    /**
     * Marks a saga as completed and removes it from the active saga registry.
     *
     * @param sagaId K-05 saga identifier
     */
    public void completeSaga(String sagaId) {
        activeSagas.remove(sagaId);
        compensationQueue.remove(sagaId);
    }

    // ─── K-05 timeout event handlers (K05-019) ────────────────────────────────

    /**
     * Called by K-05 when a per-step timeout is exceeded.
     *
     * <p>DVP step timeouts raise an SRN alert. Fund-transfer step timeouts trigger
     * a compliance notification. Both are applied regardless of step position.
     *
     * @param sagaId   K-05 saga identifier; not null
     * @param stepName name of the step that timed out; not null
     */
    public void onStepTimeout(String sagaId, String stepName) {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(stepName, "stepName");

        ActiveSagaEntry entry = activeSagas.get(sagaId);
        if (entry != null) {
            entry.stepBreaches().add(new StepBreach(stepName, Instant.now()));
        }

        // DVP settlement timeout → SRN alert (AC1)
        if (entry != null && DtcSagaPolicies.DVP_SETTLEMENT.equals(entry.sagaType())) {
            DtcSagaStepTimedOut event = new DtcSagaStepTimedOut(sagaId, stepName, entry.ctx());
            srnAlertListeners.forEach(l -> l.onSrnAlert(event));
        }

        // Fund transfer timeout → compliance notification
        if (entry != null && DtcSagaPolicies.FUND_TRANSFER.equals(entry.sagaType())) {
            DtcSagaStepTimedOut event = new DtcSagaStepTimedOut(sagaId, stepName, entry.ctx());
            complianceListeners.forEach(l -> l.onComplianceNotification(event));
        }
    }

    /**
     * Called by K-05 when the global saga SLA is exceeded (AC2).
     *
     * <p>This marks the DTC transaction as {@code SETTLEMENT_FAILED} and notifies
     * the clearing house for intervention.
     *
     * @param sagaId K-05 saga identifier; not null
     */
    public void onGlobalTimeout(String sagaId) {
        Objects.requireNonNull(sagaId, "sagaId");

        ActiveSagaEntry entry = activeSagas.get(sagaId);
        if (entry != null) {
            // Replace with a settlement-failed entry
            activeSagas.put(sagaId, new ActiveSagaEntry(
                sagaId, entry.sagaType(), entry.ctx(), entry.startedAt(),
                entry.stepBreaches(), true));
            compensationQueue.add(sagaId);

            SagaSettlementFailed failedEvent = new SagaSettlementFailed(
                sagaId, entry.sagaType(), entry.ctx(),
                "Global SLA exceeded by K-05 engine");
            clearingHouseListeners.forEach(l -> l.onSettlementFailed(failedEvent));
        }
    }

    // ─── Listener registration ────────────────────────────────────────────────

    /** Registers an SRN alert listener (DVP step timeout escalation). */
    public void addSrnAlertListener(SrnAlertListener listener) {
        srnAlertListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Registers a compliance notification listener (fund-transfer timeout). */
    public void addComplianceListener(ComplianceNotificationListener listener) {
        complianceListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Registers a clearing-house listener (global settlement failure). */
    public void addClearingHouseListener(ClearingHouseListener listener) {
        clearingHouseListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // ─── Dashboard (AC3) ──────────────────────────────────────────────────────

    /**
     * Returns a point-in-time snapshot of the DTC saga monitoring dashboard (AC3).
     *
     * <p>Includes:
     * <ul>
     *   <li>Active saga count grouped by saga type.</li>
     *   <li>Total SLA breach count across all active sagas.</li>
     *   <li>Regulatory compensation queue depth (sagas in SETTLEMENT_FAILED awaiting
     *       clearing-house resolution).</li>
     * </ul>
     *
     * @return immutable dashboard snapshot
     */
    public DashboardSnapshot getDashboardSnapshot() {
        // Group active (non-failed) sagas by type
        Map<String, Long> byType = activeSagas.values().stream()
                .filter(e -> !e.settlementFailed())
                .collect(Collectors.groupingBy(ActiveSagaEntry::sagaType,
                        Collectors.counting()));

        long totalBreaches = activeSagas.values().stream()
                .mapToLong(e -> e.stepBreaches().size())
                .sum();

        int compQueueDepth = compensationQueue.size();

        return new DashboardSnapshot(
                Map.copyOf(byType),
                activeSagas.size(),
                totalBreaches,
                compQueueDepth,
                Instant.now());
    }

    // ─── Inner types ──────────────────────────────────────────────────────────

    /** Internal mutable entry for a tracked DTC saga. */
    private record ActiveSagaEntry(
            String sagaId,
            String sagaType,
            DtcSagaCoordinator.DtcSettlementContext ctx,
            Instant startedAt,
            List<StepBreach> stepBreaches,
            boolean settlementFailed
    ) {}

    /** Records a single step SLA breach. */
    public record StepBreach(String stepName, Instant detectedAt) {}

    /**
     * Event published when a DTC saga step exceeds its configured K-05 timeout.
     *
     * @param sagaId   K-05 saga identifier
     * @param stepName name of the timed-out step
     * @param context  DTC settlement enrichment context
     */
    public record DtcSagaStepTimedOut(
            String sagaId,
            String stepName,
            DtcSagaCoordinator.DtcSettlementContext context
    ) {}

    /**
     * Event published when the global DTC saga SLA is exceeded.
     *
     * @param sagaId   K-05 saga identifier
     * @param sagaType DTC saga type
     * @param context  DTC settlement enrichment context
     * @param reason   human-readable explanation
     */
    public record SagaSettlementFailed(
            String sagaId,
            String sagaType,
            DtcSagaCoordinator.DtcSettlementContext context,
            String reason
    ) {}

    /**
     * Immutable point-in-time dashboard snapshot (AC3).
     *
     * @param activeSagasByType    count of non-failed active sagas grouped by type
     * @param totalActiveSagas     total tracked sagas (including settlement-failed)
     * @param totalStepBreaches    total step SLA breach count across all sagas
     * @param compensationQueueDepth number of sagas in regulatory compensation queue
     * @param snapshotAt           timestamp of the snapshot
     */
    public record DashboardSnapshot(
            Map<String, Long> activeSagasByType,
            long totalActiveSagas,
            long totalStepBreaches,
            int compensationQueueDepth,
            Instant snapshotAt
    ) {}

    /** Listener for SRN (Securities Regulatory Notice) alerts on DVP step timeouts. */
    @FunctionalInterface
    public interface SrnAlertListener {
        void onSrnAlert(DtcSagaStepTimedOut event);
    }

    /** Listener for compliance notifications on fund-transfer step timeouts. */
    @FunctionalInterface
    public interface ComplianceNotificationListener {
        void onComplianceNotification(DtcSagaStepTimedOut event);
    }

    /** Listener for clearing-house failure notifications on global saga SLA breach. */
    @FunctionalInterface
    public interface ClearingHouseListener {
        void onSettlementFailed(SagaSettlementFailed event);
    }
}
