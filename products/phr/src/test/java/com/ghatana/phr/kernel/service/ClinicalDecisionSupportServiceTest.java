package com.ghatana.phr.kernel.service;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Tests orchestration of PHR clinical AI assessments and review thresholds
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ClinicalDecisionSupportService")
class ClinicalDecisionSupportServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("requires service startup before use")
    void requiresStartupBeforeUse() {
        ClinicalDecisionSupportService service = new ClinicalDecisionSupportService();

        assertThrows(IllegalStateException.class, () -> service.analyzePatient(
            "patient-1",
            Map.of(),
            List.of(),
            new com.ghatana.phr.ai.agents.ReadmissionRiskAgent.ReadmissionFeatures(0, 0, false, 1.0, 0.0)
        ));
    }

    @Test
    @DisplayName("escalates combined high-risk profile to human review")
    void escalatesCombinedHighRiskProfile() {
        ClinicalDecisionSupportService service = new ClinicalDecisionSupportService();
        runPromise(service::start);

        ClinicalDecisionSupportService.ClinicalDecisionSummary summary = service.analyzePatient(
            "patient-1",
            Map.of(
                "hemoglobin", 8.5,
                "wbc", 13.2,
                "platelets", 210.0
            ),
            List.of("Warfarin", "Aspirin", "Metformin"),
            new com.ghatana.phr.ai.agents.ReadmissionRiskAgent.ReadmissionFeatures(6, 5, true, 0.45, 0.20)
        );

        assertTrue(summary.requiresHumanReview());
        assertEquals(ClinicalDecisionSupportService.ReviewPriority.CRITICAL, summary.reviewPriority());
        assertTrue(summary.recommendations().size() >= 3);
        assertTrue(summary.medicationAssessment().requiresHumanReview());
        assertTrue(summary.readmissionAssessment().requiresHumanReview());
    }

    @Test
    @DisplayName("keeps low-risk profile in automated monitoring path")
    void keepsLowRiskProfileAutomated() {
        ClinicalDecisionSupportService service = new ClinicalDecisionSupportService();
        runPromise(service::start);

        ClinicalDecisionSupportService.ClinicalDecisionSummary summary = service.analyzePatient(
            "patient-2",
            Map.of(
                "hemoglobin", 14.2,
                "wbc", 6.0,
                "platelets", 260.0
            ),
            List.of("Vitamin C", "Paracetamol"),
            new com.ghatana.phr.ai.agents.ReadmissionRiskAgent.ReadmissionFeatures(0, 1, false, 0.95, 0.05)
        );

        assertFalse(summary.requiresHumanReview());
        assertEquals(ClinicalDecisionSupportService.ReviewPriority.LOW, summary.reviewPriority());
        assertEquals(List.of("Standard post-discharge follow-up"), summary.recommendations());
    }
}
