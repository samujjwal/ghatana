package com.ghatana.digitalmarketing.domain.optimization;

/**
 * Types of experiment suggestions for A/B testing.
 *
 * @doc.type enum
 * @doc.purpose Defines the types of AI-generated experiment suggestions (P3-004)
 * @doc.layer product
 */
public enum ExperimentType {
    /** Test different creative assets (images, videos, ad copy) */
    CREATIVE_TEST,
    
    /** Test different audience targeting parameters */
    AUDIENCE_TEST,
    
    /** Test different bidding strategies */
    BIDDING_STRATEGY_TEST,
    
    /** Test different landing page designs */
    LANDING_PAGE_TEST,
    
    /** Test different ad formats and placements */
    AD_FORMAT_TEST,
    
    /** Test different campaign budgets */
    BUDGET_TEST,
    
    /** Test different call-to-action messages */
    CTA_TEST,
    
    /** Test different timing and scheduling */
    SCHEDULING_TEST,
    
    /** Test different channel combinations */
    CHANNEL_TEST
}
