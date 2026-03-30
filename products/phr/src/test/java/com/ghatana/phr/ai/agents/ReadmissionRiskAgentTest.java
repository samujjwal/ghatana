package com.ghatana.phr.ai.agents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Tests readmission risk scoring and human-review threshold behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ReadmissionRiskAgent")
class ReadmissionRiskAgentTest {

    @Test
    @DisplayName("marks high-risk profile for human review")
    void highRiskProfile() {
        ReadmissionRiskAgent agent = new ReadmissionRiskAgent(null);
        ReadmissionRiskAgent.ReadmissionRiskResult result = agent.score(
            "patient-1",
            new ReadmissionRiskAgent.ReadmissionFeatures(
                6,
                5,
                true,
                0.45,
                0.20
            )
        );

        assertTrue(result.requiresHumanReview());
        assertTrue(result.riskScore() >= 0.70);
    }

    @Test
    @DisplayName("keeps low-risk profile in LOW/MODERATE band")
    void lowRiskProfile() {
        ReadmissionRiskAgent agent = new ReadmissionRiskAgent(null);
        ReadmissionRiskAgent.ReadmissionRiskResult result = agent.score(
            "patient-2",
            new ReadmissionRiskAgent.ReadmissionFeatures(
                0,
                1,
                false,
                0.95,
                0.05
            )
        );

        assertFalse(result.requiresHumanReview());
        assertTrue(result.riskBand() == ReadmissionRiskAgent.RiskBand.LOW
            || result.riskBand() == ReadmissionRiskAgent.RiskBand.MODERATE);
        assertEquals("Standard post-discharge follow-up", result.recommendation());
    }
}
