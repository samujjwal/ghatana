/*
 * Copyright (c) 2026 Ghatana Inc.
 * Example file — fictional Nexus logistics product.
 * This file is NOT production code. It demonstrates how a product implements
 * BoundaryPolicyStore without modifying the platform kernel.
 */
package com.ghatana.nexus.kernel.policy;

import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyStore;

import java.util.List;
import java.util.Map;

/**
 * Example: Nexus logistics boundary policy store.
 *
 * <p>Demonstrates how a fictional logistics product declares its access control rules
 * via {@link BoundaryPolicyStore} without modifying {@code DefaultBoundaryPolicyResolver}
 * or any other platform kernel class.</p>
 *
 * <p>Rules govern access to shipment records, carrier data, customs declarations,
 * and cargo manifests using logistics-domain vocabulary. No platform class is aware
 * of "shipment", "carrier", or "customs" — those terms appear only here.</p>
 *
 * @doc.type class
 * @doc.purpose Example product boundary policy store for logistics domain
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class NexusBoundaryPolicyStore implements BoundaryPolicyStore {

    private static final String PACK_VERSION = "1.0.0";

    private static final List<BoundaryPolicyRule> NEXUS_RULES = List.of(

            // NEXUS-BP-001: Allow Nexus-internal scopes to read shipment records.
            BoundaryPolicyRule.builder()
                    .ruleId("NEXUS-BP-001")
                    .sourceScopePattern("nexus.*")
                    .targetScopePattern("nexus.*")
                    .resourcePattern("shipments/**")
                    .actions("read")
                    .classificationCondition("INTERNAL")
                    .requiresConsent(false)
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "shipments"))
                    .build(),

            // NEXUS-BP-002: Shipment state write requires approval (immutable state machine).
            BoundaryPolicyRule.builder()
                    .ruleId("NEXUS-BP-002")
                    .sourceScopePattern("nexus.*")
                    .targetScopePattern("nexus.*")
                    .resourcePattern("shipments/**")
                    .actions("write", "cancel", "reroute")
                    .classificationCondition("INTERNAL")
                    .requiresConsent(false)
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.REQUIRE_APPROVAL)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "shipments",
                            "approvalPolicy", "carrier-ops-lead"))
                    .build(),

            // NEXUS-BP-003: Allow carrier management read to authorized Nexus scopes.
            BoundaryPolicyRule.builder()
                    .ruleId("NEXUS-BP-003")
                    .sourceScopePattern("nexus.carrier-ops")
                    .targetScopePattern("nexus.carrier-management")
                    .resourcePattern("carriers/**")
                    .actions("read", "rate")
                    .classificationCondition("*")
                    .requiresConsent(false)
                    .requiresAudit(false)
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "carriers"))
                    .build(),

            // NEXUS-BP-004: Customs declaration export denied to all external scopes.
            BoundaryPolicyRule.builder()
                    .ruleId("NEXUS-BP-004")
                    .sourceScopePattern("**")
                    .targetScopePattern("nexus.customs-compliance")
                    .resourcePattern("declarations/**")
                    .actions("export", "download")
                    .classificationCondition("*")
                    .requiresConsent(false)
                    .requiresAudit(false)
                    .effect(BoundaryPolicyRule.Effect.DENY)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "customs",
                            "reason", "customs-data-sovereignty"))
                    .build(),

            // NEXUS-BP-999: Default deny — must remain last.
            BoundaryPolicyRule.builder()
                    .ruleId("NEXUS-BP-999")
                    .sourceScopePattern("**")
                    .targetScopePattern("**")
                    .resourcePattern("**")
                    .actions("*")
                    .classificationCondition("*")
                    .requiresConsent(false)
                    .requiresAudit(false)
                    .effect(BoundaryPolicyRule.Effect.DENY)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "default-deny"))
                    .build()
    );

    @Override
    public List<BoundaryPolicyRule> loadRules(BoundaryPolicyLoadContext context) {
        return NEXUS_RULES;
    }
}
