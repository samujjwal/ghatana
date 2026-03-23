package com.ghatana.agent.registry.domain.impl;

import com.ghatana.aep.domain.agent.registry.SecurityContext;

import java.util.Set;

/**
 * Default implementation of SecurityContext for agent execution security.
 */
public record DefaultSecurityContext(
        String principal,
        Set<String> roles,
        Set<String> permissions,
        String token,
        boolean authenticated
) implements SecurityContext {
    
    @Override
    public String getPrincipal() {
        return principal;
    }
    
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
    
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
    
    /**
     * Create a basic security context for testing or system operations
     */
    public static SecurityContext system() {
        return new DefaultSecurityContext(
                "system",
                Set.of("SYSTEM"),
                Set.of("*"),
                null,
                true
        );
    }
    
    /**
     * Create an unauthenticated security context
     */
    public static SecurityContext anonymous() {
        return new DefaultSecurityContext(
                "anonymous",
                Set.of(),
                Set.of(),
                null,
                false
        );
    }
}