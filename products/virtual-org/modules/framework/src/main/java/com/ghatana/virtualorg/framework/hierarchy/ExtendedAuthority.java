package com.ghatana.virtualorg.framework.hierarchy;

import com.ghatana.virtualorg.framework.norm.Norm;

import java.util.*;

/**
 * Enhanced authority model supporting obligations, prohibitions, and permissions.
 *
 * <p><b>Purpose</b><br>
 * ExtendedAuthority goes beyond simple decision permissions to include
 * the full normative model: what agents MUST do, CAN do, and MUST NOT do.
 * This enables richer governance and accountability.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ExtendedAuthority auth = ExtendedAuthority.builder()
 *     .permit("code_review")
 *     .permit("merge_pr")
 *     .obligate("respond_to_p1", Duration.ofMinutes(15))
 *     .prohibit("deploy_friday")
 *     .build();
 *
 * if (auth.isObligated("respond_to_p1")) {
 *     // Must track and enforce this obligation
 * }
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Extended authority with normative model
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record ExtendedAuthority(
        Set<String> permissions,
        Set<String> obligations,
        Set<String> prohibitions,
        Map<String, Norm> normDetails
) {

    /**
     * Compact constructor with defensive copies.
     */
    public ExtendedAuthority {
        permissions = permissions != null ? Set.copyOf(permissions) : Set.of();
        obligations = obligations != null ? Set.copyOf(obligations) : Set.of();
        prohibitions = prohibitions != null ? Set.copyOf(prohibitions) : Set.of();
        normDetails = normDetails != null ? Map.copyOf(normDetails) : Map.of();
    }

    /**
     * Creates a builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates from a simple Authority (backward compatible).
     */
    public static ExtendedAuthority fromAuthority(Authority authority) {
        return new ExtendedAuthority(
                authority.decisions(),
                Set.of(),
                Set.of(),
                Map.of()
        );
    }

    /**
     * Checks if this authority permits a decision.
     */
    public boolean isPermitted(String decision) {
        return permissions.contains(decision);
    }

    /**
     * Checks if this authority obligates an action.
     */
    public boolean isObligated(String action) {
        return obligations.contains(action);
    }

    /**
     * Checks if this authority prohibits an action.
     */
    public boolean isProhibited(String action) {
        return prohibitions.contains(action);
    }

    /**
     * Gets the norm details for an obligation/prohibition.
     */
    public Optional<Norm> getNorm(String action) {
        return Optional.ofNullable(normDetails.get(action));
    }

    /**
     * Converts back to simple Authority (for backward compatibility).
     */
    public Authority toSimpleAuthority() {
        return new Authority(permissions);
    }

    /**
     * Checks if empty.
     */
    public boolean isEmpty() {
        return permissions.isEmpty() && obligations.isEmpty() && prohibitions.isEmpty();
    }

    /**
     * Merges with another authority.
     */
    public ExtendedAuthority merge(ExtendedAuthority other) {
        Set<String> mergedPermissions = new HashSet<>(permissions);
        mergedPermissions.addAll(other.permissions);

        Set<String> mergedObligations = new HashSet<>(obligations);
        mergedObligations.addAll(other.obligations);

        Set<String> mergedProhibitions = new HashSet<>(prohibitions);
        mergedProhibitions.addAll(other.prohibitions);

        Map<String, Norm> mergedNorms = new HashMap<>(normDetails);
        mergedNorms.putAll(other.normDetails);

        return new ExtendedAuthority(
                mergedPermissions,
                mergedObligations,
                mergedProhibitions,
                mergedNorms
        );
    }

    /**
     * Builder for ExtendedAuthority.
     */
    public static class Builder {
        private final Set<String> permissions = new HashSet<>();
        private final Set<String> obligations = new HashSet<>();
        private final Set<String> prohibitions = new HashSet<>();
        private final Map<String, Norm> normDetails = new HashMap<>();

        /**
         * Adds a permission.
         */
        public Builder permit(String decision) {
            permissions.add(decision);
            return this;
        }

        /**
         * Adds permissions.
         */
        public Builder permit(String... decisions) {
            permissions.addAll(Arrays.asList(decisions));
            return this;
        }

        /**
         * Adds an obligation.
         */
        public Builder obligate(String action) {
            obligations.add(action);
            return this;
        }

        /**
         * Adds an obligation with norm details.
         */
        public Builder obligate(Norm norm) {
            obligations.add(norm.action());
            normDetails.put(norm.action(), norm);
            return this;
        }

        /**
         * Adds a prohibition.
         */
        public Builder prohibit(String action) {
            prohibitions.add(action);
            return this;
        }

        /**
         * Adds a prohibition with norm details.
         */
        public Builder prohibit(Norm norm) {
            prohibitions.add(norm.action());
            normDetails.put(norm.action(), norm);
            return this;
        }

        /**
         * Builds the ExtendedAuthority.
         */
        public ExtendedAuthority build() {
            return new ExtendedAuthority(permissions, obligations, prohibitions, normDetails);
        }
    }
}
