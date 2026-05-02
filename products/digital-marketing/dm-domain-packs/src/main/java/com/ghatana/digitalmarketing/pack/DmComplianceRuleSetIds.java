package com.ghatana.digitalmarketing.pack;

/**
 * Constants for DMOS compliance rule set identifiers.
 *
 * <p>These constants are the canonical rule set IDs used when registering rule packs with
 * the {@link com.ghatana.plugin.compliance.CompliancePlugin} and when evaluating entities
 * against compliance checks in application services.</p>
 *
 * <p>All rule sets must be registered at startup via
 * {@link DigitalMarketingPluginBindings#registerAll()} before evaluation calls are made.</p>
 *
 * @doc.type class
 * @doc.purpose Canonical constants for DMOS compliance rule set identifiers
 * @doc.layer product
 * @doc.pattern Constants
 */
public final class DmComplianceRuleSetIds {

    /** Marketing message integrity: truthfulness, misleading-claim detection, brand safety. */
    public static final String DM_MARKETING_INTEGRITY = "DM_MARKETING_INTEGRITY";

    /**
     * Consent lifecycle: contact opt-in/opt-out state management, GDPR/CCPA consent record
     * validation, double opt-in completeness.
     */
    public static final String DM_CONSENT_LIFECYCLE = "DM_CONSENT_LIFECYCLE";

    /**
     * Audit traceability: verifies that all required audit-trail entries exist for
     * high-risk operations such as campaign launch, audience export, and connector execution.
     */
    public static final String DM_AUDIT_TRACEABILITY = "DM_AUDIT_TRACEABILITY";

    /**
     * Campaign pre-flight: validates that a campaign satisfies all required fields,
     * budget assignments, target audience configuration, and content approvals before launch.
     */
    public static final String DM_CAMPAIGN_PREFLIGHT = "DM_CAMPAIGN_PREFLIGHT";

    /**
     * Claims and disclosures: legal-language presence checks, disclaimer completeness,
     * prohibited claim scanning.
     */
    public static final String DM_CLAIMS_DISCLOSURES = "DM_CLAIMS_DISCLOSURES";

    /**
     * Email compliance: CAN-SPAM/GDPR physical address, unsubscribe mechanism,
     * sender authentication, and content policy checks.
     */
    public static final String DM_EMAIL_COMPLIANCE = "DM_EMAIL_COMPLIANCE";

    /**
     * Connector execution safety: validates connector credentials are non-expired, rate
     * limits are respected, and PII fields are masked before transmission.
     */
    public static final String DM_CONNECTOR_EXECUTION_SAFETY = "DM_CONNECTOR_EXECUTION_SAFETY";

    private DmComplianceRuleSetIds() {
        // constants only
    }
}
