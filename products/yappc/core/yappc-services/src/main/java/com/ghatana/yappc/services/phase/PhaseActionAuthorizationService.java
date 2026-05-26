/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.capability.CapabilityEvaluationService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side authorizer for phase actions.
 *
 * <p>Centralizes authorization decisions for phase actions so action enablement
 * is always derived from backend capability, policy, readiness, and entitlements.
 *
 * @doc.type class
 * @doc.purpose Resolve backend phase action authorization decisions
 * @doc.layer services
 * @doc.pattern Service
 */
public final class PhaseActionAuthorizationService {

    /**
     * Calculates the action contract for a phase packet.
     */
    public List<PhasePacket.PhaseAction> determineAvailableActions(
            @NotNull CapabilityEvaluationService.CapabilityModel capabilities,
            @NotNull PhasePacket.TenantTier tier,
            @NotNull Set<String> enabledFlags,
            @NotNull PhasePacket.PhaseReadiness readiness,
            @NotNull List<PhasePacket.PhaseBlocker> blockers,
            @NotNull List<PhasePacket.GovernanceRecord> governance
    ) {
        List<PhasePacket.PhaseAction> actions = new ArrayList<>();
        boolean policyAllowed = governance.stream().noneMatch(this::isPolicyDenied);
        String blockerReason = blockers.isEmpty()
                ? null
                : "phaseAction.disabled.blockersMustResolve";

        actions.add(new PhasePacket.PhaseAction(
                "advance-phase",
                "phaseAction.advancePhase.label",
                "phaseAction.advancePhase.description",
                capabilities.canUpdate() && readiness.canAdvance() && policyAllowed && enabledFlags.contains("phase.advance"),
                firstDisabledReason(
                        capabilities.canUpdate() ? null : "phaseAction.disabled.updateCapabilityRequired",
                        readiness.canAdvance() ? null : blockerReason,
                        policyAllowed ? null : "phaseAction.disabled.policyDeniedTransition",
                        enabledFlags.contains("phase.advance") ? null : "phaseAction.disabled.phaseAdvanceEntitlementMissing"
                ),
                "phase:advance",
                Map.of("nextPhase", Optional.ofNullable(readiness.nextPhase()).orElse(""))
        ));

        actions.add(new PhasePacket.PhaseAction(
                "configure-phase",
                "phaseAction.configurePhase.label",
                "phaseAction.configurePhase.description",
                capabilities.canApprove() && policyAllowed && enabledFlags.contains("phase.governance.configure"),
                firstDisabledReason(
                        capabilities.canApprove() ? null : "phaseAction.disabled.approvalCapabilityRequired",
                        policyAllowed ? null : "phaseAction.disabled.policyDeniedGovernanceConfiguration",
                        enabledFlags.contains("phase.governance.configure") ? null : "phaseAction.disabled.governanceConfigurationEntitlementMissing"
                ),
                "phase:configure",
                Map.of()
        ));

        boolean canExportReport = tier == PhasePacket.TenantTier.ENTERPRISE
                && capabilities.canRead()
                && enabledFlags.contains("phase.report.export");
        actions.add(new PhasePacket.PhaseAction(
                "export-report",
                "phaseAction.exportReport.label",
                "phaseAction.exportReport.description",
                canExportReport,
                firstDisabledReason(
                        tier == PhasePacket.TenantTier.ENTERPRISE ? null : "phaseAction.disabled.enterpriseTierRequired",
                        capabilities.canRead() ? null : "phaseAction.disabled.readCapabilityRequired",
                        enabledFlags.contains("phase.report.export") ? null : "phaseAction.disabled.reportExportEntitlementMissing"
                ),
                "report:export",
                Map.of()
        ));

        return List.copyOf(actions);
    }

    private boolean isPolicyDenied(PhasePacket.GovernanceRecord record) {
        return "DENIED".equalsIgnoreCase(record.outcome())
                || "POLICY_DENIAL".equalsIgnoreCase(record.type());
    }

    private String firstDisabledReason(String... reasons) {
        for (String reason : reasons) {
            if (reason != null && !reason.isBlank()) {
                return reason;
            }
        }
        return null;
    }
}
