package com.ghatana.kernel.policy;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Registry of policy action tokens allowed for a product boundary policy pack.
 *
 * <p>The kernel owns a small canonical action vocabulary shared across products.
 * Product-specific verbs must be declared explicitly in the product domain-pack
 * manifest before they are used in runtime boundary rules.</p>
 *
 * @doc.type class
 * @doc.purpose Declares the allowed boundary-policy action vocabulary for a product pack
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public final class BoundaryPolicyActionRegistry {

    private static final Set<String> CANONICAL_ACTIONS = Set.of(
        "read",
        "write",
        "delete"
    );

    private final Set<String> allowedActions;

    private BoundaryPolicyActionRegistry(Set<String> allowedActions) {
        this.allowedActions = Set.copyOf(allowedActions);
    }

    public static BoundaryPolicyActionRegistry canonicalOnly() {
        return new BoundaryPolicyActionRegistry(CANONICAL_ACTIONS);
    }

    public static BoundaryPolicyActionRegistry ofDeclaredActions(Set<String> declaredActions) {
        Objects.requireNonNull(declaredActions, "declaredActions cannot be null");

        LinkedHashSet<String> combined = new LinkedHashSet<>(CANONICAL_ACTIONS);
        declaredActions.stream()
            .map(action -> Objects.requireNonNull(action, "declared action cannot be null").trim())
            .filter(action -> !action.isEmpty())
            .forEach(combined::add);
        return new BoundaryPolicyActionRegistry(combined);
    }

    public boolean isAllowed(String action) {
        return "*".equals(action) || allowedActions.contains(action);
    }

    public Set<String> getAllowedActions() {
        return allowedActions;
    }
}
