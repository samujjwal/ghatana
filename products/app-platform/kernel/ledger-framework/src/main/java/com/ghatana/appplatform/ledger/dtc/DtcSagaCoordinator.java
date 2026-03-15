/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import com.ghatana.appplatform.eventstore.saga.SagaInstance;
import com.ghatana.appplatform.eventstore.saga.SagaOrchestrator;
import com.ghatana.appplatform.eventstore.saga.SagaStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DTC saga coordination layer built on top of the K-05 saga orchestration engine (STORY-K17-008).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Trigger DTC saga instances via the K-05 {@link SagaOrchestrator} using policy names
 *       defined in {@link DtcSagaPolicies}.</li>
 *   <li>Map K-05 lifecycle events (step completed, step failed) to DTC-level
 *       {@link DtcTransactionState} transitions.</li>
 *   <li>Enrich handles with DTC-specific metadata: regulatory flags, settlement batch
 *       linkage, and counterparty references.</li>
 *   <li>Reconcile local DTC transaction state after K-05 engine restart by re-subscribing
 *       to known in-progress sagas via {@link SagaStore}.</li>
 * </ul>
 *
 * <p>This coordinator does <em>not</em> re-implement event-sourced saga state — K-05 owns that.
 * DTC only maps K-05 state transitions to DTC domain semantics.
 *
 * @doc.type class
 * @doc.purpose DTC saga coordination layer integrating with K-05 saga engine (K17-008)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DtcSagaCoordinator {

    private final SagaOrchestrator orchestrator;
    private final SagaStore sagaStore;
    private final List<DtcSagaEventListener> listeners = new ArrayList<>();

    /**
     * @param orchestrator K-05 saga orchestration engine
     * @param sagaStore    K-05 saga persistence store (for reconciliation queries)
     */
    public DtcSagaCoordinator(SagaOrchestrator orchestrator, SagaStore sagaStore) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.sagaStore = Objects.requireNonNull(sagaStore, "sagaStore");
    }

    // ── Saga initiation ───────────────────────────────────────────────────────

    /**
     * Starts a DVP settlement saga via the K-05 engine.
     *
     * <p>The K-05 engine executes the steps defined by {@link DtcSagaPolicies#DVP_SETTLEMENT}
     * and manages state. DTC receives lifecycle events via callbacks.
     *
     * @param ctx DTC settlement context (batch, counterparty, regulatory flags)
     * @return handle representing the running saga in {@link DtcTransactionState#IN_PROGRESS}
     */
    public DtcSagaHandle startDvpSettlement(DtcSettlementContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        SagaInstance instance = orchestrator.startSaga(
                DtcSagaPolicies.DVP_SETTLEMENT, ctx.tenantId(), ctx.settlementId());
        DtcSagaHandle handle = new DtcSagaHandle(
                instance.sagaId(), DtcTransactionState.IN_PROGRESS, ctx);
        notifyListeners(handle);
        return handle;
    }

    /**
     * Starts a fund transfer saga via the K-05 engine.
     *
     * @param ctx DTC settlement context
     * @return handle in {@link DtcTransactionState#IN_PROGRESS}
     */
    public DtcSagaHandle startFundTransfer(DtcSettlementContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        SagaInstance instance = orchestrator.startSaga(
                DtcSagaPolicies.FUND_TRANSFER, ctx.tenantId(), ctx.settlementId());
        DtcSagaHandle handle = new DtcSagaHandle(
                instance.sagaId(), DtcTransactionState.IN_PROGRESS, ctx);
        notifyListeners(handle);
        return handle;
    }

    // ── K-05 lifecycle event handlers ─────────────────────────────────────────

    /**
     * Called when K-05 reports a saga step completed successfully.
     *
     * <p>Advances the K-05 internal state to the next step. DTC transaction remains
     * {@link DtcTransactionState#IN_PROGRESS} until the saga fully completes.
     *
     * @param sagaId K-05 saga identifier
     * @param ctx    DTC settlement context for enrichment
     * @return updated DTC handle in IN_PROGRESS state
     */
    public DtcSagaHandle onSagaStepCompleted(String sagaId, DtcSettlementContext ctx) {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(ctx, "ctx");
        orchestrator.onStepCompleted(sagaId);
        DtcSagaHandle handle = new DtcSagaHandle(sagaId, DtcTransactionState.IN_PROGRESS, ctx);
        notifyListeners(handle);
        return handle;
    }

    /**
     * Called when K-05 reports a step failure triggering compensation.
     *
     * <p>Delegates to the K-05 engine which handles retry logic and triggers compensation
     * when retries are exhausted. DTC maps this to {@link DtcTransactionState#COMPENSATING}
     * and publishes a DTC-level alert via registered listeners.
     *
     * @param sagaId      K-05 saga identifier
     * @param errorMessage failure reason for audit
     * @param ctx         DTC settlement context for enrichment
     * @return updated DTC handle in COMPENSATING state
     */
    public DtcSagaHandle onSagaCompensating(String sagaId, String errorMessage,
                                            DtcSettlementContext ctx) {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(ctx, "ctx");
        orchestrator.onStepFailed(sagaId, errorMessage);
        DtcSagaHandle handle = new DtcSagaHandle(sagaId, DtcTransactionState.COMPENSATING, ctx);
        notifyListeners(handle);
        return handle;
    }

    // ── Reconciliation ────────────────────────────────────────────────────────

    /**
     * Reconciles local DTC transaction state after a K-05 engine restart.
     *
     * <p>DTC provides its known set of in-progress saga IDs. The coordinator
     * re-fetches current state from the K-05 {@link SagaStore} (which rehydrates
     * from the event log) and returns handles for each saga that is still active.
     * Sagas not found in the store (e.g. already completed before restart) are silently skipped.
     *
     * @param sagaIds known DTC in-progress saga identifiers
     * @return list of reconciled handles in {@link DtcTransactionState#RECONCILED} state
     */
    public List<DtcSagaHandle> reconcileInProgressSagas(List<String> sagaIds) {
        Objects.requireNonNull(sagaIds, "sagaIds");
        List<DtcSagaHandle> handles = new ArrayList<>();
        for (String sagaId : sagaIds) {
            sagaStore.findInstance(sagaId).ifPresent(instance -> {
                DtcSettlementContext ctx = DtcSettlementContext.of(
                        instance.tenantId(), instance.correlationId());
                handles.add(new DtcSagaHandle(sagaId, DtcTransactionState.RECONCILED, ctx));
            });
        }
        return handles;
    }

    // ── Listener registration ─────────────────────────────────────────────────

    /**
     * Registers a listener that is notified on every DTC transaction state change.
     *
     * @param listener event listener; not null
     */
    public void addListener(DtcSagaEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /**
     * DTC-level saga transaction state (maps K-05 {@code SagaState} to DTC semantics).
     */
    public enum DtcTransactionState {
        /** Saga is running; steps being executed. */
        IN_PROGRESS,
        /** Step failed; K-05 is executing compensation. DTC publishes alert. */
        COMPENSATING,
        /** All steps or compensation completed. */
        COMPLETED,
        /** State reconciled after K-05 restart. */
        RECONCILED
    }

    /**
     * Runtime handle for a DTC saga instance, carrying K-05 identifier, DTC state, and context.
     *
     * @param sagaId           K-05 saga instance identifier
     * @param transactionState DTC-level transaction state
     * @param context          DTC settlement enrichment (batch, counterparty, regulatory flags)
     */
    public record DtcSagaHandle(
            String sagaId,
            DtcTransactionState transactionState,
            DtcSettlementContext context
    ) {
        public DtcSagaHandle {
            Objects.requireNonNull(sagaId, "sagaId");
            Objects.requireNonNull(transactionState, "transactionState");
            Objects.requireNonNull(context, "context");
        }
    }

    /**
     * DTC settlement enrichment context carrying domain-specific metadata that K-05 does not own.
     *
     * @param tenantId         tenant owning the settlement
     * @param settlementId     DTC settlement / correlation ID
     * @param batchId          settlement batch linkage identifier; null if not batched
     * @param counterpartyId   counterparty reference for the settlement; null if not applicable
     * @param regulatoryFlags  DTC-specific regulatory metadata (e.g. reporting obligations)
     */
    public record DtcSettlementContext(
            String tenantId,
            String settlementId,
            String batchId,
            String counterpartyId,
            Map<String, Object> regulatoryFlags
    ) {
        public DtcSettlementContext {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(settlementId, "settlementId");
            regulatoryFlags = regulatoryFlags != null
                    ? Map.copyOf(regulatoryFlags)
                    : Map.of();
        }

        /**
         * Creates a minimal context for reconciliation (batch/counterparty unknown after restart).
         *
         * @param tenantId     tenant identifier
         * @param settlementId correlation ID from K-05 instance
         * @return minimal context for rehydrated saga
         */
        public static DtcSettlementContext of(String tenantId, String settlementId) {
            return new DtcSettlementContext(tenantId, settlementId, null, null, Map.of());
        }
    }

    /**
     * Listener notified whenever a DTC saga state transition occurs.
     */
    @FunctionalInterface
    public interface DtcSagaEventListener {
        /**
         * Invoked synchronously on each state transition.
         *
         * @param handle the updated saga handle with new state and context
         */
        void onStateChange(DtcSagaHandle handle);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void notifyListeners(DtcSagaHandle handle) {
        for (DtcSagaEventListener listener : listeners) {
            listener.onStateChange(handle);
        }
    }
}
