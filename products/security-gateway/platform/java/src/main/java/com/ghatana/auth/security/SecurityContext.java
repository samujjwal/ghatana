package com.ghatana.auth.security;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserPrincipal;

import java.util.Optional;
import java.util.Set;

/**
 * Request-scoped security context carrying authenticated principal and tenant.
 *
 * @doc.type interface
 * @doc.purpose Security context for request-scoped auth state
 * @doc.layer product
 * @doc.pattern Context
 */
public interface SecurityContext {

    Optional<UserPrincipal> getUserPrincipal();

    Optional<TenantId> getTenantId();

    Set<String> getRoles();

    Set<String> getPermissions();

    boolean isAuthenticated();

    boolean hasRole(String role);

    boolean hasPermission(String permission);

    boolean hasAnyPermission(String... requiredPermissions);

    boolean hasAllPermissions(String... requiredPermissions);

    /**
     * Creates an authenticated SecurityContext.
     */
    static SecurityContext of(UserPrincipal userPrincipal, TenantId tenantId) {
        return new DefaultSecurityContext(userPrincipal, tenantId);
    }

    /**
     * Returns an empty (unauthenticated) SecurityContext.
     */
    static SecurityContext empty() {
        return new DefaultSecurityContext(null, null);
    }
}
