/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.kernel.policy;

import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyStore;
import com.ghatana.kernel.policy.ProductBoundaryPolicyPackValidator;
import com.ghatana.kernel.policy.ProductBoundaryPolicyValidationProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PHR product boundary policy store.
 *
 * <p>Supplies the boundary policy rules that govern cross-scope access to PHR
 * resources. All rules are loaded from versioned, immutable configuration declared
 * in this class. Products must not rely on kernel defaults — all required rules
 * must be explicitly declared here.</p>
 *
 * <p>PHR enforces the following boundary posture:
 * <ul>
 *   <li>Subject record read requires consent and audit.</li>
 *   <li>Subject record write requires consent, audit, and approval.</li>
 *   <li>Clinical document export is denied to all external scopes.</li>
 *   <li>Interoperability read (e.g., FHIR exchange) requires explicit feature flag.</li>
 *   <li>Any unmatched request is denied by the default-deny rule.</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose PHR product boundary policy rules for cross-scope access control
 * @doc.layer product
 * @doc.pattern Repository
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public final class PhrBoundaryPolicyStore implements BoundaryPolicyStore {

    private static final String PACK_VERSION = "1.0.0";
    private static final ProductBoundaryPolicyValidationProfile VALIDATION_PROFILE =
            ProductBoundaryPolicyValidationProfile.builder()
                    .productName("PHR")
                    .rulePrefix("PHR-BP-")
                    .defaultDenyRuleId("PHR-BP-999")
                    .targetScopePrefix("phr.")
                    .requiredMetadataKeys(Set.of("packVersion", "ruleCategory"))
                    .build();

    /**
     * Immutable list of all PHR boundary rules, evaluated in declaration order.
     * The default-deny rule must remain last.
     */
    private static final List<BoundaryPolicyRule> PHR_RULES = List.of(

            // PHR-BP-001: Allow any PHR-internal scope to read its own subject records.
            BoundaryPolicyRule.builder()
                    .ruleId("PHR-BP-001")
                    .sourceScopePattern("phr.*")
                    .targetScopePattern("phr.*")
                    .resourcePattern("subject-records/**")
                    .actions("read")
                    .classificationCondition("*")
                    .requiresConsent(true)
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "subject-records"))
                    .build(),

            // PHR-BP-002: Subject record writes require consent, audit, and approval.
            BoundaryPolicyRule.builder()
                    .ruleId("PHR-BP-002")
                    .sourceScopePattern("phr.*")
                    .targetScopePattern("phr.*")
                    .resourcePattern("subject-records/**")
                    .actions("write", "delete")
                    .classificationCondition("*")
                    .requiresConsent(true)
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.REQUIRE_APPROVAL)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "subject-records"))
                    .build(),

            // PHR-BP-003: Interoperability read requires explicit feature flag.
            BoundaryPolicyRule.builder()
                    .ruleId("PHR-BP-003")
                    .sourceScopePattern("**")
                    .targetScopePattern("phr.*")
                    .resourcePattern("interop/**")
                    .actions("read")
                    .classificationCondition("*")
                    .requiredFeatures(Set.of("phr.interop.enabled"))
                    .requiresConsent(true)
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "interoperability"))
                    .build(),

            // PHR-BP-004: Clinical document export is always denied to external scopes.
            BoundaryPolicyRule.builder()
                    .ruleId("PHR-BP-004")
                    .sourceScopePattern("**")
                    .targetScopePattern("phr.*")
                    .resourcePattern("clinical-documents/**")
                    .actions("export", "download")
                    .classificationCondition("*")
                    .requiresConsent(false)
                    .requiresAudit(false)
                    .effect(BoundaryPolicyRule.Effect.DENY)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "clinical-documents",
                            "denialReason", "external-export-not-permitted"))
                    .build(),

            // PHR-BP-999: Default deny — any unmatched cross-scope access is denied.
            BoundaryPolicyRule.builder()
                    .ruleId("PHR-BP-999")
                    .sourceScopePattern("**")
                    .targetScopePattern("phr.*")
                    .resourcePattern("**")
                    .actions("*")
                    .classificationCondition("*")
                    .requiresConsent(false)
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.DENY)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "default-deny",
                            "denialReason", "no-matching-allow-rule"))
                    .build()
    );

    @Override
    public List<BoundaryPolicyRule> loadRules(BoundaryPolicyLoadContext context) {
        if (!"default".equals(context.getTenantId()) || !"GLOBAL".equalsIgnoreCase(context.getRegion())) {
            throw new BoundaryPolicyStoreException(
                    "PHR boundary policy overrides are unsupported. "
                            + "Only tenantId=default and region=GLOBAL are allowed; failing closed for "
                            + context);
        }
        return ProductBoundaryPolicyPackValidator.validate(PHR_RULES, VALIDATION_PROFILE);
    }
}
