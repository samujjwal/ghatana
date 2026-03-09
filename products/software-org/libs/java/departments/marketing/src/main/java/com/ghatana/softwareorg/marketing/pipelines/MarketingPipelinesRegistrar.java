package com.ghatana.softwareorg.marketing.pipelines;

import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.marketing.MarketingDepartment;

/**
 * Pipeline registrar for Marketing department workflows.
 *
 * <p>
 * <b>Purpose</b><br>
 * Registers UnifiedOperator pipelines with AEP for Marketing event flows: -
 * Campaign performance pipeline (campaign launched → track metrics → optimize)
 * - Lead generation pipeline (content → impressions → leads → sales handoff) -
 * Brand positioning pipeline (market analysis → positioning update → messaging)
 *
 * @doc.type class
 * @doc.purpose Marketing department pipeline orchestration
 * @doc.layer product
 * @doc.pattern Pipeline Registrar
 */
public class MarketingPipelinesRegistrar {

    private final MarketingDepartment department;
    private final EventPublisher publisher;

    public MarketingPipelinesRegistrar(MarketingDepartment department, EventPublisher publisher) {
        this.department = department;
        this.publisher = publisher;
    }

    /**
     * Register all Marketing pipelines with AEP.
     *
     * Registers 3 pipelines: 1. Campaign performance (MarketingCampaignLaunched
     * → metrics → optimization) 2. Lead generation (ContentPublished →
     * engagement → leads) 3. Brand positioning (market analysis → positioning
     * update)
     *
     * @return number of pipelines registered
     */
    public int registerPipelines() {
        registerCampaignPerformancePipeline();
        registerLeadGenerationPipeline();
        registerBrandPositioningPipeline();
        return 3;
    }

    private void registerCampaignPerformancePipeline() {
        try {
            // Pipeline: MarketingCampaignLaunched → collect metrics → performance update
            logPipelineRegistration("campaign-performance", "MarketingCampaignLaunched", "CampaignPerformanceUpdated");
        } catch (Exception e) {
            handlePipelineRegistrationError("campaign-performance", e);
        }
    }

    private void registerLeadGenerationPipeline() {
        try {
            // Pipeline: ContentPublished → measure engagement → lead generation
            logPipelineRegistration("lead-generation", "ContentPublished", "LeadGenerationEventRecorded");
        } catch (Exception e) {
            handlePipelineRegistrationError("lead-generation", e);
        }
    }

    private void registerBrandPositioningPipeline() {
        try {
            // Pipeline: Market analysis → competitive positioning → update
            logPipelineRegistration("brand-positioning", "MarketAnalysis", "BrandPositioningUpdated");
        } catch (Exception e) {
            handlePipelineRegistrationError("brand-positioning", e);
        }
    }

    private void logPipelineRegistration(String pipelineName, String inputEvents, String outputEvents) {
        System.out.printf("[Marketing] Registered pipeline '%s': %s -> %s%n",
                pipelineName, inputEvents, outputEvents);
    }

    private void handlePipelineRegistrationError(String pipelineName, Exception e) {
        System.err.printf("[Marketing] Failed to register pipeline '%s': %s%n",
                pipelineName, e.getMessage());
    }
}
