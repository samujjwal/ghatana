/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import com.ghatana.appplatform.eventstore.saga.SagaDefinition;
import com.ghatana.appplatform.eventstore.saga.SagaStep;
import com.ghatana.appplatform.eventstore.saga.SagaStore;

import java.util.List;
import java.util.Objects;

/**
 * DTC saga policy definitions registered into the K-05 saga registry (K17-007).
 *
 * <p>This class is the single source of truth for all DTC saga blueprints.
 * It does NOT build a new saga registry — it populates the shared K-05
 * {@link SagaStore} with DTC-specific saga definitions.
 *
 * <p>Three saga types are defined:
 * <ul>
 *   <li><b>DVP settlement</b> ({@value #DVP_SETTLEMENT}) — Lock → Deliver → Pay → Confirm.
 *       Covers delivery-versus-payment settlement with full compensation chain.</li>
 *   <li><b>Fund transfer</b> ({@value #FUND_TRANSFER}) — Debit → Credit → Notify.
 *       Atomic fund movement between accounts; notification is best-effort (no compensation).</li>
 *   <li><b>Corporate action</b> ({@value #CORPORATE_ACTION}) — Announce → Record → Distribute.
 *       Corporate event processing (dividends, splits, rights issues).</li>
 * </ul>
 *
 * <p>Registration:
 * <pre>{@code
 * DtcSagaPolicies.registerAll(sagaStore);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose DTC-specific saga policy definitions registered into K-05 saga registry (K17-007)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class DtcSagaPolicies {

    // ─── Saga type identifiers (stable, used as registry keys) ───────────────

    /** Delivery-versus-payment settlement saga: Lock → Deliver → Pay → Confirm. */
    public static final String DVP_SETTLEMENT   = "dtc.dvp-settlement";

    /** Fund transfer saga: Debit → Credit → Notify. */
    public static final String FUND_TRANSFER    = "dtc.fund-transfer";

    /** Corporate action saga: Announce → Record → Distribute. */
    public static final String CORPORATE_ACTION = "dtc.corporate-action";

    // ─── Compensation topic constants (K-05 compensation event types) ────────

    /** DVP lock-assets compensation event: unlock securities. */
    public static final String DVP_LOCK_REVERSED     = "dtc.dvp.lock.reversed";
    /** DVP deliver-securities compensation event: reverse asset transfer. */
    public static final String DVP_DELIVER_REVERSED  = "dtc.dvp.deliver.reversed";
    /** DVP pay-cash compensation event: reverse cash payment. */
    public static final String DVP_PAY_REVERSED      = "dtc.dvp.pay.reversed";
    /** Fund transfer debit-source compensation event: credit-back. */
    public static final String FUND_DEBIT_REVERSED   = "dtc.transfer.debit.reversed";
    /** Fund transfer credit-destination compensation event: debit-back. */
    public static final String FUND_CREDIT_REVERSED  = "dtc.transfer.credit.reversed";

    // ─── Policy version ───────────────────────────────────────────────────────

    /** Current version for all DTC saga policies. */
    public static final int POLICY_VERSION = 1;

    private DtcSagaPolicies() {}

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Registers all DTC saga policy definitions into the supplied K-05 saga store.
     *
     * <p>Safe to call at service startup (idempotent if the store uses upsert semantics).
     *
     * @param store K-05 saga definition registry; not null
     */
    public static void registerAll(SagaStore store) {
        Objects.requireNonNull(store, "store");
        store.saveDefinition(dvpSettlement());
        store.saveDefinition(fundTransfer());
        store.saveDefinition(corporateAction());
    }

    /**
     * Returns all DTC saga definitions (in registration order).
     *
     * @return immutable list of all three DTC saga blueprints
     */
    public static List<SagaDefinition> allDefinitions() {
        return List.of(dvpSettlement(), fundTransfer(), corporateAction());
    }

    // ─── Saga definition builders ─────────────────────────────────────────────

    /**
     * DVP settlement saga: Lock → Deliver → Pay → Confirm.
     *
     * <p>All steps except Confirm have compensation actions to support full rollback.
     * Confirm is idempotent and does not require compensation once acknowledged.
     * Max retries per step: 3 (inherited from {@link SagaStep#withCompensation}).
     *
     * @return DVP settlement saga definition at version {@value #POLICY_VERSION}
     */
    public static SagaDefinition dvpSettlement() {
        return new SagaDefinition(
            DVP_SETTLEMENT,
            POLICY_VERSION,
            "DVP settlement saga: Lock assets, Deliver securities, Pay cash, Confirm settlement",
            List.of(
                SagaStep.withCompensation(
                    "lock-assets",          0,
                    "dtc.dvp.lock.requested",
                    "dtc.dvp.lock.completed",
                    DVP_LOCK_REVERSED),

                SagaStep.withCompensation(
                    "deliver-securities",   1,
                    "dtc.dvp.deliver.requested",
                    "dtc.dvp.deliver.completed",
                    DVP_DELIVER_REVERSED),

                SagaStep.withCompensation(
                    "pay-cash",             2,
                    "dtc.dvp.pay.requested",
                    "dtc.dvp.pay.completed",
                    DVP_PAY_REVERSED),

                // Confirm: idempotent — no compensation needed once acknowledged
                SagaStep.of(
                    "confirm-settlement",   3,
                    "dtc.dvp.confirm.requested",
                    "dtc.dvp.confirm.completed")
            )
        );
    }

    /**
     * Fund transfer saga: Debit → Credit → Notify.
     *
     * <p>Debit and Credit steps have compensations. Notify is best-effort (mandatory=false
     * means failure does not trigger rollback of the settled funds).
     *
     * @return fund transfer saga definition at version {@value #POLICY_VERSION}
     */
    public static SagaDefinition fundTransfer() {
        return new SagaDefinition(
            FUND_TRANSFER,
            POLICY_VERSION,
            "Fund transfer saga: Debit source account, Credit destination, Notify parties",
            List.of(
                SagaStep.withCompensation(
                    "debit-source",         0,
                    "dtc.transfer.debit.requested",
                    "dtc.transfer.debit.completed",
                    FUND_DEBIT_REVERSED),

                SagaStep.withCompensation(
                    "credit-destination",   1,
                    "dtc.transfer.credit.requested",
                    "dtc.transfer.credit.completed",
                    FUND_CREDIT_REVERSED),

                // Notify: best-effort — failure does not trigger compensation
                new SagaStep(
                    "notify-parties",       2,
                    "dtc.transfer.notify.requested",
                    "dtc.transfer.notify.completed",
                    null,   // no compensation
                    3,
                    false)  // not mandatory — skip on failure
            )
        );
    }

    /**
     * Corporate action saga: Announce → Record → Distribute.
     *
     * <p>Record and Distribute carry compensation actions. Announce is an informational
     * broadcast and does not require rollback (mandatory=false).
     *
     * @return corporate action saga definition at version {@value #POLICY_VERSION}
     */
    public static SagaDefinition corporateAction() {
        return new SagaDefinition(
            CORPORATE_ACTION,
            POLICY_VERSION,
            "Corporate action saga: Announce event, Record entitlements, Distribute proceeds",
            List.of(
                // Announce: informational broadcast, no compensation needed
                new SagaStep(
                    "announce-event",       0,
                    "dtc.ca.announce.requested",
                    "dtc.ca.announce.completed",
                    null,   // no compensation
                    3,
                    false),  // not mandatory

                SagaStep.withCompensation(
                    "record-entitlements",  1,
                    "dtc.ca.record.requested",
                    "dtc.ca.record.completed",
                    "dtc.ca.record.reversed"),

                SagaStep.withCompensation(
                    "distribute-proceeds",  2,
                    "dtc.ca.distribute.requested",
                    "dtc.ca.distribute.completed",
                    "dtc.ca.distribute.reversed")
            )
        );
    }
}
