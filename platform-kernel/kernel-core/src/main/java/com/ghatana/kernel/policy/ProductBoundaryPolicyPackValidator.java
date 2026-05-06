package com.ghatana.kernel.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Applies product-pack validation rules consistently across all product boundary policy stores
 * @doc.layer core
 * @doc.pattern Validator
 */
public final class ProductBoundaryPolicyPackValidator {

    private ProductBoundaryPolicyPackValidator() {
    }

    public static List<BoundaryPolicyRule> validate(
            List<BoundaryPolicyRule> rules,
            ProductBoundaryPolicyValidationProfile profile) {
        return validate(
                rules,
                profile,
                BoundaryPolicyActionRegistry.canonicalOnly(),
                BoundaryPolicyResourceRegistry.empty());
    }

    public static List<BoundaryPolicyRule> validate(
            List<BoundaryPolicyRule> rules,
            ProductBoundaryPolicyValidationProfile profile,
            BoundaryPolicyActionRegistry actionRegistry) {
        return validate(rules, profile, actionRegistry, BoundaryPolicyResourceRegistry.empty());
    }

    public static List<BoundaryPolicyRule> validate(
            List<BoundaryPolicyRule> rules,
            ProductBoundaryPolicyValidationProfile profile,
            BoundaryPolicyActionRegistry actionRegistry,
            BoundaryPolicyResourceRegistry resourceRegistry) {
        BoundaryPolicyRuleValidator.validate(rules, actionRegistry, resourceRegistry);

        List<String> violations = new ArrayList<>();
        BoundaryPolicyRule defaultDenyRule = null;

        for (BoundaryPolicyRule rule : rules) {
            if (!rule.getRuleId().startsWith(profile.getRulePrefix())) {
                violations.add("Rule '" + rule.getRuleId() + "' must start with prefix '" + profile.getRulePrefix() + "'");
            }

            Map<String, String> metadata = rule.getMetadata();
            for (String key : profile.getRequiredMetadataKeys()) {
                String value = metadata.get(key);
                if (value == null || value.isBlank()) {
                    violations.add("Rule '" + rule.getRuleId() + "' is missing required metadata key '" + key + "'");
                }
            }

            if ("**".equals(rule.getTargetScopePattern()) && rule.getEffect() != BoundaryPolicyRule.Effect.DENY) {
                violations.add("Rule '" + rule.getRuleId() + "' uses unsafe targetScopePattern '**' without deny effect");
            }

            if (rule.getRuleId().equals(profile.getDefaultDenyRuleId())) {
                if (defaultDenyRule != null) {
                    violations.add("Multiple default-deny rules found for '" + profile.getProductName() + "'");
                }
                defaultDenyRule = rule;
            }
        }

        if (rules.isEmpty()) {
            violations.add("No boundary policy rules defined for '" + profile.getProductName() + "'");
        } else {
            BoundaryPolicyRule lastRule = rules.get(rules.size() - 1);
            if (!lastRule.getRuleId().equals(profile.getDefaultDenyRuleId())) {
                violations.add("Last rule must be default-deny rule '" + profile.getDefaultDenyRuleId() + "'");
            }
        }

        if (defaultDenyRule == null) {
            violations.add("Missing default-deny rule '" + profile.getDefaultDenyRuleId() + "'");
        } else {
            if (defaultDenyRule.getEffect() != BoundaryPolicyRule.Effect.DENY) {
                violations.add("Default-deny rule must use DENY effect");
            }
            if (!"**".equals(defaultDenyRule.getSourceScopePattern())) {
                violations.add("Default-deny rule must use sourceScopePattern '**'");
            }
            if (!defaultDenyRule.getTargetScopePattern().startsWith(profile.getTargetScopePrefix())) {
                violations.add("Default-deny rule must target scope prefix '" + profile.getTargetScopePrefix() + "'");
            }
            if (!"**".equals(defaultDenyRule.getResourcePattern())) {
                violations.add("Default-deny rule must use resourcePattern '**'");
            }
            if (!defaultDenyRule.getActions().contains("*")) {
                violations.add("Default-deny rule must include wildcard action '*'");
            }
            if (profile.isRequireAuditOnDefaultDeny() && !defaultDenyRule.isRequiresAudit()) {
                violations.add("Default-deny rule must require audit");
            }
        }

        if (!violations.isEmpty()) {
            throw new BoundaryPolicyStore.BoundaryPolicyStoreException(
                    "Product boundary policy validation failed for '" + profile.getProductName() + "':\n"
                            + String.join("\n", violations));
        }

        return List.copyOf(rules);
    }
}
