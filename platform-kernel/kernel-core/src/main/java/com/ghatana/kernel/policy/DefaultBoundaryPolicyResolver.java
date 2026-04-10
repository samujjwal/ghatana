package com.ghatana.kernel.policy;

import com.ghatana.kernel.scope.ScopeDescriptor;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Default boundary policy resolver using scope-aware rules.
 *
 * <p>Implements policy-driven boundary enforcement that replaces the hardcoded
 * product allowlists from ProductBoundaryEnforcer. This resolver evaluates
 * access based on scope types, resource classifications, and compliance tags
 * rather than literal product string matching.</p>
 *
 * <p>Policy rules:</p>
 * <ul>
 *   <li>Products can access domain packs they depend on</li>
 *   <li>Domain packs can access shared infrastructure</li>
 *   <li>Restricted data requires explicit consent</li>
 *   <li>Cross-border access respects regional restrictions</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default boundary policy resolver with scope-aware rules
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public class DefaultBoundaryPolicyResolver implements BoundaryPolicyResolver {

    private final Map<String, Set<String>> scopeDependencies;
    private final Map<String, Set<String>> regionalRestrictions;

    /**
     * Creates a resolver with the given scope dependencies and regional restrictions.
     *
     * @param scopeDependencies    map from scope to allowed target scopes
     * @param regionalRestrictions map from region to restricted scope types
     */
    public DefaultBoundaryPolicyResolver(Map<String, Set<String>> scopeDependencies,
                                        Map<String, Set<String>> regionalRestrictions) {
        this.scopeDependencies = Objects.requireNonNull(scopeDependencies);
        this.regionalRestrictions = Objects.requireNonNull(regionalRestrictions);
    }

    /**
     * Creates a resolver with standard scope dependency mappings.
     */
    public static DefaultBoundaryPolicyResolver withStandardMappings() {
        Map<String, Set<String>> dependencies = Map.of(
                // Products can access domain packs they depend on
                "product-health", Set.of("healthcare-pack", "shared-infrastructure"),
                "product-regulatory", Set.of("regulatory-pack", "shared-infrastructure"),
                "product-recommendation", Set.of("recommendation-pack", "analytics-pack"),

                // Domain packs can access shared infrastructure
                "healthcare-pack", Set.of("shared-infrastructure"),
                "regulatory-pack", Set.of("shared-infrastructure"),
                "recommendation-pack", Set.of("shared-infrastructure"),

                // Shared infrastructure can access core services
                "shared-infrastructure", Set.of("kernel-core")
        );

        Map<String, Set<String>> restrictions = Map.of(
                "CN", Set.of("healthcare-pack", "regulatory-pack"),
                "RU", Set.of("healthcare-pack", "regulatory-pack"),
                "US", Set.of(),
                "EU", Set.of("analytics-pack") // GDPR restrictions
        );

        return new DefaultBoundaryPolicyResolver(dependencies, restrictions);
    }

    @Override
    public BoundaryDecision resolve(ScopeDescriptor source, ScopeDescriptor target,
                                   String resource, String action,
                                   ClassificationDescriptor classification) {

        // Check scope dependency rules
        Set<String> allowedTargets = scopeDependencies.getOrDefault(source.getScopeId(), Set.of());
        if (!allowedTargets.contains(target.getScopeId()) && !allowedTargets.contains("*")) {
            return BoundaryDecision.deny("Scope dependency not allowed: " +
                    source + " → " + target);
        }

        // Check resource-specific rules
        if (!isResourceActionAllowed(source, target, resource, action, classification)) {
            return BoundaryDecision.deny("Resource-action not allowed for classification");
        }

        // Check regional restrictions
        String sourceRegion = source.getMetadata("region", "US");
        Set<String> restrictedScopes = regionalRestrictions.getOrDefault(sourceRegion, Set.of());
        if (restrictedScopes.contains(target.getScopeType().name().toLowerCase(Locale.ROOT))) {
            return BoundaryDecision.deny("Regional restriction: " + sourceRegion +
                    " cannot access " + target.getScopeType());
        }

        // Determine consent requirements
        boolean requiresConsent = classification.getSensitivityLevel()
                .compareTo(ClassificationDescriptor.SensitivityLevel.CONFIDENTIAL) >= 0;

        Set<String> requiredFeatures = requiresConsent ?
                Set.of("cross-scope.consent." + target.getScopeType().name().toLowerCase(Locale.ROOT)) :
                Set.of();

        return new BoundaryDecision(
                true, // allowed
                requiresConsent,
                true, // always audit cross-scope access
                requiredFeatures,
                Map.of(
                        "source_scope", source.toString(),
                        "target_scope", target.toString(),
                        "classification", classification.toString()
                )
        );
    }

    private boolean isResourceActionAllowed(ScopeDescriptor source, ScopeDescriptor target,
                                          String resource, String action,
                                          ClassificationDescriptor classification) {

        // Read-only access to patient records
        if (resource.equals("patient.records") && !action.equals("read")) {
            return false;
        }

        // Financial data requires audit trail
        if (resource.equals("trade.records") && !classification.hasComplianceTag("sebon")) {
            return false;
        }

        // Restricted data requires elevated permissions
        if (classification.getSensitivityLevel() == ClassificationDescriptor.SensitivityLevel.RESTRICTED
                && !action.equals("read")) {
            return false;
        }

        return true;
    }
}
