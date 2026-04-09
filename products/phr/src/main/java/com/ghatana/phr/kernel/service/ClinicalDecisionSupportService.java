package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.observability.ExplainabilityFramework;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.ai.agents.LabAnomalyDetectionAgent;
import com.ghatana.phr.ai.agents.MedicationInteractionAgent;
import com.ghatana.phr.ai.agents.ReadmissionRiskAgent;
import com.ghatana.phr.observability.PHRExplainabilityFrameworkImpl;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose PHR clinical AI orchestration service for anomaly, interaction, and readmission review
 * @doc.layer product
 * @doc.pattern Service, Facade
 */
public final class ClinicalDecisionSupportService implements KernelLifecycleAware {

    private final LabAnomalyDetectionAgent labAnomalyDetectionAgent;
    private final MedicationInteractionAgent medicationInteractionAgent;
    private final ReadmissionRiskAgent readmissionRiskAgent;
    private volatile boolean running;

    public ClinicalDecisionSupportService() {
        this(new PHRExplainabilityFrameworkImpl());
    }

    public ClinicalDecisionSupportService(ExplainabilityFramework explainabilityFramework) {
        this.labAnomalyDetectionAgent = new LabAnomalyDetectionAgent(explainabilityFramework);
        this.medicationInteractionAgent = new MedicationInteractionAgent(explainabilityFramework);
        this.readmissionRiskAgent = new ReadmissionRiskAgent(explainabilityFramework);
    }

    @Override
    public Promise<Void> start() {
        running = true;
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        running = false;
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return running;
    }

    @Override
    public String getName() {
        return "clinical-decision-support";
    }

    public ClinicalDecisionSummary analyzePatient(
            String patientId,
            Map<String, Double> labValues,
            List<String> activeMedications,
            ReadmissionRiskAgent.ReadmissionFeatures readmissionFeatures) {
        ensureRunning();
        Objects.requireNonNull(patientId, "patientId cannot be null");
        Objects.requireNonNull(labValues, "labValues cannot be null");
        Objects.requireNonNull(activeMedications, "activeMedications cannot be null");
        Objects.requireNonNull(readmissionFeatures, "readmissionFeatures cannot be null");

        LabAnomalyDetectionAgent.LabAnomalyResult labAssessment =
            labAnomalyDetectionAgent.detect(patientId, labValues);
        MedicationInteractionAgent.InteractionAssessment medicationAssessment =
            medicationInteractionAgent.assess(patientId, activeMedications);
        ReadmissionRiskAgent.ReadmissionRiskResult readmissionAssessment =
            readmissionRiskAgent.score(patientId, readmissionFeatures);

        boolean requiresHumanReview = labAssessment.requiresHumanReview()
            || medicationAssessment.requiresHumanReview()
            || readmissionAssessment.requiresHumanReview();

        ReviewPriority reviewPriority = determinePriority(labAssessment, medicationAssessment, readmissionAssessment);
        List<String> recommendations = collectRecommendations(labAssessment, medicationAssessment, readmissionAssessment);

        return new ClinicalDecisionSummary(
            patientId,
            labAssessment,
            medicationAssessment,
            readmissionAssessment,
            requiresHumanReview,
            reviewPriority,
            recommendations
        );
    }

    private static ReviewPriority determinePriority(
            LabAnomalyDetectionAgent.LabAnomalyResult labAssessment,
            MedicationInteractionAgent.InteractionAssessment medicationAssessment,
            ReadmissionRiskAgent.ReadmissionRiskResult readmissionAssessment) {
        boolean criticalMedication = medicationAssessment.highestSeverity() == MedicationInteractionAgent.Severity.CRITICAL;
        boolean criticalReadmission = readmissionAssessment.riskBand() == ReadmissionRiskAgent.RiskBand.CRITICAL;
        boolean criticalLab = labAssessment.anomalies().stream()
            .anyMatch(anomaly -> anomaly.severity() == LabAnomalyDetectionAgent.Severity.CRITICAL);

        if (criticalMedication || criticalReadmission || criticalLab) {
            return ReviewPriority.CRITICAL;
        }
        if (labAssessment.requiresHumanReview()
                || medicationAssessment.requiresHumanReview()
                || readmissionAssessment.requiresHumanReview()) {
            return ReviewPriority.HIGH;
        }
        if (!labAssessment.anomalies().isEmpty() || !medicationAssessment.matches().isEmpty()) {
            return ReviewPriority.MODERATE;
        }
        return ReviewPriority.LOW;
    }

    private static List<String> collectRecommendations(
            LabAnomalyDetectionAgent.LabAnomalyResult labAssessment,
            MedicationInteractionAgent.InteractionAssessment medicationAssessment,
            ReadmissionRiskAgent.ReadmissionRiskResult readmissionAssessment) {
        LinkedHashSet<String> uniqueRecommendations = new LinkedHashSet<>();
        if (!labAssessment.anomalies().isEmpty() || labAssessment.requiresHumanReview()) {
            uniqueRecommendations.add(labAssessment.recommendation());
        }
        if (!medicationAssessment.matches().isEmpty() || medicationAssessment.requiresHumanReview()) {
            uniqueRecommendations.add(medicationAssessment.recommendation());
        }
        uniqueRecommendations.add(readmissionAssessment.recommendation());
        return new ArrayList<>(uniqueRecommendations);
    }

    private void ensureRunning() {
        if (!running) {
            throw new IllegalStateException("ClinicalDecisionSupportService not running");
        }
    }

    public enum ReviewPriority {
        LOW,
        MODERATE,
        HIGH,
        CRITICAL
    }

    public record ClinicalDecisionSummary(
        String patientId,
        LabAnomalyDetectionAgent.LabAnomalyResult labAssessment,
        MedicationInteractionAgent.InteractionAssessment medicationAssessment,
        ReadmissionRiskAgent.ReadmissionRiskResult readmissionAssessment,
        boolean requiresHumanReview,
        ReviewPriority reviewPriority,
        List<String> recommendations
    ) {
    }
}
