package com.ghatana.phr.ai.agents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Tests medication interaction screening behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MedicationInteractionAgent")
class MedicationInteractionAgentTest {

    @Test
    @DisplayName("detects known high-risk interaction")
    void detectsHighRiskInteraction() {
        MedicationInteractionAgent agent = new MedicationInteractionAgent(null);
        MedicationInteractionAgent.InteractionAssessment result = agent.assess(
            "patient-1",
            List.of("Warfarin", "Aspirin", "Metformin")
        );

        assertTrue(result.matches().size() >= 1);
        assertEquals(MedicationInteractionAgent.Severity.HIGH, result.highestSeverity());
        assertTrue(result.requiresHumanReview());
    }

    @Test
    @DisplayName("returns low severity when no known interaction")
    void noKnownInteraction() {
        MedicationInteractionAgent agent = new MedicationInteractionAgent(null);
        MedicationInteractionAgent.InteractionAssessment result = agent.assess(
            "patient-2",
            List.of("Vitamin C", "Paracetamol")
        );

        assertEquals(MedicationInteractionAgent.Severity.LOW, result.highestSeverity());
    }
}
