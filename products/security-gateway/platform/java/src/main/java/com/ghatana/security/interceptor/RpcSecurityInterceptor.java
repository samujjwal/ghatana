package com.ghatana.security.interceptor;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.security.audit.SecurityAuditLogger;
import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.ghatana.platform.security.rbac.PolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Handles security for RPC requests, including authentication and authorization.
 * This interceptor works with the SecurityRpcHandler to secure RPC endpoints.
 * Migrated to use PolicyService for modern policy-based access control.
 
 *
 * @doc.type class
 * @doc.purpose Rpc security interceptor
 * @doc.layer core
 * @doc.pattern Interceptor
*/
public class RpcSecurityInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(RpcSecurityInterceptor.class);
    
    private final PolicyService policyService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityAuditLogger auditLogger;
    private final ExecutorService executorService;

    public RpcSecurityInterceptor(
            PolicyService policyService,
            JwtTokenProvider jwtTokenProvider,
            SecurityAuditLogger auditLogger,
            ExecutorService executorService) {
        this.policyService = policyService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditLogger = auditLogger;
        this.executorService = executorService;
    }

    /**
     * Authenticates a user using a JWT token.
     *
     * @param token the JWT token to validate
     * @return the user ID if authenticated, null otherwise
     */
    public String authenticate(String token) {
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String userId = jwtTokenProvider.getUserIdFromToken(token).orElse(null);
            if (userId != null && !userId.isEmpty()) {
                auditLogger.logAuthenticationSuccess(userId, "JWT");
                return userId;
            }
        }
        auditLogger.logAuthenticationFailure(null, "JWT", "Invalid or expired token");
        return null;
    }

    /**
     * Authorizes a user for a specific permission.
     *
     * @param userId the user ID to authorize
     * @param permission the permission to check
     * @param resource the resource being accessed
     * @return true if authorized, false otherwise
     */
    public boolean authorize(String userId, String permission, String resource) {
        if (userId == null || userId.isEmpty()) {
            auditLogger.logAccessDenied(null, resource, "User not authenticated");
            return false;
        }

        try {
            Set<String> roles = getRoles(userId);
            
            // Create a Principal for PolicyService (using "default" as tenantId)
            Principal principal = new Principal("default", roles.stream().toList());
            
            boolean authorized = policyService.isAuthorized(principal, permission, resource);
            
            if (authorized) {
                auditLogger.logAccessGranted(userId, permission, resource);
            } else {
                auditLogger.logAccessDenied(userId, resource, "Insufficient permissions");
            }
            
            return authorized;
        } catch (Exception e) {
            logger.error("Authorization error for user {}: {}", userId, e.getMessage(), e);
            auditLogger.logAccessDenied(userId, resource, "Authorization error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the user's roles from their JWT token.
     *
     * @param userId the user ID
     * @return a set of role names
     */
    public Set<String> getUserRoles(String userId) {
        try {
            return new HashSet<>(jwtTokenProvider.getRolesFromToken(userId));
        } catch (Exception e) {
            logger.error("Error getting roles for user {}: {}", userId, e.getMessage(), e);
            return Set.of();
        }
    }

    public Set<String> getRoles(String userId) {
        try {
            return new HashSet<>(jwtTokenProvider.getRolesFromToken(userId));
        } catch (Exception e) {
            logger.error("Error getting roles for user {}: {}", userId, e.getMessage(), e);
            return Set.of();
        }
    }
}
