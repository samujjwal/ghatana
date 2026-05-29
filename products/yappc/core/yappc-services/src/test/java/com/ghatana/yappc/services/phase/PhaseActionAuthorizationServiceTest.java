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
                true,
                RunActionContext.degraded(List.of("evidence-1", "evidence-2"), List.of())
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
                true,
                RunActionContext.degraded(List.of(), List.of())
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
                true,
                RunActionContext.degraded(List.of(), List.of())
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
                true,
                RunActionContext.degraded(List.of(), List.of())
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
                true,
                RunActionContext.degraded(List.of(), List.of())
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
                true,
                RunActionContext.degraded(List.of(), List.of())
        );

        List<PhasePacket.PhaseAction> enterpriseActions = service.determineAvailableActions(
                "RUN",
                capabilities,
                PhasePacket.TenantTier.ENTERPRISE,
                Set.of("phase.report.export", "phase.advance", "phase.governance.configure"),
                readiness,
                List.of(),
                List.of(),
                true,
                RunActionContext.degraded(List.of(), List.of())
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
                false,
                RunActionContext.degraded(List.of(), List.of())
        );

        assertThat(action(actions, "advance-phase").enabled()).isFalse();
        assertThat(action(actions, "advance-phase").disabledReason())
                .isEqualTo("phaseAction.disabled.featureFlagDependencyUnavailable");
        assertThat(action(actions, "configure-phase").enabled()).isFalse();
        assertThat(action(actions, "export-report").enabled()).isFalse();
    }

    @Test
    @DisplayName("evidence parameters are propagated to action suggestion parameters")
    void evidenceParametersPropagatedToSuggestionParameters() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "GENERATE",
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance", "phase.governance.configure"),
                new PhasePacket.PhaseReadiness(true, "GENERATE", List.of(), 1.0, false),
                List.of(),
                List.of(governance("POLICY_APPROVAL", "POLICY_APPROVAL", "APPROVED")),
                true,
                runContext(
                        List.of("evidence-1", "evidence-2", "evidence-3"),
                        "support-trace-abc",
                        "medium-risk",
                        "",
                        "",
                        false
                )
        );

        PhasePacket.PhaseAction advance = action(actions, "advance-phase");
        assertThat(advance.parameters()).containsEntry("evidenceIds", List.of("evidence-1", "evidence-2", "evidence-3"));
        assertThat(advance.parameters()).containsEntry("supportTrace", "support-trace-abc");
        assertThat(advance.parameters()).containsEntry("riskReason", "medium-risk");
    }

    @Test
    @DisplayName("missing target version disables rollback action")
    void missingTargetVersionDisablesRollbackAction() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "RUN",
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance"),
                new PhasePacket.PhaseReadiness(true, "RUN", List.of(), 1.0, false),
                List.of(),
                List.of(governance("POLICY_APPROVAL", "POLICY_APPROVAL", "APPROVED")),
                true,
                RunActionContext.degraded(List.of(), List.of())
        );

        PhasePacket.PhaseAction rollback = action(actions, "run.rollback");
        assertThat(rollback.enabled()).isFalse();
        assertThat(rollback.disabledReason()).isEqualTo("phaseAction.disabled.missingRollbackTarget");
    }

    @Test
    @DisplayName("missing target environment disables promote action")
    void missingTargetEnvironmentDisablesPromoteAction() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "RUN",
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance"),
                new PhasePacket.PhaseReadiness(true, "RUN", List.of(), 1.0, false),
                List.of(),
                List.of(governance("POLICY_APPROVAL", "POLICY_APPROVAL", "APPROVED")),
                true,
                RunActionContext.degraded(List.of(), List.of())
        );

        PhasePacket.PhaseAction promote = action(actions, "run.promote");
        assertThat(promote.enabled()).isFalse();
        assertThat(promote.disabledReason()).isEqualTo("phaseAction.disabled.missingPromoteTarget");
    }

    @Test
    @DisplayName("target version is included in rollback action parameters when available")
    void targetVersionIncludedInRollbackParameters() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "RUN",
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance"),
                new PhasePacket.PhaseReadiness(true, "RUN", List.of(), 1.0, false),
                List.of(),
                List.of(governance("POLICY_APPROVAL", "POLICY_APPROVAL", "APPROVED")),
                true,
                runContext(List.of(), "", "low", "v1.5.0", "", true)
        );

        PhasePacket.PhaseAction rollback = action(actions, "run.rollback");
        assertThat(rollback.enabled()).isTrue();
        assertThat(rollback.parameters()).containsEntry("targetVersion", "v1.5.0");
    }

    @Test
    @DisplayName("target environment is included in promote action parameters when available")
    void targetEnvironmentIncludedInPromoteParameters() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "RUN",
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance"),
                new PhasePacket.PhaseReadiness(true, "RUN", List.of(), 1.0, false),
                List.of(),
                List.of(governance("POLICY_APPROVAL", "POLICY_APPROVAL", "APPROVED")),
                true,
                runContext(List.of(), "", "low", "", "staging", false)
        );

        PhasePacket.PhaseAction promote = action(actions, "run.promote");
        assertThat(promote.enabled()).isTrue();
        assertThat(promote.parameters()).containsEntry("targetEnvironment", "staging");
    }

    @Test
    @DisplayName("generate actions use i18n keys for labels and descriptions")
    void generateActionsUseI18nKeys() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "GENERATE",
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance"),
                new PhasePacket.PhaseReadiness(true, "GENERATE", List.of(), 1.0, false),
                List.of(),
                List.of(governance("POLICY_APPROVAL", "POLICY_APPROVAL", "APPROVED")),
                true,
                RunActionContext.degraded(List.of(), List.of())
        );

        PhasePacket.PhaseAction apply = action(actions, "generate.apply");
        assertThat(apply.label()).isEqualTo("phaseAction.generateApply.label");
        assertThat(apply.description()).isEqualTo("phaseAction.generateApply.description");

        PhasePacket.PhaseAction reject = action(actions, "generate.reject");
        assertThat(reject.label()).isEqualTo("phaseAction.generateReject.label");
        assertThat(reject.description()).isEqualTo("phaseAction.generateReject.description");

        PhasePacket.PhaseAction rollback = action(actions, "generate.rollback");
        assertThat(rollback.label()).isEqualTo("phaseAction.generateRollback.label");
        assertThat(rollback.description()).isEqualTo("phaseAction.generateRollback.description");
    }

    @Test
    @DisplayName("run actions use i18n keys for labels and descriptions")
    void runActionsUseI18nKeys() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "RUN",
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance"),
                new PhasePacket.PhaseReadiness(true, "RUN", List.of(), 1.0, false),
                List.of(),
                List.of(governance("POLICY_APPROVAL", "POLICY_APPROVAL", "APPROVED")),
                true,
                RunActionContext.degraded(List.of(), List.of())
        );

        PhasePacket.PhaseAction retry = action(actions, "run.retry");
        assertThat(retry.label()).isEqualTo("phaseAction.runRetry.label");
        assertThat(retry.description()).isEqualTo("phaseAction.runRetry.description");

        PhasePacket.PhaseAction rollback = action(actions, "run.rollback");
        assertThat(rollback.label()).isEqualTo("phaseAction.runRollback.label");
        assertThat(rollback.description()).isEqualTo("phaseAction.runRollback.description");

        PhasePacket.PhaseAction promote = action(actions, "run.promote");
        assertThat(promote.label()).isEqualTo("phaseAction.runPromote.label");
        assertThat(promote.description()).isEqualTo("phaseAction.runPromote.description");

        PhasePacket.PhaseAction observe = action(actions, "run.observe");
        assertThat(observe.label()).isEqualTo("phaseAction.runObserve.label");
        assertThat(observe.description()).isEqualTo("phaseAction.runObserve.description");
    }

    @Test
    @DisplayName("phase mismatch uses specific disabled reason instead of generic unauthorized")
    void phaseMismatchUsesSpecificDisabledReason() {
        List<PhasePacket.PhaseAction> actions = service.determineAvailableActions(
                "INTENT", // Not GENERATE phase
                CapabilityEvaluationService.CapabilityModel.allGranted(),
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance"),
                new PhasePacket.PhaseReadiness(true, "INTENT", List.of(), 1.0, false),
                List.of(),
                List.of(governance("POLICY_APPROVAL", "POLICY_APPROVAL", "APPROVED")),
                true,
                RunActionContext.degraded(List.of(), List.of())
        );

        PhasePacket.PhaseAction generateApply = action(actions, "generate.apply");
        assertThat(generateApply.enabled()).isFalse();
        assertThat(generateApply.disabledReason()).isEqualTo("phaseAction.disabled.notAvailableForCurrentPhase");

        PhasePacket.PhaseAction runRetry = action(actions, "run.retry");
        assertThat(runRetry.enabled()).isFalse();
        assertThat(runRetry.disabledReason()).isEqualTo("phaseAction.disabled.notAvailableForCurrentPhase");
    }

    private static PhasePacket.PhaseAction action(List<PhasePacket.PhaseAction> actions, String actionId) {
        return actions.stream()
                .filter(action -> actionId.equals(action.actionId()))
                .findFirst()
                .orElseThrow();
    }

    private static RunActionContext runContext(
            List<String> evidenceIds,
            String supportTrace,
            String riskLevel,
            String rollbackTarget,
            String promoteTarget,
            boolean rollbackSupported
    ) {
        return RunActionContext.fromPlatformRunStatus(
                new PhasePacket.PlatformRunStatus(
                        "run-1",
                        "SUCCEEDED",
                        "data-cloud-aep",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:05:00Z"),
                        supportTrace,
                        evidenceIds,
                        rollbackTarget,
                        promoteTarget,
                        "",
                        riskLevel,
                        "",
                        rollbackSupported
                ),
                evidenceIds,
                List.of()
        );
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
