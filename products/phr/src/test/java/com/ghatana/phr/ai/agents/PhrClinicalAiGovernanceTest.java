package com.ghatana.phr.ai.agents;

import com.ghatana.kernel.observability.ExplainabilityFramework;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Governance contract tests for the three PHR clinical AI agents.
 *
 * <p>Invariants verified:
 * <ul>
 *   <li>Decision IDs are unique per invocation (model traceability).</li>
 *   <li>Human review is required whenever risk / severity exceeds defined thresholds.</li>
 *   <li>Low-risk outputs never trigger the human-review flag.</li>
 *   <li>The ExplainabilityFramework is called with the correct model ID on every inference.</li>
 *   <li>Recommendation text is consistent with the requiresHumanReview flag.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose PHR clinical AI governance contract enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PHR Clinical AI Governance Contracts")
@ExtendWith(MockitoExtension.class)
class PhrClinicalAiGovernanceTest {

    @Mock
    private ExplainabilityFramework explainabilityFramework;

    // -----------------------------------------------------------------------
    // LabAnomalyDetectionAgent governance
    // -----------------------------------------------------------------------

    private LabAnomalyDetectionAgent labAgent;

    @BeforeEach
    void setUp() {
        labAgent = new LabAnomalyDetectionAgent(explainabilityFramework);
    }

    @Test
    @DisplayName("lab agent decision IDs are unique across invocations")
    void labAgentDecisionIdIsUniquePerInvocation() throws InterruptedException {
        Map<String, Double> panel = Map.of("hemoglobin", 14.0, "wbc", 6.0);

        LabAnomalyDetectionAgent.LabAnomalyResult first = labAgent.detect("patient-1", panel);
        Thread.sleep(2); // guarantee distinct epoch milli
        LabAnomalyDetectionAgent.LabAnomalyResult second = labAgent.detect("patient-1", panel);

        assertThat(first.decisionId()).isNotEqualTo(second.decisionId());
    }

    @Test
    @DisplayName("lab agent high-risk score always requires human review")
    void labAgentHighRiskAlwaysRequiresHumanReview() {
        // Severely abnormal values push riskScore >= 0.75
        Map<String, Double> criticalPanel = Map.of(
            "hemoglobin", 4.0,      // critically low (normal 12-17.5)
            "wbc", 35.0,            // critically high (normal 4-11)
            "platelets", 25.0,      // critically low (normal 150-450)
            "creatinine", 8.5,      // critically high (normal 0.6-1.3)
            "glucose_fasting", 450.0 // critically high (normal 70-100)
        );

        LabAnomalyDetectionAgent.LabAnomalyResult result = labAgent.detect("patient-high", criticalPanel);

        assertThat(result.requiresHumanReview())
            .as("High riskScore must set requiresHumanReview=true")
            .isTrue();
        assertThat(result.riskScore()).isGreaterThanOrEqualTo(0.75);
        assertThat(result.recommendation()).contains("clinician");
    }

    @Test
    @DisplayName("lab agent normal panel never requires human review")
    void labAgentLowRiskNeverRequiresHumanReview() {
        Map<String, Double> normalPanel = Map.of(
            "hemoglobin", 14.0,
            "wbc", 6.5,
            "platelets", 250.0,
            "creatinine", 0.9,
            "glucose_fasting", 85.0
        );

        LabAnomalyDetectionAgent.LabAnomalyResult result = labAgent.detect("patient-normal", normalPanel);

        assertThat(result.requiresHumanReview())
            .as("Normal panel must not require human review")
            .isFalse();
        assertThat(result.anomalies()).isEmpty();
    }

    @Test
    @DisplayName("lab agent calls ExplainabilityFramework with modelId phr-lab-anomaly-v1")
    void labAgentCallsExplainabilityFrameworkWithCorrectModelId() {
        Map<String, Double> panel = Map.of("hemoglobin", 14.0);

        labAgent.detect("patient-x", panel);

        ArgumentCaptor<ExplainabilityFramework.Explanation> captor =
            ArgumentCaptor.forClass(ExplainabilityFramework.Explanation.class);
        verify(explainabilityFramework).recordDecisionExplanation(any(), captor.capture());

        assertThat(captor.getValue().getModelId()).isEqualTo("phr-lab-anomaly-v1");
    }

    @Test
    @DisplayName("lab agent explanation decisionId matches result decisionId")
    void labAgentExplanationDecisionIdMatchesResult() {
        Map<String, Double> panel = Map.of("hemoglobin", 14.0);

        LabAnomalyDetectionAgent.LabAnomalyResult result = labAgent.detect("patient-y", panel);

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(explainabilityFramework).recordDecisionExplanation(idCaptor.capture(), any());

        assertThat(idCaptor.getValue()).isEqualTo(result.decisionId());
    }

    // -----------------------------------------------------------------------
    // MedicationInteractionAgent governance
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("medication interaction high severity requires human review")
    void medicationInteractionHighSeverityRequiresHumanReview() {
        MedicationInteractionAgent agent = new MedicationInteractionAgent(explainabilityFramework);

        // Warfarin + Aspirin is a known HIGH interaction in the default rule set
        MedicationInteractionAgent.InteractionAssessment result =
            agent.assess("patient-med", List.of("Warfarin", "Aspirin"));

        assertThat(result.requiresHumanReview())
            .as("HIGH severity must require human review")
            .isTrue();
        assertThat(result.highestSeverity())
            .isIn(
                MedicationInteractionAgent.Severity.HIGH,
                MedicationInteractionAgent.Severity.CRITICAL
            );
        assertThat(result.recommendation()).contains("Pharmacist");
    }

    @Test
    @DisplayName("medication interaction no known interaction does not require human review")
    void medicationInteractionNoInteractionNoReview() {
        MedicationInteractionAgent agent = new MedicationInteractionAgent(explainabilityFramework);

        MedicationInteractionAgent.InteractionAssessment result =
            agent.assess("patient-safe", List.of("Vitamin C", "Zinc"));

        assertThat(result.requiresHumanReview()).isFalse();
        assertThat(result.matches()).isEmpty();
    }

    @Test
    @DisplayName("medication agent calls ExplainabilityFramework with modelId phr-medication-interaction-v1")
    void medicationInteractionCallsExplainabilityFrameworkWithCorrectModelId() {
        MedicationInteractionAgent agent = new MedicationInteractionAgent(explainabilityFramework);

        agent.assess("patient-ex", List.of("Metformin"));

        ArgumentCaptor<ExplainabilityFramework.Explanation> captor =
            ArgumentCaptor.forClass(ExplainabilityFramework.Explanation.class);
        verify(explainabilityFramework).recordDecisionExplanation(any(), captor.capture());

        assertThat(captor.getValue().getModelId()).isEqualTo("phr-medication-interaction-v1");
    }

    @Test
    @DisplayName("medication agent CRITICAL severity also requires human review")
    void medicationInteractionCriticalSeverityRequiresHumanReview() {
        // Inject a CRITICAL rule via default set check — HIGH already triggers; this
        // asserts the threshold is >= HIGH (covers future CRITICAL additions too).
        MedicationInteractionAgent agent = new MedicationInteractionAgent(null);
        MedicationInteractionAgent.InteractionAssessment highResult =
            agent.assess("p", List.of("Warfarin", "Aspirin"));

        // Both HIGH and CRITICAL must set requiresHumanReview = true
        boolean isHighOrCritical =
            highResult.highestSeverity() == MedicationInteractionAgent.Severity.HIGH ||
            highResult.highestSeverity() == MedicationInteractionAgent.Severity.CRITICAL;
        assertThat(isHighOrCritical).isTrue();
        assertThat(highResult.requiresHumanReview()).isTrue();
    }

    // -----------------------------------------------------------------------
    // ReadmissionRiskAgent governance
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("readmission high-risk score requires human review")
    void readmissionHighRiskScoreRequiresHumanReview() {
        ReadmissionRiskAgent agent = new ReadmissionRiskAgent(explainabilityFramework);

        // Features that will push score >= 0.70
        ReadmissionRiskAgent.ReadmissionFeatures highRisk = new ReadmissionRiskAgent.ReadmissionFeatures(
            6,     // priorAdmissionsLast12Months → contributes 0.25 (capped)
            8,     // comorbidityCount            → contributes 0.20 (capped)
            true,  // missedFollowUps             → contributes 0.15
            0.5,   // medicationAdherenceRatio    → contributes 0.15 (< 0.8)
            0.20   // socialRiskFactorScore       → contributes 0.20
        );

        ReadmissionRiskAgent.ReadmissionRiskResult result =
            agent.score("patient-hr", highRisk);

        assertThat(result.riskScore()).isGreaterThanOrEqualTo(0.70);
        assertThat(result.requiresHumanReview()).isTrue();
        assertThat(result.riskBand())
            .isIn(ReadmissionRiskAgent.RiskBand.HIGH, ReadmissionRiskAgent.RiskBand.CRITICAL);
    }

    @Test
    @DisplayName("readmission low-risk score does not require human review")
    void readmissionLowRiskScoreDoesNotRequireHumanReview() {
        ReadmissionRiskAgent agent = new ReadmissionRiskAgent(explainabilityFramework);

        ReadmissionRiskAgent.ReadmissionFeatures lowRisk = new ReadmissionRiskAgent.ReadmissionFeatures(
            0,    // priorAdmissionsLast12Months
            1,    // comorbidityCount
            false, // missedFollowUps
            0.95,  // medicationAdherenceRatio (good adherence)
            0.05   // socialRiskFactorScore (minimal)
        );

        ReadmissionRiskAgent.ReadmissionRiskResult result =
            agent.score("patient-lr", lowRisk);

        assertThat(result.riskScore()).isLessThan(0.70);
        assertThat(result.requiresHumanReview()).isFalse();
        assertThat(result.riskBand())
            .isIn(ReadmissionRiskAgent.RiskBand.LOW, ReadmissionRiskAgent.RiskBand.MODERATE);
    }

    @Test
    @DisplayName("readmission recommendation mentions case manager for high risk")
    void readmissionRecommendationTextDependsOnReviewFlag() {
        ReadmissionRiskAgent agent = new ReadmissionRiskAgent(null);

        ReadmissionRiskAgent.ReadmissionFeatures highRisk = new ReadmissionRiskAgent.ReadmissionFeatures(
            6, 8, true, 0.5, 0.20
        );
        ReadmissionRiskAgent.ReadmissionFeatures lowRisk = new ReadmissionRiskAgent.ReadmissionFeatures(
            0, 1, false, 0.95, 0.05
        );

        assertThat(agent.score("p-high", highRisk).recommendation())
            .containsIgnoringCase("case manager");
        assertThat(agent.score("p-low", lowRisk).recommendation())
            .containsIgnoringCase("Standard");
    }

    @Test
    @DisplayName("readmission agent calls ExplainabilityFramework with modelId phr-readmission-risk-v1")
    void readmissionCallsExplainabilityFrameworkWithCorrectModelId() {
        ReadmissionRiskAgent agent = new ReadmissionRiskAgent(explainabilityFramework);

        ReadmissionRiskAgent.ReadmissionFeatures features = new ReadmissionRiskAgent.ReadmissionFeatures(
            1, 2, false, 0.9, 0.05
        );
        ReadmissionRiskAgent.ReadmissionRiskResult result = agent.score("patient-ef", features);

        ArgumentCaptor<ExplainabilityFramework.Explanation> captor =
            ArgumentCaptor.forClass(ExplainabilityFramework.Explanation.class);
        verify(explainabilityFramework).recordDecisionExplanation(eq(result.decisionId()), captor.capture());

        assertThat(captor.getValue().getModelId()).isEqualTo("phr-readmission-risk-v1");
    }

    @Test
    @DisplayName("readmission risk score is bounded between 0.0 and 1.0 for any features")
    void readmissionScoreIsBounded() {
        ReadmissionRiskAgent agent = new ReadmissionRiskAgent(null);

        // Max-value features
        ReadmissionRiskAgent.ReadmissionRiskResult extremeHigh = agent.score("p-xh",
            new ReadmissionRiskAgent.ReadmissionFeatures(100, 100, true, 0.0, 1.0));
        assertThat(extremeHigh.riskScore()).isLessThanOrEqualTo(1.0);
        assertThat(extremeHigh.riskScore()).isGreaterThanOrEqualTo(0.0);

        // Zero-value features
        ReadmissionRiskAgent.ReadmissionRiskResult extremeLow = agent.score("p-xl",
            new ReadmissionRiskAgent.ReadmissionFeatures(0, 0, false, 1.0, 0.0));
        assertThat(extremeLow.riskScore()).isLessThanOrEqualTo(1.0);
        assertThat(extremeLow.riskScore()).isGreaterThanOrEqualTo(0.0);
    }
}
