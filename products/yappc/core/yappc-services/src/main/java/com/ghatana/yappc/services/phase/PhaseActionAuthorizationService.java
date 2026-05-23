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
                : blockers.size() + " blocker(s) must be resolved before continuing";

        actions.add(new PhasePacket.PhaseAction(
                "advance-phase",
                "Advance to Next Phase",
                "Move to the next lifecycle phase",
                capabilities.canUpdate() && readiness.canAdvance() && policyAllowed && enabledFlags.contains("phase.advance"),
                firstDisabledReason(
                        capabilities.canUpdate() ? null : "Update capability is required",
                        readiness.canAdvance() ? null : blockerReason,
                        policyAllowed ? null : "Policy denied this phase transition",
                        enabledFlags.contains("phase.advance") ? null : "Phase advance entitlement is not enabled"
                ),
                "phase:advance",
                Map.of("nextPhase", Optional.ofNullable(readiness.nextPhase()).orElse(""))
        ));

        actions.add(new PhasePacket.PhaseAction(
                "configure-phase",
                "Configure Phase",
                "Configure phase-specific settings",
                capabilities.canApprove() && policyAllowed && enabledFlags.contains("phase.governance.configure"),
                firstDisabledReason(
                        capabilities.canApprove() ? null : "Approval capability is required",
                        policyAllowed ? null : "Policy denied governance configuration",
                        enabledFlags.contains("phase.governance.configure") ? null : "Governance configuration entitlement is not enabled"
                ),
                "phase:configure",
                Map.of()
        ));

        boolean canExportReport = tier == PhasePacket.TenantTier.ENTERPRISE
                && capabilities.canRead()
                && enabledFlags.contains("phase.report.export");
        actions.add(new PhasePacket.PhaseAction(
                "export-report",
                "Export Phase Report",
                "Export detailed phase report",
                canExportReport,
                firstDisabledReason(
                        tier == PhasePacket.TenantTier.ENTERPRISE ? null : "Enterprise tier is required",
                        capabilities.canRead() ? null : "Read capability is required",
                        enabledFlags.contains("phase.report.export") ? null : "Report export entitlement is not enabled"
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