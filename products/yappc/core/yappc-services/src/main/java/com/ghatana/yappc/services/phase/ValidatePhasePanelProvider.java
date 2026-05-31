package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.Map;

/**
 * Validate phase panel provider.
 *
 * @doc.type class
 * @doc.purpose Provide Validate phase panel views
 * @doc.layer product
 * @doc.pattern PhasePanelProvider
 */
public final class ValidatePhasePanelProvider implements PhasePanelProvider {
    @Override
    public String phase() {
        return "validate";
    }

    @Override
    public PhasePacket.PhasePanelView build(PhasePanelInput input) {
        return new PhasePacket.PhasePanelView(
                "validate",
                input.readiness().canAdvance() ? "ready" : "blocked",
                input.readiness().canAdvance() ? "phasePanel.validate.summary.ready" : "phasePanel.validate.summary.blocked",
                input.readiness().canAdvance() ? "phasePanel.validate.recommendation.proceed" : "phasePanel.validate.recommendation.remediate",
                "backend",
                input.confidence(),
                "backend:validation-gate",
                java.util.List.of(
                        new PhasePacket.PhasePanelCard("validate-gate-result", "phasePanel.validate.card.gate.label",
                                input.readiness().canAdvance() ? "phasePanel.validate.card.gate.passed" : "phasePanel.validate.card.gate.failed",
                                input.readiness().canAdvance() ? "passed" : "failed", input.correlationId(), Map.of("gateScore", input.readiness().completenessScore())),
                        new PhasePacket.PhasePanelCard("validate-missing-artifacts", "phasePanel.validate.card.artifacts.label",
                                input.blockers().isEmpty() ? "phasePanel.validate.card.artifacts.complete" : "phasePanel.validate.card.artifacts.missing",
                                input.blockers().isEmpty() ? "complete" : "missing", input.correlationId(), Map.of("missingCount", input.blockers().size())),
                        new PhasePacket.PhasePanelCard("validate-policy-outcome", "phasePanel.validate.card.policy.label",
                                input.governance().isEmpty() ? "phasePanel.validate.card.policy.noPolicy" : "phasePanel.validate.card.policy.evaluated",
                                input.governance().isEmpty() ? "none" : "evaluated", input.correlationId(), Map.of("policyCount", input.governance().size())),
                        new PhasePacket.PhasePanelCard("validate-confidence", "phasePanel.validate.card.confidence.label",
                                input.confidence() >= 0.8 ? "phasePanel.validate.card.confidence.high" : "phasePanel.validate.card.confidence.low",
                                input.confidence() >= 0.8 ? "high" : "low", input.correlationId(), Map.of("confidence", input.confidence())),
                        new PhasePacket.PhasePanelCard("validate-remediation", "phasePanel.validate.card.remediation.label",
                                input.blockers().isEmpty() ? "phasePanel.validate.card.remediation.none" : "phasePanel.validate.card.remediation.required",
                                input.blockers().isEmpty() ? "none" : "required", input.correlationId(), Map.of("remediationSteps", input.blockers().size())))
        );
    }
}
