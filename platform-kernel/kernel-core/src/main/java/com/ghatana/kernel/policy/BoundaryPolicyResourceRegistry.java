package com.ghatana.kernel.policy;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Registry of boundary-policy resource namespaces allowed for a product policy pack.
 *
 * <p>The kernel permits universal wildcard {@code **} for the default-deny sentinel,
 * while product-owned resource roots must be declared explicitly in the product
 * domain-pack manifest before they are used in runtime rules.</p>
 *
 * @doc.type class
 * @doc.purpose Declares the allowed boundary-policy resource namespaces for a product pack
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public final class BoundaryPolicyResourceRegistry {

    private final Set<String> allowedResourceRoots;
    private final boolean allowAny;

    private BoundaryPolicyResourceRegistry(Set<String> allowedResourceRoots, boolean allowAny) {
        this.allowedResourceRoots = Set.copyOf(allowedResourceRoots);
        this.allowAny = allowAny;
    }

    public static BoundaryPolicyResourceRegistry allowAny() {
        return new BoundaryPolicyResourceRegistry(Set.of(), true);
    }

    public static BoundaryPolicyResourceRegistry empty() {
        return new BoundaryPolicyResourceRegistry(Set.of(), false);
    }

    public static BoundaryPolicyResourceRegistry ofDeclaredResources(Set<String> declaredResourceRoots) {
        Objects.requireNonNull(declaredResourceRoots, "declaredResourceRoots cannot be null");

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        declaredResourceRoots.stream()
                .map(resource -> Objects.requireNonNull(resource, "declared resource cannot be null").trim())
                .filter(resource -> !resource.isEmpty())
                .forEach(normalized::add);
        return new BoundaryPolicyResourceRegistry(normalized, false);
    }

    public boolean isAllowed(String resourcePattern) {
        Objects.requireNonNull(resourcePattern, "resourcePattern cannot be null");

        if (allowAny) {
            return true;
        }

        if ("**".equals(resourcePattern)) {
            return true;
        }

        String normalized = normalizePattern(resourcePattern);
        return allowedResourceRoots.contains(normalized);
    }

    public Set<String> getAllowedResourceRoots() {
        return allowedResourceRoots;
    }

    private static String normalizePattern(String resourcePattern) {
        String normalized = resourcePattern.trim();
        if (normalized.endsWith("/**")) {
            return normalized.substring(0, normalized.length() - 3);
        }
        if (normalized.endsWith("/*")) {
            return normalized.substring(0, normalized.length() - 2);
        }
        return normalized;
    }
}
