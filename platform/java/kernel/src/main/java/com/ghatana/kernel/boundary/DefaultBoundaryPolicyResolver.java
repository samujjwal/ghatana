package com.ghatana.kernel.boundary;

import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.policy.ClassificationDescriptor.SensitivityLevel;
import com.ghatana.kernel.scope.ScopeDescriptor;
import com.ghatana.kernel.scope.ScopeType;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Default boundary policy resolver using classification metadata.
 *
 * <p>Replaces the hardcoded product allowlists in {@link ProductBoundaryEnforcer}
 * with a classification-driven approach:</p>
 * <ul>
 *   <li>Same-scope access is always allowed</li>
 *   <li>Cross-scope access is governed by resource action rules and sensitivity</li>
 *   <li>Restricted data requires explicit consent and mandatory audit</li>
 *   <li>Compliance tags trigger additional constraints (e.g., region restrictions)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default boundary policy resolver based on classification metadata
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public class DefaultBoundaryPolicyResolver implements BoundaryPolicyResolver {

    private final Map<String, Set<String>> resourceActionRules;

    /**
     * Creates a resolver with given resource-action rules.
     *
     * @param resourceActionRules maps resource patterns to allowed actions
     */
    public DefaultBoundaryPolicyResolver(Map<String, Set<String>> resourceActionRules) {
        this.resourceActionRules = Objects.requireNonNull(resourceActionRules);
    }

    /**
     * Creates a resolver with standard resource-action rules.
     */
    public static DefaultBoundaryPolicyResolver withStandardRules() {
        return new DefaultBoundaryPolicyResolver(Map.of(
                "patient.records", Set.of("read"),
                "trade.records", Set.of("read", "write"),
                "billing", Set.of("read", "write", "execute")
        ));
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

        // Same scope always allowed
        if (source.equals(target)) {
            return BoundaryDecision.allow(false);
        }

        // Check resource-action rules
        Set<String> allowedActions = resourceActionRules.getOrDefault(resource, Set.of("read"));
        if (!allowedActions.contains(action)) {
            return BoundaryDecision.deny(
                    String.format("Action '%s' not allowed on resource '%s'", action, resource));
        }

        // Restricted data requires consent and audit
        SensitivityLevel sensitivity = classification.getSensitivityLevel();
        if (sensitivity == SensitivityLevel.RESTRICTED) {
            return BoundaryDecision.allowWithConsent(true);
        }

        // Confidential data requires audit
        if (sensitivity == SensitivityLevel.CONFIDENTIAL) {
            return BoundaryDecision.allow(true);
        }

        // Check compliance-tag restricted regions
        if (classification.hasComplianceTag("data-residency-restricted")) {
            return BoundaryDecision.deny("Cross-scope access blocked by data residency policy");
        }

        return BoundaryDecision.allow(false);
    }
}
