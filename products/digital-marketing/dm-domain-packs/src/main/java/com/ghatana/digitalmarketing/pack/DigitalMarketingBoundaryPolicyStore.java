package com.ghatana.digitalmarketing.pack;

import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyRule.Effect;
import com.ghatana.kernel.policy.BoundaryPolicyStore;
import com.ghatana.kernel.policy.ProductBoundaryPolicyPackValidator;
import com.ghatana.kernel.policy.ProductBoundaryPolicyValidationProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DMOS boundary policy store providing the complete set of platform-level access rules
 * for all digital-marketing resources.
 *
 * <p>Rules are evaluated by the kernel's {@code BoundaryPolicyResolver} in list order.
 * The first matching rule with a non-ALLOW effect terminates evaluation. The default-deny
 * sentinel rule {@code DM-BP-999} must be last and ensures no resource is accidentally
 * accessible when no explicit rule matches.</p>
 *
 * <h3>Rule inventory</h3>
 * <ul>
 *   <li>DM-BP-001 — workspaces/**: read — ALLOW</li>
 *   <li>DM-BP-002 — contacts/**: read — ALLOW (requiresConsent, requiresAudit)</li>
 *   <li>DM-BP-003 — contacts/**: write,delete,export — REQUIRE_APPROVAL (requiresAudit)</li>
 *   <li>DM-BP-004 — audiences/**: export,sync — REQUIRE_APPROVAL (requiresConsent, requiresAudit)</li>
 *   <li>DM-BP-005 — campaigns/**: launch,pause,resume — REQUIRE_APPROVAL (requiresAudit)</li>
 *   <li>DM-BP-006 — budgets/**: write,increase — REQUIRE_APPROVAL (requiresAudit)</li>
 *   <li>DM-BP-007 — content/**: publish — REQUIRE_APPROVAL (requiresAudit)</li>
 *   <li>DM-BP-008 — connectors/**: write,execute — REQUIRE_APPROVAL (requiresAudit)</li>
 *   <li>DM-BP-999 — **: * — DENY (default-deny for all unmatched requests)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DMOS boundary policy store; supplies all access rules for digital-marketing resources
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class DigitalMarketingBoundaryPolicyStore implements BoundaryPolicyStore {

    private static final String PACK_VERSION = "1.0.0";
    private static final ProductBoundaryPolicyValidationProfile VALIDATION_PROFILE =
        ProductBoundaryPolicyValidationProfile.builder()
            .productName("Digital Marketing")
            .rulePrefix("DM-BP-")
            .defaultDenyRuleId("DM-BP-999")
            .targetScopePrefix("digital-marketing.")
            .requiredMetadataKeys(Set.of("packVersion", "ruleCategory"))
            .build();

    /** Source scope pattern used on all DMOS rules — matches any caller within the dm scope. */
    private static final String DM_SOURCE_SCOPE = "digital-marketing.*";

    /** Target scope pattern used on all DMOS rules — matches any dm resource target. */
    private static final String DM_TARGET_SCOPE = "digital-marketing.*";

    /** The singleton ordered list of all DMOS boundary policy rules. */
    private static final List<BoundaryPolicyRule> RULES = buildRules();

    /**
     * Returns the complete ordered list of DMOS boundary policy rules.
     *
     * <p>Rules are returned in the same order for every call and every context.
     * Tenant-level overrides are not currently supported — all tenants share the
     * same DMOS rule set. Tenant-specific ACLs are enforced at the application layer.</p>
     *
     * @param context load context (tenant / region); not used to filter DMOS base rules
     * @return immutable list of boundary policy rules; never null or empty
     */
    @Override
    public List<BoundaryPolicyRule> loadRules(BoundaryPolicyLoadContext context) {
        if (!"default".equals(context.getTenantId()) || !"GLOBAL".equalsIgnoreCase(context.getRegion())) {
            throw new BoundaryPolicyStoreException(
                "Digital Marketing boundary policy overrides are unsupported. "
                    + "Only tenantId=default and region=GLOBAL are allowed; failing closed for "
                    + context);
        }
        return ProductBoundaryPolicyPackValidator.validate(RULES, VALIDATION_PROFILE);
    }

    // -----------------------------------------------------------------------
    // Rule construction
    // -----------------------------------------------------------------------

    private static List<BoundaryPolicyRule> buildRules() {
        return List.of(
            workspaceReadAllow(),
            contactReadAllow(),
            contactWriteRequireApproval(),
            audienceExportSyncRequireApproval(),
            campaignLifecycleRequireApproval(),
            budgetWriteRequireApproval(),
            contentPublishRequireApproval(),
            connectorWriteExecuteRequireApproval(),
            defaultDeny()
        );
    }

    /** DM-BP-001: workspaces/**: read → ALLOW */
    private static BoundaryPolicyRule workspaceReadAllow() {
        return BoundaryPolicyRule.builder()
            .ruleId("DM-BP-001")
            .sourceScopePattern(DM_SOURCE_SCOPE)
            .targetScopePattern(DM_TARGET_SCOPE)
            .resourcePattern("workspaces/**")
            .actions(Set.of("read"))
            .requiresConsent(false)
            .requiresAudit(false)
            .effect(Effect.ALLOW)
            .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "workspaces"))
            .build();
    }

    /** DM-BP-002: contacts/**: read → ALLOW (requiresConsent, requiresAudit) */
    private static BoundaryPolicyRule contactReadAllow() {
        return BoundaryPolicyRule.builder()
            .ruleId("DM-BP-002")
            .sourceScopePattern(DM_SOURCE_SCOPE)
            .targetScopePattern(DM_TARGET_SCOPE)
            .resourcePattern("contacts/**")
            .actions(Set.of("read"))
            .requiresConsent(true)
            .requiresAudit(true)
            .effect(Effect.ALLOW)
            .metadata(Map.of("packVersion", PACK_VERSION, "ruleCategory", "contacts"))
            .build();
    }

    /** DM-BP-003: contacts/**: write, delete, export → REQUIRE_APPROVAL (requiresAudit) */
    private static BoundaryPolicyRule contactWriteRequireApproval() {
        return BoundaryPolicyRule.builder()
            .ruleId("DM-BP-003")
            .sourceScopePattern(DM_SOURCE_SCOPE)
            .targetScopePattern(DM_TARGET_SCOPE)
            .resourcePattern("contacts/**")
            .actions(Set.of("write", "delete", "export"))
            .requiresConsent(false)
            .requiresAudit(true)
            .effect(Effect.REQUIRE_APPROVAL)
            .metadata(Map.of(
                "packVersion", PACK_VERSION,
                "ruleCategory", "contacts",
                "approvalPolicy", "human-approval"))
            .build();
    }

    /** DM-BP-004: audiences/**: export, sync → REQUIRE_APPROVAL (requiresConsent, requiresAudit) */
    private static BoundaryPolicyRule audienceExportSyncRequireApproval() {
        return BoundaryPolicyRule.builder()
            .ruleId("DM-BP-004")
            .sourceScopePattern(DM_SOURCE_SCOPE)
            .targetScopePattern(DM_TARGET_SCOPE)
            .resourcePattern("audiences/**")
            .actions(Set.of("export", "sync"))
            .requiresConsent(true)
            .requiresAudit(true)
            .effect(Effect.REQUIRE_APPROVAL)
            .metadata(Map.of(
                "packVersion", PACK_VERSION,
                "ruleCategory", "audiences",
                "approvalPolicy", "human-approval"))
            .build();
    }

    /** DM-BP-005: campaigns/**: launch, pause, resume → REQUIRE_APPROVAL (requiresAudit) */
    private static BoundaryPolicyRule campaignLifecycleRequireApproval() {
        return BoundaryPolicyRule.builder()
            .ruleId("DM-BP-005")
            .sourceScopePattern(DM_SOURCE_SCOPE)
            .targetScopePattern(DM_TARGET_SCOPE)
            .resourcePattern("campaigns/**")
            .actions(Set.of("launch", "pause", "resume"))
            .requiresConsent(false)
            .requiresAudit(true)
            .effect(Effect.REQUIRE_APPROVAL)
            .metadata(Map.of(
                "packVersion", PACK_VERSION,
                "ruleCategory", "campaigns",
                "approvalPolicy", "human-approval"))
            .build();
    }

    /** DM-BP-006: budgets/**: write, increase → REQUIRE_APPROVAL (requiresAudit) */
    private static BoundaryPolicyRule budgetWriteRequireApproval() {
        return BoundaryPolicyRule.builder()
            .ruleId("DM-BP-006")
            .sourceScopePattern(DM_SOURCE_SCOPE)
            .targetScopePattern(DM_TARGET_SCOPE)
            .resourcePattern("budgets/**")
            .actions(Set.of("write", "increase"))
            .requiresConsent(false)
            .requiresAudit(true)
            .effect(Effect.REQUIRE_APPROVAL)
            .metadata(Map.of(
                "packVersion", PACK_VERSION,
                "ruleCategory", "budgets",
                "approvalPolicy", "human-approval"))
            .build();
    }

    /** DM-BP-007: content/**: publish → REQUIRE_APPROVAL (requiresAudit) */
    private static BoundaryPolicyRule contentPublishRequireApproval() {
        return BoundaryPolicyRule.builder()
            .ruleId("DM-BP-007")
            .sourceScopePattern(DM_SOURCE_SCOPE)
            .targetScopePattern(DM_TARGET_SCOPE)
            .resourcePattern("content/**")
            .actions(Set.of("publish"))
            .requiresConsent(false)
            .requiresAudit(true)
            .effect(Effect.REQUIRE_APPROVAL)
            .metadata(Map.of(
                "packVersion", PACK_VERSION,
                "ruleCategory", "content",
                "approvalPolicy", "human-approval"))
            .build();
    }

    /** DM-BP-008: connectors/**: write, execute → REQUIRE_APPROVAL (requiresAudit) */
    private static BoundaryPolicyRule connectorWriteExecuteRequireApproval() {
        return BoundaryPolicyRule.builder()
            .ruleId("DM-BP-008")
            .sourceScopePattern(DM_SOURCE_SCOPE)
            .targetScopePattern(DM_TARGET_SCOPE)
            .resourcePattern("connectors/**")
            .actions(Set.of("write", "execute"))
            .requiresConsent(false)
            .requiresAudit(true)
            .effect(Effect.REQUIRE_APPROVAL)
            .metadata(Map.of(
                "packVersion", PACK_VERSION,
                "ruleCategory", "connectors",
                "approvalPolicy", "human-approval"))
            .build();
    }

    /** DM-BP-999: **: * → DENY (default-deny for all unmatched requests over digital-marketing.*) */
    private static BoundaryPolicyRule defaultDeny() {
        return BoundaryPolicyRule.builder()
            .ruleId("DM-BP-999")
            .sourceScopePattern(DM_SOURCE_SCOPE)
            .targetScopePattern(DM_TARGET_SCOPE)
            .resourcePattern("**")
            .actions(Set.of("*"))
            .requiresConsent(false)
            .requiresAudit(true)
            .effect(Effect.DENY)
            .metadata(Map.of(
                "packVersion", PACK_VERSION,
                "ruleCategory", "default-deny",
                "denialReason", "no-matching-allow-rule"))
            .build();
    }
}
