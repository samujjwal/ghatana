package com.ghatana.kernel.security;

import java.util.Map;
import java.util.Set;

/**
 * Security context containing authentication and authorization information.
 *
 * <p>Immutable security context that travels with requests through the kernel.
 * Contains tenant, user, roles, and security attributes.</p>
 *
 * @doc.type interface
 * @doc.purpose Security context for authenticated requests
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface SecurityContext {

    /**
     * Gets the tenant identifier.
     *
     * @return the tenant ID
     */
    String getTenantId();

    /**
     * Gets the user identifier.
     *
     * @return the user ID
     */
    String getUserId();

    /**
     * Gets the user roles.
     *
     * @return set of role names
     */
    Set<String> getRoles();

    /**
     * Gets security attributes.
     *
     * @return map of security attributes
     */
    Map<String, Object> getAttributes();

    /**
     * Checks if user has a specific role.
     *
     * @param role the role name
     * @return true if user has the role
     */
    boolean hasRole(String role);

    /**
     * Checks if user has a specific permission.
     *
     * @param permission the permission string
     * @return true if user has the permission
     */
    boolean hasPermission(String permission);

    /**
     * Gets a security attribute.
     *
     * @param key the attribute key
     * @return the attribute value or null
     */
    Object getAttribute(String key);

    /**
     * Gets the session ID.
     *
     * @return the session ID
     */
    String getSessionId();

    /**
     * Checks if the context is authenticated.
     *
     * @return true if authenticated
     */
    boolean isAuthenticated();

    /**
     * Gets the authentication timestamp.
     *
     * @return timestamp in milliseconds
     */
    long getAuthenticationTime();
}
