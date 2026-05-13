package com.ghatana.flashit.kernel.policy;

import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyActionRegistry;
import com.ghatana.kernel.policy.BoundaryPolicyResourceRegistry;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyStore;
import com.ghatana.kernel.policy.ProductBoundaryPolicyPackValidator;
import com.ghatana.kernel.policy.ProductBoundaryPolicyValidationProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Supplies FlashIt product-owned boundary policy rules
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class FlashItBoundaryPolicyStore implements BoundaryPolicyStore {

    private static final String PACK_VERSION = "1.0.0";
    private static final ProductBoundaryPolicyValidationProfile VALIDATION_PROFILE =
            ProductBoundaryPolicyValidationProfile.builder()
                    .productName("FlashIt")
                    .rulePrefix("FLASHIT-BP-")
                    .defaultDenyRuleId("FLASHIT-BP-999")
                    .targetScopePrefix("flashit.")
                    .requiredMetadataKeys(Set.of("packVersion", "ruleCategory"))
                    .build();
    private static final BoundaryPolicyActionRegistry ACTION_REGISTRY =
            BoundaryPolicyActionRegistry.ofDeclaredActions(
                    Set.of("read", "write", "delete", "export"));
    private static final BoundaryPolicyResourceRegistry RESOURCE_REGISTRY =
            BoundaryPolicyResourceRegistry.ofDeclaredResources(
                    Set.of("flashit:moments", "flashit:moment-children"));

    private static final List<BoundaryPolicyRule> RULES = List.of(
            BoundaryPolicyRule.builder()
                    .ruleId("FLASHIT-BP-001")
                    .sourceScopePattern("flashit.*")
                    .targetScopePattern("flashit.*")
                    .resourcePattern("flashit:moments/**")
                    .actions("read")
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "moments"))
                    .build(),
            BoundaryPolicyRule.builder()
                    .ruleId("FLASHIT-BP-002")
                    .sourceScopePattern("flashit.*")
                    .targetScopePattern("flashit.*")
                    .resourcePattern("flashit:moments/**")
                    .actions("write", "delete", "export")
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.REQUIRE_APPROVAL)
                    .metadata(Map.of(
                            "packVersion", PACK_VERSION,
                            "ruleCategory", "moments",
                            "approvalPolicy", "human-approval"))
                    .build(),
            BoundaryPolicyRule.builder()
                    .ruleId("FLASHIT-BP-999")
                    .sourceScopePattern("**")
                    .targetScopePattern("flashit.*")
                    .resourcePattern("**")
                    .actions("*")
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.DENY)
                    .metadata(Map.of(
                            "packVersion", PACK_VERSION,
                            "ruleCategory", "default-deny",
                            "denialReason", "no-matching-allow-rule"))
                    .build()
    );

    @Override
    public List<BoundaryPolicyRule> loadRules(BoundaryPolicyLoadContext context) {
        if (!"default".equals(context.getTenantId()) || !"GLOBAL".equalsIgnoreCase(context.getRegion())) {
            throw new BoundaryPolicyStoreException(
                    "FlashIt boundary policy overrides are unsupported. "
                            + "Only tenantId=default and region=GLOBAL are allowed; failing closed for "
                            + context);
        }
        return ProductBoundaryPolicyPackValidator.validate(
                RULES,
                VALIDATION_PROFILE,
                ACTION_REGISTRY,
                RESOURCE_REGISTRY);
    }
}
