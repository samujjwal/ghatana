package com.ghatana.flashit.kernel.policy;

import com.ghatana.plugin.compliance.CompliancePlugin;

import java.util.List;

/**
 * @doc.type class
 * @doc.purpose Supplies FlashIt compliance rule packs for kernel plugin registration
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class FlashItComplianceRulePack {

    public static final String MOMENT_PRIVACY = "FLASHIT_MOMENT_PRIVACY";

    private FlashItComplianceRulePack() {
    }

    public static List<CompliancePlugin.ComplianceRule> momentPrivacyRules() {
        return List.of(
                new CompliancePlugin.ComplianceRule(
                        "FLASHIT-CR-001",
                        MOMENT_PRIVACY,
                        "Sensitive moment exports must emit audit evidence before release",
                        CompliancePlugin.Severity.HIGH,
                        "$.auditEventEmitted == true"
                ),
                new CompliancePlugin.ComplianceRule(
                        "FLASHIT-CR-002",
                        MOMENT_PRIVACY,
                        "AI-assisted reflections must honor explicit disabled mode",
                        CompliancePlugin.Severity.HIGH,
                        "$.aiDisabled != null"
                )
        );
    }
}
