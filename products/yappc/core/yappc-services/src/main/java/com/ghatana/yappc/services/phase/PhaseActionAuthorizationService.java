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
            @NotNull String phase,
            @NotNull CapabilityEvaluationService.CapabilityModel capabilities,
            @NotNull PhasePacket.TenantTier tier,
            @NotNull Set<String> enabledFlags,
            @NotNull PhasePacket.PhaseReadiness readiness,
            @NotNull List<PhasePacket.PhaseBlocker> blockers,
            @NotNull List<PhasePacket.GovernanceRecord> governance,
            boolean featureFlagDependencyAvailable
    ) {
        List<PhasePacket.PhaseAction> actions = new ArrayList<>();
        String normalizedPhase = phase.trim().toUpperCase();
        boolean policyAllowed = governance.stream().noneMatch(this::isPolicyDenied);
        String blockerReason = blockers.isEmpty()
                ? null
                : "phaseAction.disabled.blockersMustResolve";

        actions.add(action(
                "advance-phase",
                "phaseAction.advancePhase.label",
                "phaseAction.advancePhase.description",
                capabilities.canUpdate() && readiness.canAdvance() && policyAllowed
                        && featureFlagDependencyAvailable && enabledFlags.contains("phase.advance"),
                firstDisabledReason(
                        capabilities.canUpdate() ? null : "phaseAction.disabled.updateCapabilityRequired",
                        readiness.canAdvance() ? null : blockerReason,
                        policyAllowed ? null : "phaseAction.disabled.policyDeniedTransition",
                        featureFlagDependencyAvailable ? null : "phaseAction.disabled.featureFlagDependencyUnavailable",
                        enabledFlags.contains("phase.advance") ? null : "phaseAction.disabled.phaseAdvanceEntitlementMissing"
                ),
                "phase:advance",
                "phase-transition",
                "high",
                true,
                "phase.advance",
                "phase.advance.requested",
                "server",
                null,
                null,
                true,
                "phase.advance",
                "refresh-packet",
                suggestionParameters(
                        0.95,
                        "low",
                        "one-click",
                        false,
                        true,
                        Map.of("nextPhase", Optional.ofNullable(readiness.nextPhase()).orElse(""))
                )
        ));

        actions.add(action(
                "configure-phase",
                "phaseAction.configurePhase.label",
                "phaseAction.configurePhase.description",
                capabilities.canApprove() && policyAllowed
                        && featureFlagDependencyAvailable
                        && enabledFlags.contains("phase.governance.configure"),
                firstDisabledReason(
                        capabilities.canApprove() ? null : "phaseAction.disabled.approvalCapabilityRequired",
                        policyAllowed ? null : "phaseAction.disabled.policyDeniedGovernanceConfiguration",
                        featureFlagDependencyAvailable ? null : "phaseAction.disabled.featureFlagDependencyUnavailable",
                        enabledFlags.contains("phase.governance.configure") ? null : "phaseAction.disabled.governanceConfigurationEntitlementMissing"
                ),
                "phase:configure",
                "governance",
                "medium",
                true,
                "phase.configure",
                "phase.governance.configure.requested",
                "drawer",
                null,
                "governance",
                false,
                "phase.governance.configure",
                "refresh-packet",
                suggestionParameters(
                        0.9,
                        "medium",
                        "review-required",
                        true,
                        false,
                        Map.of()
                )
        ));

        boolean canExportReport = tier == PhasePacket.TenantTier.ENTERPRISE
                && capabilities.canRead()
                && featureFlagDependencyAvailable
                && enabledFlags.contains("phase.report.export");
        actions.add(action(
                "export-report",
                "phaseAction.exportReport.label",
                "phaseAction.exportReport.description",
                canExportReport,
                firstDisabledReason(
                        tier == PhasePacket.TenantTier.ENTERPRISE ? null : "phaseAction.disabled.enterpriseTierRequired",
                        capabilities.canRead() ? null : "phaseAction.disabled.readCapabilityRequired",
                        featureFlagDependencyAvailable ? null : "phaseAction.disabled.featureFlagDependencyUnavailable",
                        enabledFlags.contains("phase.report.export") ? null : "phaseAction.disabled.reportExportEntitlementMissing"
                ),
                "report:export",
                "report",
                "low",
                false,
                "phase.report.export",
                "phase.report.exported",
                "route",
                "/reports",
                null,
                false,
                "phase.report.export",
                "download",
                suggestionParameters(
                        0.92,
                        "low",
                        "one-click",
                        false,
                        false,
                        Map.of()
                )
        ));

        boolean generatePhase = "GENERATE".equals(normalizedPhase);
        actions.add(action(
                "generate.apply",
                "Apply recommendation",
                "Apply reviewed generation output to project artifacts.",
                generatePhase && capabilities.canApprove() && policyAllowed,
                firstDisabledReason(
                        generatePhase ? null : "phaseAction.disabled.unauthorized",
                        capabilities.canApprove() ? null : "phaseAction.disabled.approvalCapabilityRequired",
                        policyAllowed ? null : "phaseAction.disabled.policyDeniedTransition"
                ),
                "generate:apply",
                "review",
                "medium",
                true,
                "generate.apply",
                "generate.review.apply",
                "server",
                null,
                null,
                false,
                "generate.apply",
                "refresh-packet",
                suggestionParameters(
                        0.88,
                        "medium",
                        "review-required",
                        true,
                        true,
                        Map.of("requiresActor", true)
                )
        ));

        actions.add(action(
                "generate.reject",
                "Reject recommendation",
                "Reject reviewed generation output and keep existing artifacts.",
                generatePhase && capabilities.canReject() && policyAllowed,
                firstDisabledReason(
                        generatePhase ? null : "phaseAction.disabled.unauthorized",
                        capabilities.canReject() ? null : "phaseAction.disabled.approvalCapabilityRequired",
                        policyAllowed ? null : "phaseAction.disabled.policyDeniedTransition"
                ),
                "generate:reject",
                "review",
                "medium",
                true,
                "generate.reject",
                "generate.review.reject",
                "server",
                null,
                null,
                false,
                "generate.reject",
                "refresh-packet",
                suggestionParameters(
                        0.86,
                        "low",
                        "review-required",
                        true,
                        false,
                        Map.of("requiresActor", true)
                )
        ));

        actions.add(action(
                "generate.rollback",
                "Rollback generation",
                "Rollback to the previous generated revision.",
                generatePhase && capabilities.canRollback() && policyAllowed,
                firstDisabledReason(
                        generatePhase ? null : "phaseAction.disabled.unauthorized",
                        capabilities.canRollback() ? null : "phaseAction.disabled.updateCapabilityRequired",
                        policyAllowed ? null : "phaseAction.disabled.policyDeniedTransition"
                ),
                "generate:rollback",
                "danger",
                "high",
                true,
                "generate.rollback",
                "generate.review.rollback",
                "server",
                null,
                null,
                false,
                "generate.rollback",
                "refresh-packet",
                suggestionParameters(
                        0.84,
                        "high",
                        "review-required",
                        true,
                        true,
                        Map.of("requiresActor", true)
                )
        ));

        boolean runPhase = "RUN".equals(normalizedPhase);
        actions.add(action(
                "run.retry",
                "Retry run",
                "Retry the latest run with the same validated configuration.",
                runPhase && capabilities.canUpdate() && policyAllowed,
                firstDisabledReason(
                        runPhase ? null : "phaseAction.disabled.unauthorized",
                        capabilities.canUpdate() ? null : "phaseAction.disabled.updateCapabilityRequired",
                        policyAllowed ? null : "phaseAction.disabled.policyDeniedTransition"
                ),
                "run:retry",
                "post-run",
                "medium",
                true,
                "run.retry",
                "run.post.retry",
                "server",
                null,
                null,
                false,
                "run.retry",
                "refresh-packet",
                suggestionParameters(
                        0.82,
                        "medium",
                        "one-click",
                        false,
                        true,
                        Map.of("requiresActor", false)
                )
        ));

        actions.add(action(
                "run.rollback",
                "Rollback run",
                "Rollback the latest run deployment.",
                runPhase && capabilities.canRollback() && policyAllowed,
                firstDisabledReason(
                        runPhase ? null : "phaseAction.disabled.unauthorized",
                        capabilities.canRollback() ? null : "phaseAction.disabled.updateCapabilityRequired",
                        policyAllowed ? null : "phaseAction.disabled.policyDeniedTransition"
                ),
                "run:rollback",
                "danger",
                "high",
                true,
                "run.rollback",
                "run.post.rollback",
                "server",
                null,
                null,
                false,
                "run.rollback",
                "refresh-packet",
                suggestionParameters(
                        0.8,
                        "high",
                        "manual",
                        false,
                        true,
                        Map.of("requiresActor", false)
                )
        ));

        actions.add(action(
                "run.promote",
                "Promote run",
                "Promote the latest successful run to the next environment.",
                runPhase && capabilities.canApprove() && policyAllowed,
                firstDisabledReason(
                        runPhase ? null : "phaseAction.disabled.unauthorized",
                        capabilities.canApprove() ? null : "phaseAction.disabled.approvalCapabilityRequired",
                        policyAllowed ? null : "phaseAction.disabled.policyDeniedTransition"
                ),
                "run:promote",
                "post-run",
                "medium",
                true,
                "run.promote",
                "run.post.promote",
                "server",
                null,
                null,
                false,
                "run.promote",
                "refresh-packet",
                suggestionParameters(
                        0.83,
                        "medium",
                        "review-required",
                        true,
                        true,
                        Map.of("requiresActor", true)
                )
        ));

        actions.add(action(
                "run.observe",
                "Open observe phase",
                "Navigate to observe phase to inspect runtime signals and outcomes.",
                runPhase && capabilities.canRead(),
                firstDisabledReason(
                        runPhase ? null : "phaseAction.disabled.unauthorized",
                        capabilities.canRead() ? null : "phaseAction.disabled.readCapabilityRequired"
                ),
                "run:observe",
                "post-run",
                "low",
                false,
                "run.observe",
                "run.post.observe",
                "route",
                "observe",
                null,
                false,
                "run.observe",
                "navigate-observe",
                suggestionParameters(
                        0.9,
                        "low",
                        "one-click",
                        false,
                        false,
                        Map.of("requiresActor", false)
                )
        ));

        return List.copyOf(actions);
    }

    private PhasePacket.PhaseAction action(
            String actionId,
            String label,
            String description,
            boolean enabled,
            String disabledReason,
            String requiredPermission,
            String category,
            String severity,
            boolean confirmationRequired,
            String idempotencyKey,
            String auditType,
            String targetType,
            String targetRoute,
            String targetDrawer,
            boolean requiresPreview,
            String serverOperation,
            String postSuccessBehavior,
            Map<String, Object> parameters
    ) {
        return new PhasePacket.PhaseAction(
                actionId,
                label,
                description,
                enabled,
                disabledReason,
                requiredPermission,
                category,
                severity,
                confirmationRequired,
                idempotencyKey,
                auditType,
                targetType,
                targetRoute,
                targetDrawer,
                requiresPreview,
                serverOperation,
                postSuccessBehavior,
                parameters
        );
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

        private Map<String, Object> suggestionParameters(
                        double confidence,
                        String riskLevel,
                        String applyMode,
                        boolean approvalRequired,
                        boolean rollbackSupported,
                        Map<String, Object> additional
        ) {
                Map<String, Object> parameters = new java.util.LinkedHashMap<>();
                parameters.put("confidence", confidence);
                parameters.put("evidence", List.of());
                parameters.put("riskLevel", riskLevel);
                parameters.put("applyMode", applyMode);
                parameters.put("approvalRequired", approvalRequired);
                parameters.put("rollbackSupported", rollbackSupported);
                parameters.putAll(additional);
                return Map.copyOf(parameters);
        }
}
