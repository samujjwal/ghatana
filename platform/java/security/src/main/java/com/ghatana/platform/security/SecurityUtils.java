package com.ghatana.platform.security;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.annotation.RequiresPermission;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.rbac.PolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Utility class for common security operations.
 * Migrated to use PolicyService for modern policy-based access control.
 
 *
 * @doc.type class
 * @doc.purpose Security utils
 * @doc.layer core
 * @doc.pattern Component
*/
public class SecurityUtils {
    private static final Logger logger = LoggerFactory.getLogger(SecurityUtils.class);
    
    // Predefined permission constants
    public static final String PERMISSION_ADMIN = "*:*:*";
    public static final String PERMISSION_EVENT_READ = "event:read:all";
    public static final String PERMISSION_EVENT_WRITE = "event:write:all";
    public static final String PERMISSION_USER_READ = "user:read:all";
    public static final String PERMISSION_USER_WRITE = "user:write:all";
    
    private SecurityUtils() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Checks if the current user has the required permission using PolicyService.
     *
     * @param permission The permission to check
     * @param user The user to check permissions for
     * @param policyService The PolicyService to use for authorization
     * @param resource The resource for the permission check
     * @return true if the user has the permission, false otherwise
     */
    public static boolean hasPermission(String permission, User user, PolicyService policyService, String resource) {
        if (user == null || policyService == null) {
            logger.warn("Permission check failed: user or policyService is null");
            return false;
        }
        
        // Create a Principal from the user for PolicyService
        // Note: Using "default" as tenantId; in production, tenantId should come from request context
        Principal principal = new Principal("default", user.getRoles().stream().toList());
        
        // Use PolicyService for authorization
        return policyService.isAuthorized(principal, permission, resource);
    }
    

    
    /**
     * Checks if a user permission matches a required permission.
     * Supports wildcard matching.
     */
    private static boolean matchesPermission(String userPermission, String requiredPermission) {
        if (userPermission.equals(PERMISSION_ADMIN)) {
            return true;
        }
        
        String[] userParts = userPermission.split(":");
        String[] requiredParts = requiredPermission.split(":");
        
        if (userParts.length != 3 || requiredParts.length != 3) {
            return false;
        }
        
        // Check each part of the permission
        for (int i = 0; i < 3; i++) {
            if (!userParts[i].equals("*") && !userParts[i].equals(requiredParts[i])) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Extracts the required permission from a method's annotations.
     *
     * @param methodAnnotations The method to check for permission annotations
     * @return The required permission, or null if no permission is required
     */
    public static String getRequiredPermission(Annotation[] methodAnnotations) {
        if (methodAnnotations == null) {
            return null;
        }
        
        for (Annotation annotation : methodAnnotations) {
            if (annotation instanceof RequiresPermission) {
                return ((RequiresPermission) annotation).value();
            }
        }
        
        return null;
    }
    

    
    /**
     * Extracts the bearer token from an authorization header.
     *
     * @param authHeader The Authorization header value
     * @return The token if present, null otherwise
     */
    public static String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }
    
    /**
     * Validates if a password meets the security requirements.
     *
     * @param password The password to validate
     * @return A map with validation results, where the key is the validation rule
     *         and the value is a boolean indicating if the rule passed
     */
    public static Map<String, Boolean> validatePassword(String password) {
        // Implementation would check password strength requirements
        // This is a simplified example
        return Map.of(
            "minLength", password != null && password.length() >= 8,
            "hasUppercase", password != null && password.matches(".*[A-Z].*"),
            "hasLowercase", password != null && password.matches(".*[a-z].*"),
            "hasNumber", password != null && password.matches(".*\\d.*"),
            "hasSpecialChar", password != null && password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")
        );
    }
}
