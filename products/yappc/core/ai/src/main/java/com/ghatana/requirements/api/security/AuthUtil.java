package com.ghatana.requirements.api.security;

import com.ghatana.platform.security.model.User;
import io.activej.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for extracting authentication information from HTTP requests.
 *
 * <p><b>Purpose</b><br>
 * Provides helper methods for controllers to access authenticated user information
 * from HTTP requests that have passed through the AuthenticationFilter.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * public Promise<HttpResponse> getWorkspaces(HttpRequest request) {
 *     User user = AuthUtil.getCurrentUser(request);
 *     return workspaceService.listWorkspaces(user);
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Authentication utility for controllers
 * @doc.layer product
 * @doc.pattern Utility
 * @since 1.0.0
 */
public final class AuthUtil {
    private static final Logger logger = LoggerFactory.getLogger(AuthUtil.class);
    
    private AuthUtil() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Get the current authenticated user from the request.
     *
     * @param request HTTP request that has passed through authentication
     * @return authenticated user principal
     * @throws AuthenticationException if no user is attached to the request
     */
    public static User getCurrentUser(HttpRequest request) throws AuthenticationException {
        Object principalObj = request.getAttachment("userPrincipal");
        
        if (principalObj == null) {
            logger.error("No user principal found in request - authentication filter may not have been applied");
            throw new AuthenticationException("User not authenticated");
        }
        
        if (!(principalObj instanceof User principal)) {
            logger.error("Invalid principal type found in request: {}", principalObj.getClass().getName());
            throw new AuthenticationException("Invalid authentication state");
        }
        
        return principal;
    }
    
    /**
     * Get the current user ID from the request.
     *
     * @param request HTTP request that has passed through authentication
     * @return user ID
     * @throws AuthenticationException if no user is attached to the request
     */
    public static String getCurrentUserId(HttpRequest request) throws AuthenticationException {
        return getCurrentUser(request).getUserId();
    }
    
    /**
     * Get the current username from the request.
     *
     * @param request HTTP request that has passed through authentication
     * @return username
     * @throws AuthenticationException if no user is attached to the request
     */
    public static String getCurrentUsername(HttpRequest request) throws AuthenticationException {
        return getCurrentUser(request).getUsername();
    }
    
    /**
     * Get the current tenant ID from the request.
     *
     * @param request HTTP request that has passed through authentication
     * @return tenant ID
     * @throws AuthenticationException if no user is attached to the request
     */
    public static String getCurrentTenantId(HttpRequest request) throws AuthenticationException {
        // Tenant ID is not directly available in core UserPrincipal
        // Extract from email domain or use a default
        String email = getCurrentUser(request).getEmail();
        if (email != null && email.contains("@")) {
            String domain = email.substring(email.indexOf("@") + 1);
            if (domain.endsWith(".local")) {
                return domain.replace(".local", "");
            }
        }
        return "default";
    }
    
    /**
     * Check if a user is authenticated for the request.
     *
     * @param request HTTP request to check
     * @return true if user is authenticated, false otherwise
     */
    public static boolean isAuthenticated(HttpRequest request) {
        Object principalObj = request.getAttachment("userPrincipal");
        return principalObj instanceof User;
    }
    
    /**
     * Exception thrown when authentication is required but not available.
     */
    public static class AuthenticationException extends Exception {
        public AuthenticationException(String message) {
            super(message);
        }
        
        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
