package com.ghatana.digitalmarketing.pack;

import com.ghatana.plugin.compliance.CompliancePlugin.ComplianceRule;
import com.ghatana.plugin.compliance.CompliancePlugin.ComplianceRule.Severity;

import java.util.List;

import static com.ghatana.digitalmarketing.pack.DmComplianceRuleSetIds.*;

/**
 * Factory for DMOS compliance rule packs.
 *
 * <p>Each method in this class returns the canonical list of {@link ComplianceRule} instances
 * for one rule set ID. These lists are registered with the platform's
 * {@link com.ghatana.plugin.compliance.CompliancePlugin} at startup by
 * {@link DigitalMarketingPluginBindings#registerAll()}.</p>
 *
 * <p>Rules are expressed using a condition string that the compliance engine evaluates
 * against the {@code ComplianceContext.data()} map. The condition format is aligned with
 * the platform's built-in SpEL/Rego evaluation support as configured in the product's
 * compliance plugin implementation.</p>
 *
 * <h3>Rule sets provided</h3>
 * <ul>
 *   <li>{@link #marketingIntegrityRules()} — DM_MARKETING_INTEGRITY</li>
 *   <li>{@link #consentLifecycleRules()} — DM_CONSENT_LIFECYCLE</li>
 *   <li>{@link #auditTraceabilityRules()} — DM_AUDIT_TRACEABILITY</li>
 *   <li>{@link #campaignPreflightRules()} — DM_CAMPAIGN_PREFLIGHT</li>
 *   <li>{@link #claimsDisclosuresRules()} — DM_CLAIMS_DISCLOSURES</li>
 *   <li>{@link #emailComplianceRules()} — DM_EMAIL_COMPLIANCE</li>
 *   <li>{@link #connectorExecutionSafetyRules()} — DM_CONNECTOR_EXECUTION_SAFETY</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DMOS compliance rule pack factory; supplies canonical rule lists per rule set
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class DigitalMarketingComplianceRulePack {

    private DigitalMarketingComplianceRulePack() {
        // factory only
    }

    // -----------------------------------------------------------------------
    // DM_MARKETING_INTEGRITY
    // -----------------------------------------------------------------------

    /**
     * Returns rules for the {@link DmComplianceRuleSetIds#DM_MARKETING_INTEGRITY} rule set.
     *
     * <p>Covers truthfulness requirements, misleading-claim detection, and brand safety.</p>
     */
    public static List<ComplianceRule> marketingIntegrityRules() {
        return List.of(
            new ComplianceRule(
                "MI-001",
                "CONTENT_TRUTHFULNESS",
                "Marketing claims must not be demonstrably false or misleading",
                Severity.HIGH,
                "data['hasFalseClaimFlag'] == false"
            ),
            new ComplianceRule(
                "MI-002",
                "BRAND_SAFETY",
                "Content must not reference prohibited competitor brands without legal approval",
                Severity.MEDIUM,
                "data['brandSafetyScore'] >= 70"
            ),
            new ComplianceRule(
                "MI-003",
                "SUPERLATIVE_CLAIMS",
                "Superlative marketing claims (best, #1, etc.) must be substantiated with references",
                Severity.MEDIUM,
                "data['superlativeClaimsSubstantiated'] != false"
            )
        );
    }

    // -----------------------------------------------------------------------
    // DM_CONSENT_LIFECYCLE
    // -----------------------------------------------------------------------

    /**
     * Returns rules for the {@link DmComplianceRuleSetIds#DM_CONSENT_LIFECYCLE} rule set.
     *
     * <p>Covers GDPR/CCPA opt-in validation, opt-out handling, and double opt-in completeness.</p>
     */
    public static List<ComplianceRule> consentLifecycleRules() {
        return List.of(
            new ComplianceRule(
                "CL-001",
                "CONTACT_OPT_IN",
                "All contacts added to a marketing audience must have valid opt-in consent records",
                Severity.CRITICAL,
                "data['contactHasValidOptIn'] == true"
            ),
            new ComplianceRule(
                "CL-002",
                "OPT_OUT_HONOURED",
                "Contacts that have opted out must not receive any marketing communications",
                Severity.CRITICAL,
                "data['contactOptOutStatus'] != 'OPTED_OUT'"
            ),
            new ComplianceRule(
                "CL-003",
                "DOUBLE_OPT_IN_COMPLETE",
                "For EU contacts, double opt-in confirmation must be complete before adding to any list",
                Severity.HIGH,
                "data['region'] != 'EU' || data['doubleOptInComplete'] == true"
            ),
            new ComplianceRule(
                "CL-004",
                "CONSENT_EXPIRY",
                "Consent records older than the configured retention period must be renewed",
                Severity.HIGH,
                "data['consentAgeInDays'] <= data['maxConsentAgeInDays']"
            )
        );
    }

    // -----------------------------------------------------------------------
    // DM_AUDIT_TRACEABILITY
    // -----------------------------------------------------------------------

    /**
     * Returns rules for the {@link DmComplianceRuleSetIds#DM_AUDIT_TRACEABILITY} rule set.
     *
     * <p>Verifies that required audit entries exist for all high-risk operations.</p>
     */
    public static List<ComplianceRule> auditTraceabilityRules() {
        return List.of(
            new ComplianceRule(
                "AT-001",
                "CAMPAIGN_LAUNCH_AUDIT",
                "Campaign launch events must have a corresponding audit trail entry",
                Severity.HIGH,
                "data['campaignLaunchAuditPresent'] == true"
            ),
            new ComplianceRule(
                "AT-002",
                "AUDIENCE_EXPORT_AUDIT",
                "Audience export operations must have a corresponding audit trail entry",
                Severity.HIGH,
                "data['audienceExportAuditPresent'] == true"
            ),
            new ComplianceRule(
                "AT-003",
                "CONNECTOR_EXECUTION_AUDIT",
                "External connector executions must have a corresponding audit trail entry",
                Severity.HIGH,
                "data['connectorExecutionAuditPresent'] == true"
            )
        );
    }

    // -----------------------------------------------------------------------
    // DM_CAMPAIGN_PREFLIGHT
    // -----------------------------------------------------------------------

    /**
     * Returns rules for the {@link DmComplianceRuleSetIds#DM_CAMPAIGN_PREFLIGHT} rule set.
     *
     * <p>Validates campaign readiness before launch is permitted.</p>
     */
    public static List<ComplianceRule> campaignPreflightRules() {
        return List.of(
            new ComplianceRule(
                "CP-001",
                "CAMPAIGN_HAS_BUDGET",
                "Campaign must have an approved budget assignment before launch",
                Severity.HIGH,
                "data['budgetApproved'] == true"
            ),
            new ComplianceRule(
                "CP-002",
                "CAMPAIGN_HAS_AUDIENCE",
                "Campaign must have at least one non-empty target audience segment",
                Severity.HIGH,
                "data['targetAudienceCount'] > 0"
            ),
            new ComplianceRule(
                "CP-003",
                "CAMPAIGN_HAS_CONTENT",
                "Campaign must have at least one approved content piece",
                Severity.HIGH,
                "data['approvedContentCount'] > 0"
            ),
            new ComplianceRule(
                "CP-004",
                "CAMPAIGN_WITHIN_BUDGET_LIMIT",
                "Campaign total spend must not exceed the approved budget ceiling",
                Severity.CRITICAL,
                "data['totalSpend'] <= data['approvedBudget']"
            )
        );
    }

    // -----------------------------------------------------------------------
    // DM_CLAIMS_DISCLOSURES
    // -----------------------------------------------------------------------

    /**
     * Returns rules for the {@link DmComplianceRuleSetIds#DM_CLAIMS_DISCLOSURES} rule set.
     *
     * <p>Legal language, disclaimer presence, and prohibited claim checks.</p>
     */
    public static List<ComplianceRule> claimsDisclosuresRules() {
        return List.of(
            new ComplianceRule(
                "CD-001",
                "DISCLAIMER_PRESENT",
                "Content making financial, health, or safety claims must include required disclaimers",
                Severity.HIGH,
                "data['disclaimerPresent'] == true || data['requiresDisclaimer'] == false"
            ),
            new ComplianceRule(
                "CD-002",
                "PROHIBITED_CLAIMS",
                "Content must not contain prohibited claim keywords from the configured blocklist",
                Severity.CRITICAL,
                "data['prohibitedClaimCount'] == 0"
            )
        );
    }

    // -----------------------------------------------------------------------
    // DM_EMAIL_COMPLIANCE
    // -----------------------------------------------------------------------

    /**
     * Returns rules for the {@link DmComplianceRuleSetIds#DM_EMAIL_COMPLIANCE} rule set.
     *
     * <p>CAN-SPAM, GDPR, and basic deliverability compliance for email campaigns.</p>
     */
    public static List<ComplianceRule> emailComplianceRules() {
        return List.of(
            new ComplianceRule(
                "EC-001",
                "PHYSICAL_ADDRESS",
                "Email must include a valid physical mailing address (CAN-SPAM requirement)",
                Severity.CRITICAL,
                "data['physicalAddressPresent'] == true"
            ),
            new ComplianceRule(
                "EC-002",
                "UNSUBSCRIBE_LINK",
                "Email must contain a clearly visible unsubscribe mechanism",
                Severity.CRITICAL,
                "data['unsubscribeLinkPresent'] == true"
            ),
            new ComplianceRule(
                "EC-003",
                "SENDER_AUTHENTICATION",
                "Email sender domain must have valid SPF and DKIM records configured",
                Severity.HIGH,
                "data['spfValid'] == true && data['dkimValid'] == true"
            ),
            new ComplianceRule(
                "EC-004",
                "SUBJECT_LINE_NON_DECEPTIVE",
                "Email subject line must not be deceptive or misleading",
                Severity.HIGH,
                "data['subjectLineDeceptive'] != true"
            )
        );
    }

    // -----------------------------------------------------------------------
    // DM_CONNECTOR_EXECUTION_SAFETY
    // -----------------------------------------------------------------------

    /**
     * Returns rules for the {@link DmComplianceRuleSetIds#DM_CONNECTOR_EXECUTION_SAFETY} rule set.
     *
     * <p>Validates connector safety before external data transmission.</p>
     */
    public static List<ComplianceRule> connectorExecutionSafetyRules() {
        return List.of(
            new ComplianceRule(
                "CES-001",
                "CONNECTOR_CREDENTIALS_VALID",
                "Connector credentials must not be expired or revoked",
                Severity.CRITICAL,
                "data['credentialsExpired'] != true"
            ),
            new ComplianceRule(
                "CES-002",
                "PII_FIELDS_MASKED",
                "PII fields must be masked or pseudonymized before transmission to external connectors",
                Severity.CRITICAL,
                "data['piiFieldsMasked'] == true"
            ),
            new ComplianceRule(
                "CES-003",
                "RATE_LIMIT_RESPECTED",
                "Connector execution must not exceed the connector's configured rate limit",
                Severity.HIGH,
                "data['requestsInWindow'] <= data['rateLimit']"
            )
        );
    }
}
