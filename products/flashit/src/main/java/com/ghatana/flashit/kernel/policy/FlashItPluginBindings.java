package com.ghatana.flashit.kernel.policy;

import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;

/**
 * @doc.type class
 * @doc.purpose Registers FlashIt-owned rule packs with kernel plugins at product startup
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class FlashItPluginBindings {

    private final CompliancePlugin compliancePlugin;

    public FlashItPluginBindings(CompliancePlugin compliancePlugin) {
        this.compliancePlugin = compliancePlugin;
    }

    public Promise<Void> registerAll() {
        return compliancePlugin.registerRuleSet(
                FlashItComplianceRulePack.MOMENT_PRIVACY,
                FlashItComplianceRulePack.momentPrivacyRules());
    }
}
