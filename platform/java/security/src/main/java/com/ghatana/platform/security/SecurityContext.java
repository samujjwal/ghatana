package com.ghatana.platform.security;

import java.util.Optional;
import java.util.Set;
import java.util.Collections;

/**
 * Security context interface for ActiveJ-based applications.
 *
 * @doc.type interface
 * @doc.purpose Holds current user and tenant security information
 * @doc.layer core
 * @doc.pattern Context
 */
public interface SecurityContext {
    
    /**
     * Gets the current user ID.
     *
     * @return Optional containing user ID if authenticated
     */
    Optional<String> getUserId();
    
    /**
     * Gets the current tenant ID.
     *
     * @return Optional containing tenant ID if available
     */
    Optional<String> getTenantId();
    
    /**
     * Gets the user's roles.
     *
     * @return Set of role names
     */
    Set<String> getRoles();
    
    /**
     * Gets the user's permissions.
     *
     * @return Set of permission names
     */
    Set<String> getPermissions();
    
    /**
     * Checks if the user is authenticated.
     *
     * @return true if authenticated
     */
    boolean isAuthenticated();
    
    /**
     * Default implementation.
     */
    class Default implements SecurityContext {
        private final String userId;
        private final String tenantId;
        private final Set<String> roles;
        private final Set<String> permissions;
        
        public Default(String userId, String tenantId, Set<String> roles, Set<String> permissions) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.roles = roles != null ? Set.copyOf(roles) : Collections.emptySet();
            this.permissions = permissions != null ? Set.copyOf(permissions) : Collections.emptySet();
        }
        
        @Override
        public Optional<String> getUserId() {
            return Optional.ofNullable(userId);
        }
        
        @Override
        public Optional<String> getTenantId() {
            return Optional.ofNullable(tenantId);
        }
        
        @Override
        public Set<String> getRoles() {
            return roles;
        }
        
        @Override
        public Set<String> getPermissions() {
            return permissions;
        }
        
        @Override
        public boolean isAuthenticated() {
            return userId != null;
        }
    }
}
