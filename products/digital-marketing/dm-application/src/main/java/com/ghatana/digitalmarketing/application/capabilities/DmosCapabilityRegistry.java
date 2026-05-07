package com.ghatana.digitalmarketing.application.capabilities;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * P0-004: Canonical backend capability registry for DMOS.
 * 
 * <p>Defines all capability keys and their metadata. This is the single source
 * of truth for backend capability definitions. The frontend CapabilityKeys must
 * match these keys exactly.
 *
 * @doc.type class
 * @doc.purpose Canonical backend capability registry for DMOS
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class DmosCapabilityRegistry {

    // Core features
    public static final String CAMPAIGNS = "dmos.campaigns";
    public static final String STRATEGY = "dmos.strategy";
    public static final String BUDGET = "dmos.budget";
    public static final String APPROVALS = "dmos.approvals";
    public static final String AI_ACTIONS = "dmos.ai_actions";

    // Content generation capabilities
    public static final String AD_COPY_GENERATION = "dmos.ad_copy_generation";
    public static final String LANDING_PAGE_GENERATION = "dmos.landing_page_generation";
    public static final String EMAIL_DRAFT_GENERATION = "dmos.email_draft_generation";
    public static final String SOW_GENERATION = "dmos.sow_generation";

    // P0-004: New route manifest capability keys
    public static final String REPORTING = "dmos.reporting";
    public static final String SELF_MARKETING = "dmos.self_marketing";
    public static final String MARKET_RESEARCH = "dmos.market_research";
    public static final String ADVANCED_CHANNELS = "dmos.advanced_channels";
    public static final String LOCALIZATION = "dmos.localization";
    public static final String AGENCY = "dmos.agency";

    private static final Map<String, CapabilityDefinition> REGISTRY = Map.ofEntries(
        Map.entry(CAMPAIGNS, new CapabilityDefinition(
            CAMPAIGNS,
            "Campaign management and execution",
            "Allows creating, managing, and launching marketing campaigns",
            null,
            null
        )),
        Map.entry(STRATEGY, new CapabilityDefinition(
            STRATEGY,
            "Marketing strategy generation",
            "AI-powered marketing strategy and channel planning",
            null,
            null
        )),
        Map.entry(BUDGET, new CapabilityDefinition(
            BUDGET,
            "Budget management and recommendations",
            "Budget allocation, optimization, and performance tracking",
            null,
            null
        )),
        Map.entry(APPROVALS, new CapabilityDefinition(
            APPROVALS,
            "Approval workflows",
            "Multi-stage approval process for campaigns and content",
            null,
            null
        )),
        Map.entry(AI_ACTIONS, new CapabilityDefinition(
            AI_ACTIONS,
            "AI action logging and transparency",
            "Audit trail of AI-generated actions and decisions",
            null,
            null
        )),
        Map.entry(AD_COPY_GENERATION, new CapabilityDefinition(
            AD_COPY_GENERATION,
            "Ad copy generation",
            "AI-generated ad copy for various platforms",
            null,
            null
        )),
        Map.entry(LANDING_PAGE_GENERATION, new CapabilityDefinition(
            LANDING_PAGE_GENERATION,
            "Landing page generation",
            "AI-generated landing pages with conversion optimization",
            null,
            null
        )),
        Map.entry(EMAIL_DRAFT_GENERATION, new CapabilityDefinition(
            EMAIL_DRAFT_GENERATION,
            "Email draft generation",
            "AI-generated email marketing campaigns",
            null,
            null
        )),
        Map.entry(SOW_GENERATION, new CapabilityDefinition(
            SOW_GENERATION,
            "Statement of work generation",
            "AI-generated SOW documents for agency work",
            null,
            null
        )),
        Map.entry(REPORTING, new CapabilityDefinition(
            REPORTING,
            "Advanced reporting and analytics",
            "Funnel analytics, attribution, ROI/ROAS reporting",
            null,
            null
        )),
        Map.entry(SELF_MARKETING, new CapabilityDefinition(
            SELF_MARKETING,
            "Self-marketing funnel",
            "Self-service marketing funnel optimization",
            null,
            null
        )),
        Map.entry(MARKET_RESEARCH, new CapabilityDefinition(
            MARKET_RESEARCH,
            "Market research",
            "Market analysis, competitor research, and persona insights",
            null,
            null
        )),
        Map.entry(ADVANCED_CHANNELS, new CapabilityDefinition(
            ADVANCED_CHANNELS,
            "Advanced channel management",
            "Multi-channel campaign management across platforms",
            null,
            null
        )),
        Map.entry(LOCALIZATION, new CapabilityDefinition(
            LOCALIZATION,
            "Localization and internationalization",
            "Multi-language and multi-region campaign support",
            null,
            null
        )),
        Map.entry(AGENCY, new CapabilityDefinition(
            AGENCY,
            "Agency operations",
            "Agency workspace management and collaboration",
            null,
            null
        ))
    );

    /**
     * Returns all defined capabilities.
     */
    public static List<CapabilityDefinition> allCapabilities() {
        return List.copyOf(REGISTRY.values());
    }

    /**
     * Returns a capability definition by key.
     */
    public static CapabilityDefinition getCapability(String key) {
        return REGISTRY.get(key);
    }

    /**
     * Checks if a capability key is defined in the registry.
     */
    public static boolean isDefined(String key) {
        return REGISTRY.containsKey(key);
    }

    /**
     * Capability definition metadata.
     */
    public record CapabilityDefinition(
        String key,
        String displayName,
        String description,
        String requiresRole,
        String tier
    ) {
        public CapabilityDefinition {
            Objects.requireNonNull(key, "key required");
            Objects.requireNonNull(displayName, "displayName required");
            Objects.requireNonNull(description, "description required");
        }
    }
}
