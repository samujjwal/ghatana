package com.ghatana.virtualorg.security;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Represents an authenticated principal (agent or user) in the system.
 *
 * <p><b>Purpose</b><br>
 * Value object representing an authenticated entity with identity, type, roles,
 * and attributes for access control.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Principal agent = new Principal(
 *     "agent-123",
 *     PrincipalType.AGENT,
 *     Set.of("senior-engineer", "code-reviewer"),
 *     Map.of("team", "backend", "clearance", "high")
 * );
 * 
 * boolean hasRole = agent.hasRole("code-reviewer");
 * String team = agent.getAttribute("team");
 * }</pre>
 *
 * <p><b>Structure</b><br>
 * - **id**: Unique identifier (non-blank)
 * - **type**: Principal type (AGENT, USER, SERVICE)
 * - **roles**: Set of roles for RBAC (at least one required)
 * - **attributes**: Key-value pairs for ABAC (fine-grained access control)
 *
 * <p><b>Validation</b><br>
 * Canonical constructor validates:
 * - id: non-blank
 * - roles: non-empty (at least one role required)
 *
 * @param id Unique principal identifier
 * @param type Principal type (agent, user, service)
 * @param roles Set of roles for role-based access control
 * @param attributes Key-value attributes for attribute-based access control
 * @doc.type record
 * @doc.purpose Principal value object for authenticated entity representation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record Principal(
    @NotNull String id,
    @NotNull PrincipalType type,
    @NotNull Set<String> roles,
    @NotNull java.util.Map<String, String> attributes
) {
    public Principal {
        if (id.isBlank()) {
            throw new IllegalArgumentException("Principal ID cannot be blank");
        }
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Principal must have at least one role");
        }
    }

    /**
     * Check if this principal has the specified role
     */
    public boolean hasRole(@NotNull String role) {
        return roles.contains(role);
    }

    /**
     * Check if this principal has any of the specified roles
     */
    public boolean hasAnyRole(@NotNull Set<String> requiredRoles) {
        return requiredRoles.stream().anyMatch(roles::contains);
    }

    /**
     * Check if this principal has all of the specified roles
     */
    public boolean hasAllRoles(@NotNull Set<String> requiredRoles) {
        return roles.containsAll(requiredRoles);
    }

    /**
     * Get an attribute value
     */
    @NotNull
    public String getAttribute(@NotNull String key, @NotNull String defaultValue) {
        return attributes.getOrDefault(key, defaultValue);
    }

    /**
     * Check if principal is an agent
     */
    public boolean isAgent() {
        return type == PrincipalType.AGENT;
    }

    /**
     * Check if principal is a user
     */
    public boolean isUser() {
        return type == PrincipalType.USER;
    }

    /**
     * Check if principal is a service
     */
    public boolean isService() {
        return type == PrincipalType.SERVICE;
    }
}
