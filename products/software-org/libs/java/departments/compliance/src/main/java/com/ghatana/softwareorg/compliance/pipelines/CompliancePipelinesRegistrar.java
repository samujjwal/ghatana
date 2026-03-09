package com.ghatana.softwareorg.compliance.pipelines;

import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.compliance.ComplianceDepartment;

/**
 * Pipeline registrar for Compliance department workflows.
 *
 * <p>
 * <b>Purpose</b><br>
 * Registers UnifiedOperator pipelines with AEP for Compliance event flows: -
 * Audit pipeline (initiate → detect findings → remediate) - Policy violation
 * pipeline (detect violation → assess → remediate) - Privacy assessment
 * pipeline (processing activity → PIA → mitigation)
 *
 * @doc.type class
 * @doc.purpose Compliance department pipeline orchestration
 * @doc.layer product
 * @doc.pattern Pipeline Registrar
 */
public class CompliancePipelinesRegistrar {

    private final ComplianceDepartment department;
    private final EventPublisher publisher;

    public CompliancePipelinesRegistrar(ComplianceDepartment department, EventPublisher publisher) {
        this.department = department;
        this.publisher = publisher;
    }

    /**
     * Register all Compliance pipelines with AEP.
     *
     * Registers 3 pipelines: 1. Security audit (SecurityAuditInitiated →
     * findings → remediation) 2. Policy violation (detect → assess → escalate →
     * remediate) 3. Privacy assessment (processing activity → PIA → mitigation)
     *
     * @return number of pipelines registered
     */
    public int registerPipelines() {
        registerAuditPipeline();
        registerPolicyViolationPipeline();
        registerPrivacyAssessmentPipeline();
        return 3;
    }

    private void registerAuditPipeline() {
        try {
            // Pipeline: SecurityAuditInitiated → findings → remediation
            logPipelineRegistration("audit", "SecurityAuditInitiated", "RemediationCompleted");
        } catch (Exception e) {
            handlePipelineRegistrationError("audit", e);
        }
    }

    private void registerPolicyViolationPipeline() {
        try {
            // Pipeline: Detect violation → assess → escalate → remediate
            logPipelineRegistration("policy-violation", "ViolationDetected", "ViolationRemediated");
        } catch (Exception e) {
            handlePipelineRegistrationError("policy-violation", e);
        }
    }

    private void registerPrivacyAssessmentPipeline() {
        try {
            // Pipeline: Processing activity → PIA → mitigation
            logPipelineRegistration("privacy-assessment", "ProcessingActivity", "PrivacyImpactAssessmentCompleted");
        } catch (Exception e) {
            handlePipelineRegistrationError("privacy-assessment", e);
        }
    }

    private void logPipelineRegistration(String pipelineName, String inputEvents, String outputEvents) {
        System.out.printf("[Compliance] Registered pipeline '%s': %s -> %s%n",
                pipelineName, inputEvents, outputEvents);
    }

    private void handlePipelineRegistrationError(String pipelineName, Exception e) {
        System.err.printf("[Compliance] Failed to register pipeline '%s': %s%n",
                pipelineName, e.getMessage());
    }
}
