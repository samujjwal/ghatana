package com.ghatana.digitalmarketing.pack;

import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.Objects;

import static com.ghatana.digitalmarketing.pack.DmComplianceRuleSetIds.*;

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
     * @return promise completing when all rule packs are registered; never {@code null}
     */
    public Promise<Void> registerAll() {
        return Promises.all(
            compliancePlugin.registerRuleSet(
                DM_MARKETING_INTEGRITY,
                DigitalMarketingComplianceRulePack.marketingIntegrityRules()
            ),
            compliancePlugin.registerRuleSet(
                DM_CONSENT_LIFECYCLE,
                DigitalMarketingComplianceRulePack.consentLifecycleRules()
            ),
            compliancePlugin.registerRuleSet(
                DM_AUDIT_TRACEABILITY,
                DigitalMarketingComplianceRulePack.auditTraceabilityRules()
            ),
            compliancePlugin.registerRuleSet(
                DM_CAMPAIGN_PREFLIGHT,
                DigitalMarketingComplianceRulePack.campaignPreflightRules()
            ),
            compliancePlugin.registerRuleSet(
                DM_CLAIMS_DISCLOSURES,
                DigitalMarketingComplianceRulePack.claimsDisclosuresRules()
            ),
            compliancePlugin.registerRuleSet(
                DM_EMAIL_COMPLIANCE,
                DigitalMarketingComplianceRulePack.emailComplianceRules()
            ),
            compliancePlugin.registerRuleSet(
                DM_CONNECTOR_EXECUTION_SAFETY,
                DigitalMarketingComplianceRulePack.connectorExecutionSafetyRules()
            )
        );
    }
}
