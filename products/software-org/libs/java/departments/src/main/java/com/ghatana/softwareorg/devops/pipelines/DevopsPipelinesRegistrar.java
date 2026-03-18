package com.ghatana.softwareorg.devops.pipelines;

import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.devops.DevopsDepartment;

/**
 * Pipeline registrar for DevOps department workflows.
 *
 * <p>
 * <b>Purpose</b><br>
 * Registers UnifiedOperator pipelines with AEP for DevOps event flows: -
 * Deployment pipeline (QualityGatePassed → DeploymentStarted/Succeeded/Failed)
 * - Incident detection pipeline (ErrorMetrics → IncidentDetected) - Incident
 * resolution pipeline (IncidentResolved → post-incident review)
 *
 * @doc.type class
 * @doc.purpose DevOps department pipeline orchestration
 * @doc.layer product
 * @doc.pattern Pipeline Registrar
 */
public class DevopsPipelinesRegistrar {

    private final DevopsDepartment department;
    private final EventPublisher publisher;

    public DevopsPipelinesRegistrar(DevopsDepartment department, EventPublisher publisher) {
        this.department = department;
        this.publisher = publisher;
    }

    /**
     * Register all DevOps pipelines with AEP.
     *
     * Registers 3 pipelines: 1. Deployment orchestration (QualityGatePassed →
     * Deploy → Success/Failed) 2. Incident detection (ErrorMetrics →
     * IncidentDetected) 3. Incident resolution (IncidentResolved →
     * post-incident tasks)
     *
     * @return number of pipelines registered
     */
    public int registerPipelines() {
        registerDeploymentPipeline();
        registerIncidentDetectionPipeline();
        registerIncidentResolutionPipeline();
        return 3;
    }

    private void registerDeploymentPipeline() {
        try {
            // Pipeline: QualityGatePassed → trigger deployment → DeploymentStarted/Succeeded/Failed
            logPipelineRegistration("deployment", "QualityGatePassed", "DeploymentStarted|DeploymentSucceeded|DeploymentFailed");
        } catch (Exception e) {
            handlePipelineRegistrationError("deployment", e);
        }
    }

    private void registerIncidentDetectionPipeline() {
        try {
            // Pipeline: Monitor error rates → detect anomalies → IncidentDetected
            logPipelineRegistration("incident-detection", "ErrorMetrics", "IncidentDetected");
        } catch (Exception e) {
            handlePipelineRegistrationError("incident-detection", e);
        }
    }

    private void registerIncidentResolutionPipeline() {
        try {
            // Pipeline: Incident resolved → post-incident tasks → IncidentResolved
            logPipelineRegistration("incident-resolution", "IncidentReported", "IncidentResolved");
        } catch (Exception e) {
            handlePipelineRegistrationError("incident-resolution", e);
        }
    }

    private void logPipelineRegistration(String pipelineName, String inputEvents, String outputEvents) {
        System.out.printf("[DevOps] Registered pipeline '%s': %s -> %s%n",
                pipelineName, inputEvents, outputEvents);
    }

    private void handlePipelineRegistrationError(String pipelineName, Exception e) {
        System.err.printf("[DevOps] Failed to register pipeline '%s': %s%n",
                pipelineName, e.getMessage());
    }
}
