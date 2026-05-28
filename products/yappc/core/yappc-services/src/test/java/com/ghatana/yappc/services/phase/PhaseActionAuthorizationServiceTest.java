package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.capability.CapabilityEvaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PhaseActionAuthorizationService}.
 */
@DisplayName("PhaseActionAuthorizationService")
class PhaseActionAuthorizationServiceTest {

    private final PhaseActionAuthorizationService service = new PhaseActionAuthorizationService();

    @Test
    @DisplayName("policy approval enables mutating phase actions when capabilities readiness and flags pass")
    void policyApprovalEnablesMutatingPhaseActions() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "GENERATE",
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance", "phase.governance.configure"),
                new PhasePacket.PhaseReadiness(true, "GENERATE", List.of(), 1.0, false),
                List.of(),
                List.of(governance("POLICY_APPROVAL", "POLICY_APPROVAL", "APPROVED")),
                true
        );

        PhasePacket.PhaseAction advance = action(actions, "advance-phase");
        PhasePacket.PhaseAction configure = action(actions, "configure-phase");

        assertThat(advance.enabled()).isTrue();
        assertThat(advance.disabledReason()).isNull();
                assertThat(advance.targetType()).isEqualTo("server");
                assertThat(advance.serverOperation()).isEqualTo("phase.advance");
                assertThat(advance.postSuccessBehavior()).isEqualTo("refresh-packet");
        assertThat(configure.enabled()).isTrue();
        assertThat(configure.disabledReason()).isNull();

                PhasePacket.PhaseAction generateApply = action(actions, "generate.apply");
                assertThat(generateApply.enabled()).isTrue();
                assertThat(generateApply.category()).isEqualTo("review");
                assertThat(generateApply.parameters()).containsEntry("approvalRequired", true);
    }

    @Test
    @DisplayName("denied governance disables advance action")
    void deniedGovernanceDisablesAdvanceAction() {
        CapabilityEvaluationService.CapabilityModel capabilities = CapabilityEvaluationService.CapabilityModel.allGranted();
        PhasePacket.PhaseReadiness readiness = new PhasePacket.PhaseReadiness(
                true,
                "SHAPE",
                List.of(),
                1.0,
                false
        );

        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "SHAPE",
                capabilities,
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance"),
                readiness,
                List.of(),
                List.of(new PhasePacket.GovernanceRecord(
                        "POLICY_DENIAL",
                        "POLICY_DENIAL",
                        "Policy denied",
                        "system",
                        Instant.now(),
                        Map.of(),
                        "decision-1"
                )),
                true
        );

        PhasePacket.PhaseAction advance = actions.stream()
                .filter(action -> "advance-phase".equals(action.actionId()))
                .findFirst()
                .orElseThrow();

        assertThat(advance.enabled()).isFalse();
        assertThat(advance.label()).isEqualTo("phaseAction.advancePhase.label");
        assertThat(advance.description()).isEqualTo("phaseAction.advancePhase.description");
        assertThat(advance.disabledReason()).isEqualTo("phaseAction.disabled.policyDeniedTransition");
        assertThat(advance.category()).isEqualTo("phase-transition");
        assertThat(advance.severity()).isEqualTo("high");
        assertThat(advance.confirmationRequired()).isTrue();
        assertThat(advance.idempotencyKey()).isEqualTo("phase.advance");
        assertThat(advance.auditType()).isEqualTo("phase.advance.requested");
    }

    @Test
    @DisplayName("policy denial disables all mutating phase actions")
    void policyDenialDisablesAllMutatingPhaseActions() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "GENERATE",
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance", "phase.governance.configure"),
                new PhasePacket.PhaseReadiness(true, "GENERATE", List.of(), 1.0, false),
                List.of(),
                List.of(governance("POLICY_DENIAL", "POLICY_DENIAL", "DENIED")),
                true
        );

        PhasePacket.PhaseAction advance = action(actions, "advance-phase");
        PhasePacket.PhaseAction configure = action(actions, "configure-phase");

        assertThat(advance.enabled()).isFalse();
        assertThat(advance.disabledReason()).isEqualTo("phaseAction.disabled.policyDeniedTransition");
        assertThat(configure.enabled()).isFalse();
        assertThat(configure.disabledReason())
                .isEqualTo("phaseAction.disabled.policyDeniedGovernanceConfiguration");
    }

    @Test
    @DisplayName("policy query error record fails closed for all mutating phase actions")
    void policyErrorRecordFailsClosedForAllMutatingPhaseActions() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "GENERATE",
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance", "phase.governance.configure"),
                new PhasePacket.PhaseReadiness(true, "GENERATE", List.of(), 1.0, false),
                List.of(),
                List.of(new PhasePacket.GovernanceRecord(
                        "GOVERNANCE_QUERY_FAILED",
                        "POLICY_DENIAL",
                        "DENIED",
                        "system",
                        Instant.parse("2026-05-26T10:15:30Z"),
                        Map.of("reason", "TimeoutException"),
                        "governance-query-failed:project-1:GENERATE")),
                true
        );

        PhasePacket.PhaseAction advance = action(actions, "advance-phase");
        PhasePacket.PhaseAction configure = action(actions, "configure-phase");

        assertThat(advance.enabled()).isFalse();
        assertThat(advance.disabledReason()).isEqualTo("phaseAction.disabled.policyDeniedTransition");
        assertThat(configure.enabled()).isFalse();
        assertThat(configure.disabledReason())
                .isEqualTo("phaseAction.disabled.policyDeniedGovernanceConfiguration");
    }

    @Test
    @DisplayName("missing phase advance flag disables advance action")
    void missingAdvanceFlagDisablesAdvanceAction() {
        CapabilityEvaluationService.CapabilityModel capabilities = CapabilityEvaluationService.CapabilityModel.allGranted();
        PhasePacket.PhaseReadiness readiness = new PhasePacket.PhaseReadiness(
                true,
                "GENERATE",
                List.of(),
                1.0,
                false
        );

        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "GENERATE",
                capabilities,
                PhasePacket.TenantTier.PRO,
                Set.of(),
                readiness,
                List.of(),
                List.of(new PhasePacket.GovernanceRecord(
                        "POLICY_APPROVAL",
                        "POLICY_APPROVAL",
                        "Allowed",
                        "system",
                        Instant.now(),
                        Map.of(),
                        "decision-2"
                )),
                true
        );

        PhasePacket.PhaseAction advance = actions.stream()
                .filter(action -> "advance-phase".equals(action.actionId()))
                .findFirst()
                .orElseThrow();

        assertThat(advance.enabled()).isFalse();
        assertThat(advance.disabledReason()).isEqualTo("phaseAction.disabled.phaseAdvanceEntitlementMissing");
    }

    @Test
    @DisplayName("enterprise report action is enabled only for enterprise tier with flag")
    void enterpriseReportActionRequiresTierAndFlag() {
        CapabilityEvaluationService.CapabilityModel capabilities = CapabilityEvaluationService.CapabilityModel.allGranted();
        PhasePacket.PhaseReadiness readiness = new PhasePacket.PhaseReadiness(
                true,
                "RUN",
                List.of(),
                1.0,
                false
        );

        List<PhasePacket.PhaseAction> proActions = service.determineAvailableActions(
                "RUN",
                capabilities,
                PhasePacket.TenantTier.PRO,
                Set.of("phase.report.export", "phase.advance", "phase.governance.configure"),
                readiness,
                List.of(),
                List.of(),
                true
        );

        List<PhasePacket.PhaseAction> enterpriseActions = service.determineAvailableActions(
                "RUN",
                capabilities,
                PhasePacket.TenantTier.ENTERPRISE,
                Set.of("phase.report.export", "phase.advance", "phase.governance.configure"),
                readiness,
                List.of(),
                List.of(),
                true
        );

        PhasePacket.PhaseAction proReport = proActions.stream()
                .filter(action -> "export-report".equals(action.actionId()))
                .findFirst()
                .orElseThrow();
        PhasePacket.PhaseAction enterpriseReport = enterpriseActions.stream()
                .filter(action -> "export-report".equals(action.actionId()))
                .findFirst()
                .orElseThrow();

        assertThat(proReport.enabled()).isFalse();
        assertThat(enterpriseReport.enabled()).isTrue();
        assertThat(enterpriseReport.category()).isEqualTo("report");
        assertThat(enterpriseReport.confirmationRequired()).isFalse();
        assertThat(enterpriseReport.auditType()).isEqualTo("phase.report.exported");

        PhasePacket.PhaseAction observe = action(enterpriseActions, "run.observe");
        assertThat(observe.enabled()).isTrue();
        assertThat(observe.targetType()).isEqualTo("route");
        assertThat(observe.targetRoute()).isEqualTo("observe");
    }

    @Test
    @DisplayName("feature flag dependency degraded disables entitlement-dependent actions")
    void featureFlagDependencyDegradedDisablesEntitlementDependentActions() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "INTENT",
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.ENTERPRISE,
                Set.of("phase.advance", "phase.governance.configure", "phase.report.export"),
                new PhasePacket.PhaseReadiness(true, "SHAPE", List.of(), 1.0, false),
                List.of(),
                List.of(governance("POLICY_APPROVAL", "POLICY_APPROVAL", "APPROVED")),
                false
        );

        assertThat(action(actions, "advance-phase").enabled()).isFalse();
        assertThat(action(actions, "advance-phase").disabledReason())
                .isEqualTo("phaseAction.disabled.featureFlagDependencyUnavailable");
        assertThat(action(actions, "configure-phase").enabled()).isFalse();
        assertThat(action(actions, "export-report").enabled()).isFalse();
    }

    private static PhasePacket.PhaseAction action(List<PhasePacket.PhaseAction> actions, String actionId) {
        return actions.stream()
                .filter(action -> actionId.equals(action.actionId()))
                .findFirst()
                .orElseThrow();
    }

    private static PhasePacket.GovernanceRecord governance(String id, String type, String outcome) {
        return new PhasePacket.GovernanceRecord(
                id,
                type,
                outcome,
                "system",
                Instant.parse("2026-05-26T10:15:30Z"),
                Map.of(),
                "decision-1"
        );
    }
}
