package com.ghatana.kernel.policy;

import com.ghatana.kernel.scope.ScopeDescriptor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Default boundary policy resolver driven by a rule-based {@link BoundaryPolicyStore}.
 *
 * <p>Replaces the prior map-based implementation (scopeDependencies / regionalRestrictions)
 * with a generic rule evaluation engine. Rules are loaded from a {@link BoundaryPolicyStore}
 * and evaluated in list order; the first matching rule determines the outcome. If no rule
 * matches, access is denied by default (default-deny posture).</p>
 *
 * <p>Rule matching applies the following tests in order:</p>
 * <ol>
 *   <li>Source scope ID matches {@link BoundaryPolicyRule#getSourceScopePattern()} via glob</li>
 *   <li>Target scope ID matches {@link BoundaryPolicyRule#getTargetScopePattern()} - "same"
 *       is a reserved keyword meaning source and target are identical</li>
 *   <li>Resource matches {@link BoundaryPolicyRule#getResourcePattern()} via glob</li>
 *   <li>Action is contained in {@link BoundaryPolicyRule#getActions()}, or the rule allows "*"</li>
 *   <li>Classification matches {@link BoundaryPolicyRule#getClassificationCondition()}</li>
 * </ol>
 *
 * <p>Once matched, the rule effect determines the {@link BoundaryDecision}:
 * ALLOW -> allowed; DENY -> denied with explicit reason; REQUIRE_APPROVAL -> denied pending workflow.</p>
 *
 * @doc.type class
 * @doc.purpose Rule-based boundary policy resolver; replaces map-based implementation
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.2.0
 */
public class DefaultBoundaryPolicyResolver implements BoundaryPolicyResolver {

    private final BoundaryPolicyStore store;
    private final BoundaryPolicyLoadContext defaultLoadContext;

    /**
     * Creates a resolver backed by the given store, using a global load context.
     *
     * @param store the policy rule store; must not be null
     */
    public DefaultBoundaryPolicyResolver(BoundaryPolicyStore store) {
        this(store, BoundaryPolicyLoadContext.global());
    }

    /**
     * Creates a resolver backed by the given store and load context.
     *
     * @param store              the policy rule store; must not be null
     * @param defaultLoadContext the load context used for rule retrieval
     */
    public DefaultBoundaryPolicyResolver(BoundaryPolicyStore store,
                                          BoundaryPolicyLoadContext defaultLoadContext) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.defaultLoadContext = Objects.requireNonNull(defaultLoadContext, "defaultLoadContext cannot be null");
    }

    /**
     * Creates a resolver pre-loaded with the standard platform rule set.
     *
     * @return a resolver backed by standard platform rules
     */
    public static DefaultBoundaryPolicyResolver withStandardMappings() {
        return new DefaultBoundaryPolicyResolver(InMemoryBoundaryPolicyStore.withStandardRules());
    }

    @Override
    public BoundaryDecision resolve(ScopeDescriptor source, ScopeDescriptor target,
                                    String resource, String action,
                                    ClassificationDescriptor classification) {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(resource, "resource cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(classification, "classification cannot be null");

        List<BoundaryPolicyRule> rules = store.loadRules(defaultLoadContext);
        BoundaryPolicyEvaluationRequest request =
                BoundaryPolicyEvaluationRequest.of(source, target, resource, action, classification);

        for (BoundaryPolicyRule rule : rules) {
            if (!matchesRule(rule, source, target, resource, action, classification)) {
                continue;
            }
            return effectToDecision(rule, request, classification, target);
        }

        // Default-deny: no rule matched
        return BoundaryDecision.deny("No matching allow rule for "
                + source.getScopeId() + " -> " + target.getScopeId()
                + " [" + resource + ":" + action + "]");
    }

    // Rule matching

    private boolean matchesRule(BoundaryPolicyRule rule,
                                ScopeDescriptor source, ScopeDescriptor target,
                                String resource, String action,
                                ClassificationDescriptor classification) {
        if ("same".equalsIgnoreCase(rule.getTargetScopePattern())) {
            if (!source.equals(target)) return false;
        } else {
            if (!globMatches(rule.getTargetScopePattern(), target.getScopeId())) return false;
        }
        if (!globMatches(rule.getSourceScopePattern(), source.getScopeId())) return false;
        if (!globMatches(rule.getResourcePattern(), resource)) return false;
        if (!matchesAction(rule, action)) return false;
        if (!matchesClassification(rule, classification)) return false;
        return true;
    }

    private boolean matchesAction(BoundaryPolicyRule rule, String action) {
        return rule.getActions().contains("*") || rule.getActions().contains(action);
    }

    private boolean matchesClassification(BoundaryPolicyRule rule, ClassificationDescriptor classification) {
        String condition = rule.getClassificationCondition();
        if ("*".equals(condition)) return true;
        String lc = condition.toLowerCase(Locale.ROOT);
        if (lc.startsWith("sensitivity:")) {
            String level = lc.substring("sensitivity:".length()).toUpperCase(Locale.ROOT);
            return classification.getSensitivityLevel().name().equals(level);
        }
        if (lc.startsWith("compliance-tag:")) {
            String tag = condition.substring("compliance-tag:".length());
            return classification.hasComplianceTag(tag);
        }
        if (lc.startsWith("domain:")) {
            String domain = condition.substring("domain:".length());
            return classification.getDomain().equalsIgnoreCase(domain);
        }
        return false;
    }

    private boolean globMatches(String pattern, String value) {
        if ("*".equals(pattern) || "**".equals(pattern)) return true;
        if (pattern.equals(value)) return true;
        
        // Handle **/ prefix (matches any number of directories)
        if (pattern.startsWith("**/")) {
            String suffix = pattern.substring(3);
            return value.endsWith(suffix);
        }
        
        // Handle /** suffix (matches any number of directories and files)
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return value.startsWith(prefix);
        }
        
        // Handle /** in the middle (matches any number of directories)
        if (pattern.contains("/**")) {
            String[] parts = pattern.split("/\\*\\*/", 2);
            if (parts.length == 2) {
                return value.startsWith(parts[0]) && value.endsWith(parts[1]);
            }
        }
        
        if (pattern.startsWith("*") && pattern.endsWith("*") && pattern.length() > 2) {
            return value.contains(pattern.substring(1, pattern.length() - 1));
        }
        if (pattern.startsWith("*")) return value.endsWith(pattern.substring(1));
        if (pattern.endsWith("*")) return value.startsWith(pattern.substring(0, pattern.length() - 1));
        return false;
    }

    // Effect to BoundaryDecision translation

    private BoundaryDecision effectToDecision(BoundaryPolicyRule rule,
                                              BoundaryPolicyEvaluationRequest request,
                                              ClassificationDescriptor classification,
                                              ScopeDescriptor target) {
        return switch (rule.getEffect()) {
            case DENY -> BoundaryDecision.deny("Access denied by rule '" + rule.getRuleId() + "'");
            case REQUIRE_APPROVAL -> BoundaryDecision.deny(
                    "Access for [" + request.getResource() + ":" + request.getAction()
                    + "] requires approval workflow (rule: " + rule.getRuleId() + ")");
            case ALLOW -> buildAllowDecision(rule, classification, target);
        };
    }

    private BoundaryDecision buildAllowDecision(BoundaryPolicyRule rule,
                                                ClassificationDescriptor classification,
                                                ScopeDescriptor target) {
        boolean requiresConsent = rule.isRequiresConsent();
        boolean requiresAudit = rule.isRequiresAudit();
        Set<String> requiredFeatures = requiresConsent && rule.getRequiredFeatures().isEmpty()
                ? Set.of("cross-scope.consent." + target.getScopeType().name().toLowerCase(Locale.ROOT))
                : rule.getRequiredFeatures();
        return new BoundaryDecision(
                true,
                requiresConsent,
                requiresAudit,
                requiredFeatures,
                Map.of(
                        "matched_rule", rule.getRuleId(),
                        "source_sensitivity", classification.getSensitivityLevel().name()
                )
        );
    }
}
