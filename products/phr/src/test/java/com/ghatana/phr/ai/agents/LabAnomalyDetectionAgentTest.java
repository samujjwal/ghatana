package com.ghatana.phr.ai.agents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Tests anomaly detection behavior for lab agent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("LabAnomalyDetectionAgent")
class LabAnomalyDetectionAgentTest {

    @Test
    @DisplayName("flags out-of-range markers as anomalies")
    void flagsAnomalies() {
        LabAnomalyDetectionAgent agent = new LabAnomalyDetectionAgent(null);
        LabAnomalyDetectionAgent.LabAnomalyResult result = agent.detect("patient-1", Map.of(
            "hemoglobin", 8.5,
            "wbc", 13.2,
            "platelets", 210.0
        ));

        assertFalse(result.anomalies().isEmpty());
        assertTrue(result.riskScore() > 0.0);
    }

    @Test
    @DisplayName("does not require human review for low-risk normal panel")
    void lowRiskNormalPanel() {
        LabAnomalyDetectionAgent agent = new LabAnomalyDetectionAgent(null);
        LabAnomalyDetectionAgent.LabAnomalyResult result = agent.detect("patient-2", Map.of(
            "hemoglobin", 14.2,
            "wbc", 6.0,
            "platelets", 260.0
        ));

        assertFalse(result.requiresHumanReview());
    }
}
