package com.ghatana.digitalmarketing.domain.strategy;

/**
 * Supported marketing execution channels in the MVP scope.
 *
 * @doc.type class
 * @doc.purpose Enumerates the MVP-supported marketing channels for strategy planning
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum StrategyChannel {
    /**
     * Google Search paid advertising campaigns.
     */
    GOOGLE_SEARCH,

    /**
     * Landing page creation and conversion optimisation.
     */
    LANDING_PAGE,

    /**
     * Email follow-up sequences for captured leads.
     */
    EMAIL_FOLLOW_UP
}
