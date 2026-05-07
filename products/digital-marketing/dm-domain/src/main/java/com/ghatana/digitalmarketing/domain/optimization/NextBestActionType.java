package com.ghatana.digitalmarketing.domain.optimization;

/**
 * Types of next-best-action recommendations for campaign optimization.
 *
 * @doc.type enum
 * @doc.purpose Defines the types of AI-generated next-best-action recommendations (P3-004)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum NextBestActionType {
    /** Increase budget for underperforming campaign with high potential */
    INCREASE_BUDGET,
    
    /** Decrease budget for overspending campaign with diminishing returns */
    DECREASE_BUDGET,
    
    /** Pause campaign with poor performance */
    PAUSE_CAMPAIGN,
    
    /** Relaunch paused campaign with improved conditions */
    RELAUNCH_CAMPAIGN,
    
    /** Adjust targeting parameters to improve audience quality */
    ADJUST_TARGETING,
    
    /** Update creative assets to improve engagement */
    UPDATE_CREATIVE,
    
    /** Add new audience segment to expand reach */
    ADD_AUDIENCE_SEGMENT,
    
    /** Remove underperforming audience segment */
    REMOVE_AUDIENCE_SEGMENT,
    
    /** Change bid strategy to optimize for different objectives */
    CHANGE_BID_STRATEGY,
    
    /** Launch new campaign based on successful patterns */
    LAUNCH_NEW_CAMPAIGN,
    
    /** A/B test creatives to determine best performer */
    TEST_CREATIVES,
    
    /** Optimize ad scheduling for better timing */
    OPTIMIZE_SCHEDULING,
    
    /** Expand to additional channels or platforms */
    EXPAND_CHANNELS,
    
    /** Consolidate budget to best-performing channels */
    CONSOLIDATE_CHANNELS
}
