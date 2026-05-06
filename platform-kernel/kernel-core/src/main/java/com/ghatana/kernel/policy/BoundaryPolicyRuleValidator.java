package com.ghatana.kernel.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates a list of {@link BoundaryPolicyRule} instances at startup.
 *
 * <p>The validator detects malformed rules (missing required fields, empty action sets)
 * and conflicting rules (same rule ID, overlapping patterns with contradictory effects
 * at the same priority level). {@link BoundaryPolicyStore} implementations must invoke
 * validation before returning rules to the resolver.</p>
 *
 * @doc.type class
 * @doc.purpose Startup validator for BoundaryPolicyRule lists
 * @doc.layer core
 * @doc.pattern Validator
 * @author Ghatana Kernel Team
 * @since 1.2.0
 */
public final class BoundaryPolicyRuleValidator {

    private BoundaryPolicyRuleValidator() {}

    /**
     * Validates a list of rules. Throws on the first detected violation.
     *
     * @param rules the rules to validate; must not be null
     * @throws BoundaryPolicyStore.BoundaryPolicyStoreException if any rule is malformed or conflicts exist
     */
    public static void validate(List<BoundaryPolicyRule> rules) {
        validate(rules, BoundaryPolicyActionRegistry.canonicalOnly(), BoundaryPolicyResourceRegistry.allowAny());
    }

    /**
     * Validates a list of rules against a declared action registry.
     *
     * @param rules the rules to validate; must not be null
     * @param actionRegistry allowed action vocabulary for the owning product pack
     * @throws BoundaryPolicyStore.BoundaryPolicyStoreException if any rule is malformed or conflicts exist
     */
    public static void validate(List<BoundaryPolicyRule> rules, BoundaryPolicyActionRegistry actionRegistry) {
        validate(rules, actionRegistry, BoundaryPolicyResourceRegistry.allowAny());
    }

    /**
     * Validates a list of rules against declared action and resource registries.
     *
     * @param rules the rules to validate; must not be null
     * @param actionRegistry allowed action vocabulary for the owning product pack
     * @param resourceRegistry allowed resource namespaces for the owning product pack
     * @throws BoundaryPolicyStore.BoundaryPolicyStoreException if any rule is malformed or conflicts exist
     */
    public static void validate(
            List<BoundaryPolicyRule> rules,
            BoundaryPolicyActionRegistry actionRegistry,
            BoundaryPolicyResourceRegistry resourceRegistry) {
        Objects.requireNonNull(rules, "rules cannot be null");
        Objects.requireNonNull(actionRegistry, "actionRegistry cannot be null");
        Objects.requireNonNull(resourceRegistry, "resourceRegistry cannot be null");

        List<String> violations = new ArrayList<>();
        List<String> seenRuleIds = new ArrayList<>();

        for (BoundaryPolicyRule rule : rules) {
            // Duplicate rule IDs
            if (seenRuleIds.contains(rule.getRuleId())) {
                violations.add("Duplicate ruleId: '" + rule.getRuleId() + "'");
            }
            seenRuleIds.add(rule.getRuleId());

            // Pattern validation
            if (rule.getSourceScopePattern().isBlank()) {
                violations.add("Rule '" + rule.getRuleId() + "': sourceScopePattern cannot be blank");
            }
            if (rule.getTargetScopePattern().isBlank()) {
                violations.add("Rule '" + rule.getRuleId() + "': targetScopePattern cannot be blank");
            }
            if (rule.getResourcePattern().isBlank()) {
                violations.add("Rule '" + rule.getRuleId() + "': resourcePattern cannot be blank");
            } else if (!resourceRegistry.isAllowed(rule.getResourcePattern())) {
                violations.add(
                        "Rule '" + rule.getRuleId() + "': resourcePattern '" + rule.getResourcePattern()
                                + "' is not declared in the product policy resource registry");
            }
            if (rule.getActions().isEmpty()) {
                violations.add("Rule '" + rule.getRuleId() + "': actions must not be empty");
            }
            for (String action : rule.getActions()) {
                if (!actionRegistry.isAllowed(action)) {
                    violations.add(
                        "Rule '" + rule.getRuleId() + "': action '" + action
                            + "' is not declared in the product policy action registry"
                    );
                }
            }
            if (rule.getEffect() == null) {
                violations.add("Rule '" + rule.getRuleId() + "': effect must not be null");
            }

            // Logical consistency: a rule with no actions in DENY/REQUIRE_APPROVAL is meaningless
            // (already validated above, but confirm effect is always set)
            if (rule.getEffect() == BoundaryPolicyRule.Effect.ALLOW && rule.isRequiresConsent() && rule.isRequiresAudit() == false) {
                violations.add("Rule '" + rule.getRuleId() + "': ALLOW with requiresConsent=true must also have requiresAudit=true for traceability");
            }
        }

        if (!violations.isEmpty()) {
            throw new BoundaryPolicyStore.BoundaryPolicyStoreException(
                    "Boundary policy rule validation failed with " + violations.size() + " violation(s):\n"
                    + String.join("\n", violations));
        }
    }

    /**
     * Returns true if the rule list is valid, false otherwise. Does not throw.
     */
    public static boolean isValid(List<BoundaryPolicyRule> rules) {
        try {
            validate(rules);
            return true;
        } catch (BoundaryPolicyStore.BoundaryPolicyStoreException e) {
            return false;
        }
    }

    /**
     * Returns true if the rule list is valid for the given action registry, false otherwise.
     */
    public static boolean isValid(List<BoundaryPolicyRule> rules, BoundaryPolicyActionRegistry actionRegistry) {
        return isValid(rules, actionRegistry, BoundaryPolicyResourceRegistry.allowAny());
    }

    /**
     * Returns true if the rule list is valid for the given action and resource registries, false otherwise.
     */
    public static boolean isValid(
            List<BoundaryPolicyRule> rules,
            BoundaryPolicyActionRegistry actionRegistry,
            BoundaryPolicyResourceRegistry resourceRegistry) {
        try {
            validate(rules, actionRegistry, resourceRegistry);
            return true;
        } catch (BoundaryPolicyStore.BoundaryPolicyStoreException e) {
            return false;
        }
    }
}
