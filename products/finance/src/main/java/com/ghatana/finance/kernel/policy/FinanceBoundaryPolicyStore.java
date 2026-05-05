/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.kernel.policy;

import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyStore;
import com.ghatana.kernel.policy.ProductBoundaryPolicyPackValidator;
import com.ghatana.kernel.policy.ProductBoundaryPolicyValidationProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finance product boundary policy store.
 *
 * <p>Supplies the boundary policy rules that govern cross-scope access to Finance
 * resources. Rules are immutable and version-pinned to the pack version declared
 * in this class. Products must not rely on kernel defaults — all required rules
 * must be explicitly declared here.</p>
 *
 * <p>Finance enforces the following boundary posture:
 * <ul>
 *   <li>Transaction read requires audit and explicit authorization.</li>
 *   <li>Transaction write and settlement require approval (four-eyes principle).</li>
 *   <li>Position data export is denied to all external scopes.</li>
 *   <li>Market-data read from interop scope requires explicit feature flag.</li>
 *   <li>Any unmatched request is denied by the default-deny rule.</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose Finance product boundary policy rules for cross-scope access control
 * @doc.layer product
 * @doc.pattern Repository
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceBoundaryPolicyStore implements BoundaryPolicyStore {

    private static final String PACK_VERSION = "1.0.0";
    private static final ProductBoundaryPolicyValidationProfile VALIDATION_PROFILE =
            ProductBoundaryPolicyValidationProfile.builder()
                    .productName("Finance")
                    .rulePrefix("FIN-BP-")
                    .defaultDenyRuleId("FIN-BP-999")
                    .targetScopePrefix("finance.")
                    .requiredMetadataKeys(Set.of("packVersion", "ruleCategory"))
                    .build();

    /**
     * Immutable list of Finance boundary rules, evaluated in declaration order.
     * The default-deny rule must remain last.
     */
    private static final List<BoundaryPolicyRule> FINANCE_RULES = List.of(

            // FIN-BP-001: Allow finance-internal scopes to read transaction records
            // (requires audit).
            BoundaryPolicyRule.builder()
                    .ruleId("FIN-BP-001")
                    .sourceScopePattern("finance.*")
                    .targetScopePattern("finance.*")
                    .resourcePattern("transactions/*")
                    .actions("read")
                    .classificationCondition("*")
                    .requiresConsent(false)
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "transactions"))
                    .build(),

            // FIN-BP-002: Transaction writes and settlements require four-eyes approval.
            BoundaryPolicyRule.builder()
                    .ruleId("FIN-BP-002")
                    .sourceScopePattern("finance.*")
                    .targetScopePattern("finance.*")
                    .resourcePattern("transactions/*")
                    .actions("write", "settle", "cancel")
                    .classificationCondition("*")
                    .requiresConsent(false)
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.REQUIRE_APPROVAL)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "transactions",
                            "approvalPolicy", "four-eyes"))
                    .build(),

            // FIN-BP-003: Position data export is denied to all scopes (including internal).
            BoundaryPolicyRule.builder()
                    .ruleId("FIN-BP-003")
                    .sourceScopePattern("**")
                    .targetScopePattern("**")
                    .resourcePattern("positions/*")
                    .actions("export", "download")
                    .classificationCondition("*")
                    .requiresConsent(false)
                    .requiresAudit(false)
                    .effect(BoundaryPolicyRule.Effect.DENY)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "positions",
                            "denialReason", "position-export-not-permitted"))
                    .build(),

            // FIN-BP-004: Market-data interop read requires explicit feature flag.
            BoundaryPolicyRule.builder()
                    .ruleId("FIN-BP-004")
                    .sourceScopePattern("**")
                    .targetScopePattern("**")
                    .resourcePattern("market-data/*")
                    .actions("read")
                    .classificationCondition("*")
                    .requiredFeatures(Set.of("finance.market-data.interop.enabled"))
                    .requiresConsent(false)
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "market-data"))
                    .build(),

            // FIN-BP-005: Allow finance-internal scopes to read risk data (with audit).
            BoundaryPolicyRule.builder()
                    .ruleId("FIN-BP-005")
                    .sourceScopePattern("finance.*")
                    .targetScopePattern("finance.*")
                    .resourcePattern("risk-data/*")
                    .actions("read")
                    .classificationCondition("*")
                    .requiresConsent(false)
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "risk-data"))
                    .build(),

            // FIN-BP-999: Default deny — any unmatched cross-scope access is denied.
            BoundaryPolicyRule.builder()
                    .ruleId("FIN-BP-999")
                    .sourceScopePattern("**")
                    .targetScopePattern("finance.*")
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
                    "Finance boundary policy overrides are unsupported. "
                            + "Only tenantId=default and region=GLOBAL are allowed; failing closed for "
                            + context);
        }
        return ProductBoundaryPolicyPackValidator.validate(FINANCE_RULES, VALIDATION_PROFILE);
    }
}
