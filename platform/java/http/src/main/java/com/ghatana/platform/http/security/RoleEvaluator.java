package com.ghatana.platform.http.security;

import java.util.Map;
import java.util.Objects;

/**
 * Kernel-owned fail-closed role evaluator for route entitlement filtering.
 * <p>
 * Enforces that unknown or malformed roles fail closed (deny access) instead
 * of granting default access. This prevents role spoofing and ensures that
 * only explicitly recognized roles can access routes, actions, and cards.
 * </p>
 *
 * @doc.type interface
 * @doc.purpose Evaluates role hierarchy and fails closed for unknown roles
 * @doc.layer kernel
 * @doc.pattern Evaluator
 */
public interface RoleEvaluator {

    /**
     * Checks if the current role meets or exceeds the minimum required role.
     *
     * @param currentRole  the current user's role; must not be null
     * @param minimumRole  the minimum required role; must not be null
     * @param roleOrder     the role hierarchy mapping from role name to numeric order
     * @return true if current role meets or exceeds minimum role
     * @throws IllegalArgumentException if either role is null or not recognized in roleOrder
     */
    boolean isRoleSufficient(String currentRole, String minimumRole, Map<String, Integer> roleOrder);

    /**
     * Gets the numeric order for a role.
     *
     * @param role     the role to look up
     * @param roleOrder the role hierarchy mapping
     * @return the numeric order value
     * @throws IllegalArgumentException if role is null, blank, or not recognized in roleOrder
     */
    int getRoleOrder(String role, Map<String, Integer> roleOrder);

    /**
     * Default implementation that fails closed for unknown roles.
     */
    final class FailClosed implements RoleEvaluator {

        @Override
        public boolean isRoleSufficient(String currentRole, String minimumRole, Map<String, Integer> roleOrder) {
            Objects.requireNonNull(currentRole, "currentRole must not be null");
            Objects.requireNonNull(minimumRole, "minimumRole must not be null");
            Objects.requireNonNull(roleOrder, "roleOrder must not be null");

            int currentOrder = getRoleOrder(currentRole, roleOrder);
            int minimumOrder = getRoleOrder(minimumRole, roleOrder);
            return currentOrder >= minimumOrder;
        }

        @Override
        public int getRoleOrder(String role, Map<String, Integer> roleOrder) {
            Objects.requireNonNull(role, "role must not be null");
            Objects.requireNonNull(roleOrder, "roleOrder must not be null");

            if (role.isBlank()) {
                throw new IllegalArgumentException("role must not be blank");
            }

            Integer order = roleOrder.get(role);
            if (order == null) {
                throw new IllegalArgumentException(
                    "Role '" + role + "' is not recognized in roleOrder. " +
                    "Unknown roles fail closed to prevent privilege escalation."
                );
            }
            return order;
        }
    }
}
