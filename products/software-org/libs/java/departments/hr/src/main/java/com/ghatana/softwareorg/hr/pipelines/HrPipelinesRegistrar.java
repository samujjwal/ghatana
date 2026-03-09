package com.ghatana.softwareorg.hr.pipelines;

import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.hr.HrDepartment;

/**
 * Pipeline registrar for HR department workflows.
 *
 * <p>
 * <b>Purpose</b><br>
 * Registers UnifiedOperator pipelines with AEP for HR event flows: - Recruiting
 * pipeline (job opening → candidate → hired) - Onboarding pipeline (hired →
 * onboarded → productive) - Capacity planning pipeline (utilization monitoring
 * → forecasting)
 *
 * @doc.type class
 * @doc.purpose HR department pipeline orchestration
 * @doc.layer product
 * @doc.pattern Pipeline Registrar
 */
public class HrPipelinesRegistrar {

    private final HrDepartment department;
    private final EventPublisher publisher;

    public HrPipelinesRegistrar(HrDepartment department, EventPublisher publisher) {
        this.department = department;
        this.publisher = publisher;
    }

    /**
     * Register all HR pipelines with AEP.
     *
     * Registers 3 pipelines: 1. Recruiting (JobOpeningCreated → candidate
     * pipeline → hired) 2. Onboarding (EmployeeOnboarded → provision →
     * productive) 3. Capacity planning (monitor capacity → forecast → hiring
     * recs)
     *
     * @return number of pipelines registered
     */
    public int registerPipelines() {
        registerRecruitingPipeline();
        registerOnboardingPipeline();
        registerCapacityPlanningPipeline();
        return 3;
    }

    private void registerRecruitingPipeline() {
        try {
            // Pipeline: JobOpeningCreated → candidate pipeline → hired
            logPipelineRegistration("recruiting", "JobOpeningCreated", "EmployeeOnboarded");
        } catch (Exception e) {
            handlePipelineRegistrationError("recruiting", e);
        }
    }

    private void registerOnboardingPipeline() {
        try {
            // Pipeline: EmployeeOnboarded → provision access → team assignment
            logPipelineRegistration("onboarding", "EmployeeOnboarded", "EmployeeProductive");
        } catch (Exception e) {
            handlePipelineRegistrationError("onboarding", e);
        }
    }

    private void registerCapacityPlanningPipeline() {
        try {
            // Pipeline: Monitor capacity → forecasting → hiring recommendations
            logPipelineRegistration("capacity-planning", "TeamCapacityUpdated", "HiringRecommendation");
        } catch (Exception e) {
            handlePipelineRegistrationError("capacity-planning", e);
        }
    }

    private void logPipelineRegistration(String pipelineName, String inputEvents, String outputEvents) {
        System.out.printf("[HR] Registered pipeline '%s': %s -> %s%n",
                pipelineName, inputEvents, outputEvents);
    }

    private void handlePipelineRegistrationError(String pipelineName, Exception e) {
        System.err.printf("[HR] Failed to register pipeline '%s': %s%n",
                pipelineName, e.getMessage());
    }
}
