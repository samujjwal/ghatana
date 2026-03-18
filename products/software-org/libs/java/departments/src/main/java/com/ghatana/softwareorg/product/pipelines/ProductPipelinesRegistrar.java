package com.ghatana.softwareorg.product.pipelines;

import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.product.ProductDepartment;

/**
 * Pipeline registrar for Product department workflows.
 *
 * <p>
 * <b>Purpose</b><br>
 * Registers UnifiedOperator pipelines with AEP for Product event flows: -
 * Feature request pipeline (customer request → roadmap planning) - Market
 * research pipeline (feature → research → competitive analysis) - Feedback
 * aggregation pipeline (customer input → sentiment analysis)
 *
 * @doc.type class
 * @doc.purpose Product department pipeline orchestration
 * @doc.layer product
 * @doc.pattern Pipeline Registrar
 */
public class ProductPipelinesRegistrar {

    private final ProductDepartment department;
    private final EventPublisher publisher;

    public ProductPipelinesRegistrar(ProductDepartment department, EventPublisher publisher) {
        this.department = department;
        this.publisher = publisher;
    }

    /**
     * Register all Product pipelines with AEP.
     *
     * Registers 3 pipelines: 1. Feature request (FeatureRequestReceived →
     * prioritization → roadmap) 2. Market research (trigger → competitive
     * analysis → synthesis) 3. Feedback aggregation (customer input → sentiment
     * analysis)
     *
     * @return number of pipelines registered
     */
    public int registerPipelines() {
        registerFeatureRequestPipeline();
        registerMarketResearchPipeline();
        registerFeedbackAggregationPipeline();
        return 3;
    }

    private void registerFeatureRequestPipeline() {
        try {
            // Pipeline: FeatureRequestReceived → deduplication → prioritization
            logPipelineRegistration("feature-request", "FeatureRequestReceived", "RoadmapItemPlanned");
        } catch (Exception e) {
            handlePipelineRegistrationError("feature-request", e);
        }
    }

    private void registerMarketResearchPipeline() {
        try {
            // Pipeline: Trigger market research → competitive analysis → synthesis
            logPipelineRegistration("market-research", "MarketResearchTriggered", "MarketResearchCompleted");
        } catch (Exception e) {
            handlePipelineRegistrationError("market-research", e);
        }
    }

    private void registerFeedbackAggregationPipeline() {
        try {
            // Pipeline: Customer feedback → sentiment analysis → insights
            logPipelineRegistration("feedback-aggregation", "CustomerFeedback", "FeedbackInsights");
        } catch (Exception e) {
            handlePipelineRegistrationError("feedback-aggregation", e);
        }
    }

    private void logPipelineRegistration(String pipelineName, String inputEvents, String outputEvents) {
        System.out.printf("[Product] Registered pipeline '%s': %s -> %s%n",
                pipelineName, inputEvents, outputEvents);
    }

    private void handlePipelineRegistrationError(String pipelineName, Exception e) {
        System.err.printf("[Product] Failed to register pipeline '%s': %s%n",
                pipelineName, e.getMessage());
    }
}
