package com.ghatana.softwareorg.product;

import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.domain.common.BaseSoftwareOrgDepartment;
import com.ghatana.platform.types.identity.Identifier;

/**
 * Product Department for software-org.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides extension hooks for product-specific operations. Core behavior
 * (event types, workflows, agents) is defined in YAML configuration.
 *
 * <p>
 * <b>Extension Points</b><br>
 * - Roadmap management
 * - Feature request tracking
 * - Market research
 * - Competitive analysis
 *
 * @doc.type class
 * @doc.purpose Product department extension hooks
 * @doc.layer product
 * @doc.pattern Extension Point
 */
public class ProductDepartment extends BaseSoftwareOrgDepartment {

    public static final String DEPARTMENT_TYPE = "PRODUCT";
    public static final String DEPARTMENT_NAME = "Product";

    public ProductDepartment(AbstractOrganization org, EventPublisher publisher) {
        super(org, publisher, DEPARTMENT_NAME, DEPARTMENT_TYPE);
    }

    /**
     * Hook: Record customer feature request.
     *
     * @param title       feature title
     * @param description feature description
     * @param category    feature category
     * @return request ID
     */
    public String recordFeatureRequest(String title, String description, String category) {
        String requestId = Identifier.random().raw();

        publishEvent("FeatureRequestReceived", newPayload()
                .withField("request_id", requestId)
                .withField("title", title)
                .withField("description", description)
                .withField("category", category)
                .withField("status", "RECEIVED")
                .withTimestamp()
                .build());

        return requestId;
    }

    /**
     * Hook: Plan feature on roadmap.
     *
     * @param featureRequestId feature to plan
     * @param targetRelease    target release quarter
     */
    public void planRoadmapItem(String featureRequestId, String targetRelease) {
        publishEvent("RoadmapItemPlanned", newPayload()
                .withField("feature_request_id", featureRequestId)
                .withField("target_release", targetRelease)
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Conduct market research on feature.
     *
     * @param featureId feature to research
     * @return research ID
     */
    public String conductMarketResearch(String featureId) {
        String researchId = Identifier.random().raw();

        publishEvent("MarketResearchCompleted", newPayload()
                .withField("research_id", researchId)
                .withField("feature_id", featureId)
                .withField("status", "COMPLETED")
                .withTimestamp()
                .build());

        return researchId;
    }

    /**
     * Hook: Aggregate customer feedback for feature.
     *
     * @param featureId feature to analyze
     * @return summary ID
     */
    public String aggregateCustomerFeedback(String featureId) {
        String summaryId = Identifier.random().raw();

        publishEvent("CustomerFeedbackAggregated", newPayload()
                .withField("summary_id", summaryId)
                .withField("feature_id", featureId)
                .withField("status", "AGGREGATED")
                .withTimestamp()
                .build());

        return summaryId;
    }

    /**
     * Hook: Update competitive analysis.
     *
     * @param featureId      feature to analyze
     * @param competitorName competitor to compare against
     */
    public void updateCompetitiveAnalysis(String featureId, String competitorName) {
        publishEvent("CompetitiveAnalysisUpdated", newPayload()
                .withField("feature_id", featureId)
                .withField("competitor_name", competitorName)
                .withTimestamp()
                .build());
    }
}
