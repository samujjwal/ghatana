package com.ghatana.digitalmarketing.application.capabilities;

/**
 * Canonical DMOS capability registry.
 *
 * <p>This enum is generated from the canonical route manifest.
 * Do not edit manually - regenerate from dmos-route-manifest.yaml.</p>
 *
 * @doc.type enum
 * @doc.purpose Canonical DMOS capability definitions
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmosCapability {
    /**
     * Programmatic advertising and advanced channels
     * Tier: enterprise
     * Lifecycle: boundary
     */
    DMOS_ADVANCED_CHANNELS("dmos.advanced_channels"),

    /**
     * Agency operations and multi-client management
     * Tier: enterprise
     * Lifecycle: boundary
     */
    DMOS_AGENCY("dmos.agency"),

    /**
     * AI-driven optimization and recommendations
     * Tier: enterprise
     * Lifecycle: boundary
     */
    DMOS_AI_OPTIMIZATION("dmos.ai_optimization"),

    /**
     * Budget recommendations and approval decisions
     * Tier: growth
     */
    DMOS_BUDGET("dmos.budget"),

    /**
     * Campaign management and orchestration
     * Tier: core
     */
    DMOS_CAMPAIGNS("dmos.campaigns"),

    /**
     * Multi-language campaign support
     * Tier: enterprise
     * Lifecycle: boundary
     */
    DMOS_LOCALIZATION("dmos.localization"),

    /**
     * Market research and competitive intelligence
     * Tier: enterprise
     * Lifecycle: boundary
     */
    DMOS_MARKET_RESEARCH("dmos.market_research"),

    /**
     * Analytics, attribution, and ROI/ROAS reporting
     * Tier: growth
     * Lifecycle: boundary
     */
    DMOS_REPORTING("dmos.reporting"),

    /**
     * Product-led growth funnel management
     * Tier: enterprise
     * Lifecycle: boundary
     */
    DMOS_SELF_MARKETING("dmos.self_marketing"),

    /**
     * Strategy generation, review, and approvals
     * Tier: growth
     */
    DMOS_STRATEGY("dmos.strategy"),

    /**
     * Null capability for routes that don't require specific capability checks.
     */
    NONE(null);

    private final String key;

    DmosCapability(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static boolean isDefined(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        for (DmosCapability cap : values()) {
            if (cap.key != null && cap.key.equals(key)) {
                return true;
            }
        }
        return false;
    }
}
