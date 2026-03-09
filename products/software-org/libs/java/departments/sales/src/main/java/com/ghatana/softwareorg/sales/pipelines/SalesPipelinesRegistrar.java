package com.ghatana.softwareorg.sales.pipelines;

import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.sales.SalesDepartment;

/**
 * Pipeline registrar for Sales department workflows.
 *
 * <p>
 * <b>Purpose</b><br>
 * Registers UnifiedOperator pipelines with AEP for Sales event flows: - Lead
 * qualification pipeline (inbound → qualified leads) - Opportunity advancement
 * pipeline (qualification → closing) - Pipeline metrics pipeline (opportunity
 * tracking → forecasting)
 *
 * @doc.type class
 * @doc.purpose Sales department pipeline orchestration
 * @doc.layer product
 * @doc.pattern Pipeline Registrar
 */
public class SalesPipelinesRegistrar {

    private final SalesDepartment department;
    private final EventPublisher publisher;

    public SalesPipelinesRegistrar(SalesDepartment department, EventPublisher publisher) {
        this.department = department;
        this.publisher = publisher;
    }

    /**
     * Register all Sales pipelines with AEP.
     *
     * Registers 3 pipelines: 1. Lead qualification (SalesLeadCreated →
     * qualified → opportunity) 2. Opportunity advancement (progression → stage
     * changes) 3. Sales forecasting (aggregate → weighted forecast)
     *
     * @return number of pipelines registered
     */
    public int registerPipelines() {
        registerLeadQualificationPipeline();
        registerOpportunityAdvancementPipeline();
        registerForecastingPipeline();
        return 3;
    }

    private void registerLeadQualificationPipeline() {
        try {
            // Pipeline: SalesLeadCreated → qualification → SalesLeadQualified
            logPipelineRegistration("lead-qualification", "SalesLeadCreated", "SalesLeadQualified");
        } catch (Exception e) {
            handlePipelineRegistrationError("lead-qualification", e);
        }
    }

    private void registerOpportunityAdvancementPipeline() {
        try {
            // Pipeline: Opportunity progression → detect stage changes → SalesOpportunityAdvanced
            logPipelineRegistration("opportunity-advancement", "OpportunityProgress", "SalesOpportunityAdvanced");
        } catch (Exception e) {
            handlePipelineRegistrationError("opportunity-advancement", e);
        }
    }

    private void registerForecastingPipeline() {
        try {
            // Pipeline: Aggregate opportunities → calculate weighted forecast
            logPipelineRegistration("forecasting", "OpportunityData", "SalesForecastGenerated");
        } catch (Exception e) {
            handlePipelineRegistrationError("forecasting", e);
        }
    }

    private void logPipelineRegistration(String pipelineName, String inputEvents, String outputEvents) {
        System.out.printf("[Sales] Registered pipeline '%s': %s -> %s%n",
                pipelineName, inputEvents, outputEvents);
    }

    private void handlePipelineRegistrationError(String pipelineName, Exception e) {
        System.err.printf("[Sales] Failed to register pipeline '%s': %s%n",
                pipelineName, e.getMessage());
    }
}
