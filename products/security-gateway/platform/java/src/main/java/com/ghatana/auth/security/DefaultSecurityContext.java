package com.ghatana.auth.security;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserPrincipal;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Default immutable implementation of SecurityContext.
 *
 * <p><b>Purpose</b><br>
 * Holds authenticated user principal and tenant information for request.
 * Immutable value object that safely represents security state at request time.
 *
 * <p><b>Design</b><br>
 * - Stores Optional references to handle both authenticated and unauthenticated cases
 * - All queries delegate to UserPrincipal for role/permission checks
 * - Thread-safe: immutable after construction
 *
 * @doc.type class
 * @doc.purpose Default SecurityContext implementation
 * @doc.layer product
 * @doc.pattern Value Object
 */
final class DefaultSecurityContext implements SecurityContext {

    private final UserPrincipal userPrincipal;
    private final TenantId tenantId;

    /**
     * Creates a SecurityContext with user principal and tenant.
     *
     * @param userPrincipal user principal (null for unauthenticated)
     * @param tenantId tenant ID (null for unauthenticated)
     */
    DefaultSecurityContext(UserPrincipal userPrincipal, TenantId tenantId) {
        this.userPrincipal = userPrincipal;
        this.tenantId = tenantId;
    }

    @Override
    public Optional<UserPrincipal> getUserPrincipal() {
        return Optional.ofNullable(userPrincipal);
    }

    @Override
    public Optional<TenantId> getTenantId() {
        return Optional.ofNullable(tenantId);
    }

    @Override
    public Set<String> getRoles() {
        return userPrincipal != null
            ? userPrincipal.getRoles()
            : Set.of();
    }

    @Override
    public Set<String> getPermissions() {
        return userPrincipal != null
            ? userPrincipal.getPermissions()
            : Set.of();
    }

    @Override
    public boolean isAuthenticated() {
        return userPrincipal != null && tenantId != null;
    }

    @Override
    public boolean hasRole(String role) {
        if (userPrincipal == null) {
            return false;
        }
        return userPrincipal.hasRole(role);
    }

    @Override
    public boolean hasPermission(String permission) {
        if (userPrincipal == null) {
            return false;
        }
        return userPrincipal.hasPermission(permission);
    }

    @Override
    public boolean hasAnyPermission(String... requiredPermissions) {
        if (userPrincipal == null || requiredPermissions == null || requiredPermissions.length == 0) {
            return false;
        }
        Set<String> perms = userPrincipal.getPermissions();
        for (String p : requiredPermissions) {
            if (perms.contains(p)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasAllPermissions(String... requiredPermissions) {
        if (userPrincipal == null || requiredPermissions == null || requiredPermissions.length == 0) {
            return false;
        }
        Set<String> perms = userPrincipal.getPermissions();
        for (String p : requiredPermissions) {
            if (!perms.contains(p)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultSecurityContext)) return false;
        DefaultSecurityContext that = (DefaultSecurityContext) o;
        return Objects.equals(userPrincipal, that.userPrincipal) &&
                Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userPrincipal, tenantId);
    }

    @Override
    public String toString() {
        return "SecurityContext{" +
                "user=" + (userPrincipal != null ? userPrincipal.getUserId() : "anonymous") +
                ", tenant=" + (tenantId != null ? tenantId.value() : "none") +
                ", authenticated=" + isAuthenticated() +
                '}';
    }
}
