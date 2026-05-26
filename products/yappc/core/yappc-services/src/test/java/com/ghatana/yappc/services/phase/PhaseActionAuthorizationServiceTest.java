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
                ))
        );

        PhasePacket.PhaseAction advance = actions.stream()
                .filter(action -> "advance-phase".equals(action.actionId()))
                .findFirst()
                .orElseThrow();

        assertThat(advance.enabled()).isFalse();
        assertThat(advance.label()).isEqualTo("phaseAction.advancePhase.label");
        assertThat(advance.description()).isEqualTo("phaseAction.advancePhase.description");
        assertThat(advance.disabledReason()).isEqualTo("phaseAction.disabled.policyDeniedTransition");
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
                ))
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
                capabilities,
                PhasePacket.TenantTier.PRO,
                Set.of("phase.report.export", "phase.advance", "phase.governance.configure"),
                readiness,
                List.of(),
                List.of()
        );

        List<PhasePacket.PhaseAction> enterpriseActions = service.determineAvailableActions(
                capabilities,
                PhasePacket.TenantTier.ENTERPRISE,
                Set.of("phase.report.export", "phase.advance", "phase.governance.configure"),
                readiness,
                List.of(),
                List.of()
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
    }
}
