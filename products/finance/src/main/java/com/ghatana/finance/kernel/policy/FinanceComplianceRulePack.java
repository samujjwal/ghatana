/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.kernel.policy;

import com.ghatana.plugin.compliance.CompliancePlugin;

import java.util.List;

/**
 * Finance compliance rule pack.
 *
 * <p>Declares the compliance rules applicable to Finance transaction processing,
 * audit controls, and trade surveillance workflows. These rules are registered
 * with the platform {@link CompliancePlugin} via {@link #getRuleSetId()} and
 * the corresponding rule factory methods at product startup.</p>
 *
 * <p>Rule set identifiers follow the convention {@code FIN_<DOMAIN>_CONTROL}
 * to avoid collisions with rules from other products.</p>
 *
 * @doc.type class
 * @doc.purpose Finance product compliance rule definitions for transaction and audit controls
 * @doc.layer product
 * @doc.pattern ValueObject
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceComplianceRulePack {

    /**
     * Rule set identifier for Finance transaction integrity controls.
     * Register with {@code compliancePlugin.registerRuleSet(TRANSACTION_INTEGRITY, rules)}.
     */
    public static final String TRANSACTION_INTEGRITY = "FIN_TRANSACTION_INTEGRITY";

    /**
     * Rule set identifier for Finance audit and record-keeping controls.
     */
    public static final String AUDIT_RECORD_KEEPING = "FIN_AUDIT_RECORD_KEEPING";

    /**
     * Rule set identifier for Finance trade surveillance controls.
     */
    public static final String TRADE_SURVEILLANCE = "FIN_TRADE_SURVEILLANCE";

    private FinanceComplianceRulePack() {
        // Non-instantiable — use static factory methods.
    }

    /**
     * Returns the transaction integrity compliance rules.
     *
     * <p>Rules enforce that:
     * <ul>
     *   <li>Transactions must be authorized before settlement.</li>
     *   <li>Dual authorization (four-eyes) is required for high-value transactions.</li>
     *   <li>Transaction reversals must carry an approved justification.</li>
     * </ul>
     * </p>
     *
     * @return immutable list of transaction integrity rules
     */
    public static List<CompliancePlugin.ComplianceRule> transactionIntegrityRules() {
        return List.of(
                new CompliancePlugin.ComplianceRule(
                        "FIN-TI-001",
                        "AUTHORIZATION_CONTROL",
                        "Transactions must be authorized before settlement",
                        CompliancePlugin.ComplianceRule.Severity.CRITICAL,
                        "authorization_granted == true"
                ),
                new CompliancePlugin.ComplianceRule(
                        "FIN-TI-002",
                        "AUTHORIZATION_CONTROL",
                        "High-value transactions require dual authorization",
                        CompliancePlugin.ComplianceRule.Severity.CRITICAL,
                        "transaction_value < high_value_threshold OR dual_authorization == true"
                ),
                new CompliancePlugin.ComplianceRule(
                        "FIN-TI-003",
                        "DATA_INTEGRITY",
                        "Transaction reversals must carry an approved justification",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "reversal_justification_approved == true"
                )
        );
    }

    /**
     * Returns the audit and record-keeping compliance rules.
     *
     * <p>Rules enforce that:
     * <ul>
     *   <li>All transaction events must be audited.</li>
     *   <li>Audit records must be retained for the minimum required period.</li>
     *   <li>Audit records must be tamper-evident.</li>
     * </ul>
     * </p>
     *
     * @return immutable list of audit record-keeping rules
     */
    public static List<CompliancePlugin.ComplianceRule> auditRecordKeepingRules() {
        return List.of(
                new CompliancePlugin.ComplianceRule(
                        "FIN-AR-001",
                        "AUDIT_CONTROL",
                        "All transaction events must be audited",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "transaction_event_audited == true"
                ),
                new CompliancePlugin.ComplianceRule(
                        "FIN-AR-002",
                        "AUDIT_CONTROL",
                        "Audit records must be retained for the minimum required period",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "audit_retention_years >= 7"
                ),
                new CompliancePlugin.ComplianceRule(
                        "FIN-AR-003",
                        "AUDIT_CONTROL",
                        "Audit records must be tamper-evident (hash-chained)",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "audit_tamper_evident == true"
                )
        );
    }

    /**
     * Returns the trade surveillance compliance rules.
     *
     * <p>Rules enforce that:
     * <ul>
     *   <li>Trades are screened for market-manipulation patterns.</li>
     *   <li>Position limits are checked before order execution.</li>
     *   <li>Wash-trade detection is active for all accounts.</li>
     * </ul>
     * </p>
     *
     * @return immutable list of trade surveillance rules
     */
    public static List<CompliancePlugin.ComplianceRule> tradeSurveillanceRules() {
        return List.of(
                new CompliancePlugin.ComplianceRule(
                        "FIN-TS-001",
                        "TRADE_SURVEILLANCE",
                        "Trades must be screened for market-manipulation patterns",
                        CompliancePlugin.ComplianceRule.Severity.CRITICAL,
                        "manipulation_screening_passed == true"
                ),
                new CompliancePlugin.ComplianceRule(
                        "FIN-TS-002",
                        "POSITION_CONTROL",
                        "Position limits must be checked before order execution",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "position_within_limit == true"
                ),
                new CompliancePlugin.ComplianceRule(
                        "FIN-TS-003",
                        "TRADE_SURVEILLANCE",
                        "Wash-trade detection must be active for all accounts",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "wash_trade_detection_active == true"
                )
        );
    }
}
