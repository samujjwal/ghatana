/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.kernel.policy;

import com.ghatana.plugin.compliance.CompliancePlugin;

import java.util.List;

/**
 * PHR compliance rule pack.
 *
 * <p>Declares the compliance rules applicable to PHR subject records, consent
 * management, and clinical documentation workflows. These rules are registered
 * with the platform {@link CompliancePlugin} via {@link #getRuleSetId()} and
 * {@link #getRules()} at product startup.</p>
 *
 * <p>Rule set identifiers follow the convention {@code PHR_<DOMAIN>_CONTROL}
 * to avoid collisions with rules from other products.</p>
 *
 * @doc.type class
 * @doc.purpose PHR product compliance rule definitions for subject records and consent management
 * @doc.layer product
 * @doc.pattern ValueObject
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public final class PhrComplianceRulePack {

    /**
     * Rule set identifier for PHR subject-record access control.
     * Register this with {@code compliancePlugin.registerRuleSet(SUBJECT_RECORD_ACCESS, rules)}.
     */
    public static final String SUBJECT_RECORD_ACCESS = "PHR_SUBJECT_RECORD_ACCESS";

    /**
     * Rule set identifier for PHR consent lifecycle control.
     */
    public static final String CONSENT_LIFECYCLE = "PHR_CONSENT_LIFECYCLE";

    /**
     * Rule set identifier for PHR audit and traceability requirements.
     */
    public static final String AUDIT_TRACEABILITY = "PHR_AUDIT_TRACEABILITY";

    private PhrComplianceRulePack() {
        // Non-instantiable — use static factory methods.
    }

    /**
     * Returns the subject-record access control compliance rules.
     *
     * <p>Rules enforce that:
     * <ul>
     *   <li>Subject record access requires a valid consent grant.</li>
     *   <li>Authentication must be verified before any record read.</li>
     *   <li>Bulk export of subject records is explicitly prohibited.</li>
     * </ul>
     * </p>
     *
     * @return immutable list of subject-record access control rules
     */
    public static List<CompliancePlugin.ComplianceRule> subjectRecordAccessRules() {
        return List.of(
                new CompliancePlugin.ComplianceRule(
                        "PHR-AC-001",
                        "ACCESS_CONTROL",
                        "Subject record access requires verified consent",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "consent_verified == true"
                ),
                new CompliancePlugin.ComplianceRule(
                        "PHR-AC-002",
                        "AUTHENTICATION",
                        "Authentication must be verified before record access",
                        CompliancePlugin.ComplianceRule.Severity.CRITICAL,
                        "authentication_verified == true"
                ),
                new CompliancePlugin.ComplianceRule(
                        "PHR-AC-003",
                        "DATA_PROTECTION",
                        "Bulk export of subject records is not permitted",
                        CompliancePlugin.ComplianceRule.Severity.CRITICAL,
                        "bulk_export_requested == false"
                )
        );
    }

    /**
     * Returns the consent lifecycle compliance rules.
     *
     * <p>Rules enforce that:
     * <ul>
     *   <li>Consent records must be immutable once created.</li>
     *   <li>Consent withdrawal must be honored immediately.</li>
     *   <li>Consent must be renewed when the purpose changes.</li>
     * </ul>
     * </p>
     *
     * @return immutable list of consent lifecycle rules
     */
    public static List<CompliancePlugin.ComplianceRule> consentLifecycleRules() {
        return List.of(
                new CompliancePlugin.ComplianceRule(
                        "PHR-CL-001",
                        "CONSENT_CONTROL",
                        "Consent records must be immutable after creation",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "consent_record_immutable == true"
                ),
                new CompliancePlugin.ComplianceRule(
                        "PHR-CL-002",
                        "CONSENT_CONTROL",
                        "Consent withdrawal must be honored immediately",
                        CompliancePlugin.ComplianceRule.Severity.CRITICAL,
                        "consent_withdrawal_honored == true"
                ),
                new CompliancePlugin.ComplianceRule(
                        "PHR-CL-003",
                        "CONSENT_CONTROL",
                        "Consent must be renewed when processing purpose changes",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "consent_purpose_unchanged == true OR consent_renewed == true"
                )
        );
    }

    /**
     * Returns the audit and traceability compliance rules.
     *
     * <p>Rules enforce that:
     * <ul>
     *   <li>All subject record access events must be audited.</li>
     *   <li>Audit records must be retained for the required period.</li>
     *   <li>Audit records must be tamper-evident.</li>
     * </ul>
     * </p>
     *
     * @return immutable list of audit traceability rules
     */
    public static List<CompliancePlugin.ComplianceRule> auditTraceabilityRules() {
        return List.of(
                new CompliancePlugin.ComplianceRule(
                        "PHR-AT-001",
                        "AUDIT_CONTROL",
                        "All subject record access events must be audited",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "access_event_audited == true"
                ),
                new CompliancePlugin.ComplianceRule(
                        "PHR-AT-002",
                        "AUDIT_CONTROL",
                        "Audit records must be retained for the minimum required period",
                        CompliancePlugin.ComplianceRule.Severity.MEDIUM,
                        "audit_retention_years >= 7"
                ),
                new CompliancePlugin.ComplianceRule(
                        "PHR-AT-003",
                        "AUDIT_CONTROL",
                        "Audit records must be tamper-evident (hash-chained)",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "audit_tamper_evident == true"
                )
        );
    }
}
