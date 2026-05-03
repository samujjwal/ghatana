package com.ghatana.digitalmarketing.pack;

import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Startup bindings that register all DMOS plugin rule packs and hooks into
 * platform plugin infrastructure.
 *
 * <p>{@code DigitalMarketingPluginBindings} must be called during application startup
 * (in the kernel extension's {@code onInitialize} method or equivalent startup lifecycle
 * hook) before any DMOS compliance evaluations can be performed.</p>
 *
 * <p>Bindings are idempotent: calling {@link #registerAll()} multiple times with the
 * same rule content is safe and results in the same registered state.</p>
 *
 * <h3>Registered rule sets</h3>
 * <ol>
 *   <li>{@link DmComplianceRuleSetIds#DM_MARKETING_INTEGRITY}</li>
 *   <li>{@link DmComplianceRuleSetIds#DM_CONSENT_LIFECYCLE}</li>
 *   <li>{@link DmComplianceRuleSetIds#DM_AUDIT_TRACEABILITY}</li>
 *   <li>{@link DmComplianceRuleSetIds#DM_CAMPAIGN_PREFLIGHT}</li>
 *   <li>{@link DmComplianceRuleSetIds#DM_CLAIMS_DISCLOSURES}</li>
 *   <li>{@link DmComplianceRuleSetIds#DM_EMAIL_COMPLIANCE}</li>
 *   <li>{@link DmComplianceRuleSetIds#DM_CONNECTOR_EXECUTION_SAFETY}</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Startup bindings registering all DMOS compliance rule packs with the platform plugin
 * @doc.layer product
 * @doc.pattern Initializer
 */
public final class DigitalMarketingPluginBindings {

    private final CompliancePlugin compliancePlugin;

    /**
     * Creates a new bindings instance.
     *
     * @param compliancePlugin the platform compliance plugin; must not be {@code null}
     */
    public DigitalMarketingPluginBindings(CompliancePlugin compliancePlugin) {
        this.compliancePlugin = Objects.requireNonNull(compliancePlugin, "compliancePlugin must not be null");
    }

    /**
     * Registers all DMOS compliance rule packs with the platform compliance plugin.
     *
     * <p>All seven rule sets are registered in parallel. The returned promise completes
     * when all registrations have completed successfully. Any single failure causes the
     * combined promise to fail — the caller should abort startup if this promise fails.</p>
     *
     * <p>DMOS-P2-15: Startup validation is performed before registration to ensure
     * rule IDs are unique, properly prefixed, and non-empty.</p>
     *
     * @return promise completing when all rule packs are registered; never {@code null}
     */
    public Promise<Void> registerAll() {
        // DMOS-P2-15: Startup validation
        validateRulePacks();

        return Promises.all(
            compliancePlugin.registerRuleSet(
                DmComplianceRuleSetIds.DM_MARKETING_INTEGRITY,
                DigitalMarketingComplianceRulePack.marketingIntegrityRules()
            ),
            compliancePlugin.registerRuleSet(
                DmComplianceRuleSetIds.DM_CONSENT_LIFECYCLE,
                DigitalMarketingComplianceRulePack.consentLifecycleRules()
            ),
            compliancePlugin.registerRuleSet(
                DmComplianceRuleSetIds.DM_AUDIT_TRACEABILITY,
                DigitalMarketingComplianceRulePack.auditTraceabilityRules()
            ),
            compliancePlugin.registerRuleSet(
                DmComplianceRuleSetIds.DM_CAMPAIGN_PREFLIGHT,
                DigitalMarketingComplianceRulePack.campaignPreflightRules()
            ),
            compliancePlugin.registerRuleSet(
                DmComplianceRuleSetIds.DM_CLAIMS_DISCLOSURES,
                DigitalMarketingComplianceRulePack.claimsDisclosuresRules()
            ),
            compliancePlugin.registerRuleSet(
                DmComplianceRuleSetIds.DM_EMAIL_COMPLIANCE,
                DigitalMarketingComplianceRulePack.emailComplianceRules()
            ),
            compliancePlugin.registerRuleSet(
                DmComplianceRuleSetIds.DM_CONNECTOR_EXECUTION_SAFETY,
                DigitalMarketingComplianceRulePack.connectorExecutionSafetyRules()
            )
        );
    }

    /**
     * DMOS-P2-15: Validates all rule packs before registration.
     *
     * <p>Ensures:
     * <ul>
     *   <li>All rule IDs are unique</li>
     *   <li>All rule IDs start with DM- prefix</li>
     *   <li>No rule sets are empty</li>
     *   <li>All required rule sets are present</li>
     * </ul></p>
     *
     * @throws IllegalStateException if validation fails
     */
    private void validateRulePacks() {
        Set<String> allRuleIds = new HashSet<>();

        validateRuleSet(DmComplianceRuleSetIds.DM_MARKETING_INTEGRITY, DigitalMarketingComplianceRulePack.marketingIntegrityRules(), allRuleIds);
        validateRuleSet(DmComplianceRuleSetIds.DM_CONSENT_LIFECYCLE, DigitalMarketingComplianceRulePack.consentLifecycleRules(), allRuleIds);
        validateRuleSet(DmComplianceRuleSetIds.DM_AUDIT_TRACEABILITY, DigitalMarketingComplianceRulePack.auditTraceabilityRules(), allRuleIds);
        validateRuleSet(DmComplianceRuleSetIds.DM_CAMPAIGN_PREFLIGHT, DigitalMarketingComplianceRulePack.campaignPreflightRules(), allRuleIds);
        validateRuleSet(DmComplianceRuleSetIds.DM_CLAIMS_DISCLOSURES, DigitalMarketingComplianceRulePack.claimsDisclosuresRules(), allRuleIds);
        validateRuleSet(DmComplianceRuleSetIds.DM_EMAIL_COMPLIANCE, DigitalMarketingComplianceRulePack.emailComplianceRules(), allRuleIds);
        validateRuleSet(DmComplianceRuleSetIds.DM_CONNECTOR_EXECUTION_SAFETY, DigitalMarketingComplianceRulePack.connectorExecutionSafetyRules(), allRuleIds);
    }

    /**
     * Validates a single rule set.
     *
     * @param ruleSetId the rule set identifier
     * @param rules the compliance rules
     * @param allRuleIds set of all rule IDs seen so far (for duplicate detection)
     */
    private void validateRuleSet(String ruleSetId, 
                                   java.util.List<com.ghatana.plugin.compliance.CompliancePlugin.ComplianceRule> rules,
                                   Set<String> allRuleIds) {
        if (rules.isEmpty()) {
            throw new IllegalStateException("Rule set " + ruleSetId + " is empty");
        }

        for (var rule : rules) {
            String ruleId = rule.ruleId();
            if (ruleId == null || ruleId.isBlank()) {
                throw new IllegalStateException("Rule set " + ruleSetId + " contains rule with blank ID");
            }
            if (!ruleId.startsWith("DM-")) {
                throw new IllegalStateException("Rule set " + ruleSetId + " contains rule with invalid prefix: " + ruleId);
            }
            if (!allRuleIds.add(ruleId)) {
                throw new IllegalStateException("Duplicate rule ID found: " + ruleId + " in rule set " + ruleSetId);
            }
        }
    }
}
