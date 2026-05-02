/*
 * Copyright (c) 2026 Ghatana Inc.
 * Example file — fictional Nexus logistics product.
 * This file is NOT production code. It demonstrates how a product supplies
 * compliance rule packs to the generic platform CompliancePlugin without
 * modifying any platform kernel class.
 */
package com.ghatana.nexus.kernel.compliance;

import java.util.List;
import java.util.Map;

/**
 * Example: Nexus logistics compliance rule pack.
 *
 * <p>Declares logistics-domain regulatory rules (IATA-DGR, IMO-IMDG, customs-harmonized)
 * as product-owned data. The generic {@code CompliancePlugin} loads and evaluates these
 * rules; no platform code is aware of IATA, IMO, or customs terminology.</p>
 *
 * <p>To apply the same pattern in a real product:</p>
 * <ol>
 *   <li>Create a class implementing the platform {@code ComplianceRulePack} SPI.</li>
 *   <li>Declare rule sets with clear IDs and rule counts.</li>
 *   <li>Register the pack in your product's {@code domain-pack-manifest.yaml}.</li>
 *   <li>Wire it into the {@code CompliancePlugin} via your product's plugin-bindings manifest.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Example product compliance rule pack for logistics domain
 * @doc.layer product
 * @doc.pattern Pack
 */
public final class NexusComplianceRulePack {

    private static final String PACK_ID = "nexus-compliance";
    private static final String PACK_VERSION = "1.0.0";

    /**
     * Returns the pack identifier.
     *
     * @return canonical pack ID
     */
    public String getPackId() {
        return PACK_ID;
    }

    /**
     * Returns the pack version.
     *
     * @return semantic version string
     */
    public String getPackVersion() {
        return PACK_VERSION;
    }

    /**
     * Returns all rule sets provided by this pack.
     *
     * @return immutable list of rule set descriptors
     */
    public List<RuleSetDescriptor> getRuleSets() {
        return List.of(
                new RuleSetDescriptor(
                        "NEXUS_DGR_CLASSIFICATION",
                        "dangerousGoodsClassificationRules",
                        "IATA-DGR dangerous goods classification and labelling requirements",
                        5,
                        Map.of("regulation", "IATA-DGR-2026", "scope", "air-freight")
                ),
                new RuleSetDescriptor(
                        "NEXUS_MARITIME_COMPLIANCE",
                        "maritimeDangerousGoodsRules",
                        "IMO-IMDG maritime dangerous goods transport compliance rules",
                        4,
                        Map.of("regulation", "IMO-IMDG-2024", "scope", "ocean-freight")
                ),
                new RuleSetDescriptor(
                        "NEXUS_CUSTOMS_DECLARATION",
                        "customsDeclarationRules",
                        "Harmonized customs tariff classification and export control rules",
                        6,
                        Map.of("regulation", "HS-Convention-2022", "scope", "cross-border")
                ),
                new RuleSetDescriptor(
                        "NEXUS_AUDIT_RECORD_RETENTION",
                        "auditRecordRetentionRules",
                        "Shipment audit record retention requirements (7 years for regulated goods)",
                        3,
                        Map.of("regulation", "nexus-retention-policy-1.0", "scope", "all")
                )
        );
    }

    /**
     * Descriptor for a single rule set within this compliance pack.
     *
     * @param ruleSetId     canonical rule set ID
     * @param factoryMethod name of the method that produces the rules (for tooling)
     * @param description   human-readable description of what this rule set covers
     * @param ruleCount     number of rules in the set
     * @param metadata      additional key-value metadata (regulation name, scope, etc.)
     */
    public record RuleSetDescriptor(
            String ruleSetId,
            String factoryMethod,
            String description,
            int ruleCount,
            Map<String, String> metadata
    ) {}
}
