/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 *
 * This source code and the accompanying materials are the confidential
 * and proprietary information of Ghatana Inc. ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Ghatana Inc.
 *
 * Unauthorized copying of this file, via any medium, is strictly prohibited.
 * Proprietary and confidential.
 */
package com.ghatana.security.govern;

import com.ghatana.contracts.common.v1.AuditPolicyProto;
import com.ghatana.contracts.common.v1.CompatibilityPolicyProto;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.rbac.PolicyService;
import com.ghatana.platform.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * IAM Policy Enforcer that validates access control based on PolicyProto enforcement rules.
 * Provides centralized policy enforcement for pattern learning endpoints with comprehensive
 * governance controls including RBAC, audit logging, and policy validation.
 * Migrated to use PolicyService for modern policy-based access control.
 
 *
 * @doc.type class
 * @doc.purpose Iam policy enforcer
 * @doc.layer core
 * @doc.pattern Component
*/
public class IamPolicyEnforcer {
    private static final Logger logger = LoggerFactory.getLogger(IamPolicyEnforcer.class);
    
    // Pattern learning permission scopes
    public static final String PATTERNS_RECOMMEND_PERMISSION = "patterns:recommend:*";
    public static final String PATTERNS_EVALUATE_PERMISSION = "patterns:evaluate:*";
    
    private final PolicyService policyService;
    private final AuditPolicyProto auditPolicy;
    
    /**
     * Creates a new IAM Policy Enforcer with the specified PolicyService and audit policy.
     *
     * @param policyService the PolicyService for permission checking
     * @param auditPolicy the audit policy configuration
     */
    public IamPolicyEnforcer(PolicyService policyService, AuditPolicyProto auditPolicy) {
        this.policyService = policyService;
        this.auditPolicy = auditPolicy;
        logger.info("IamPolicyEnforcer initialized with audit policy enabled: {}", auditPolicy.getEnabled());
    }
    
    /**
     * Enforces policy for pattern recommendation requests.
     * Validates that the user has the required patterns.recommend permission.
     *
     * @param user the authenticated user making the request
     * @param tenantId the tenant ID for multi-tenant access control
     * @param resourceId the specific resource being accessed
     * @return PolicyEnforcementResult indicating whether access is granted
     */
    public PolicyEnforcementResult enforcePatternRecommendPolicy(User user, String tenantId, String resourceId) {
        return enforcePolicy(user, tenantId, resourceId, PATTERNS_RECOMMEND_PERMISSION, "PATTERN_RECOMMEND");
    }
    
    /**
     * Enforces policy for pattern evaluation requests.
     * Validates that the user has the required patterns.evaluate permission.
     *
     * @param user the authenticated user making the request
     * @param tenantId the tenant ID for multi-tenant access control
     * @param resourceId the specific resource being accessed
     * @return PolicyEnforcementResult indicating whether access is granted
     */
    public PolicyEnforcementResult enforcePatternEvaluatePolicy(User user, String tenantId, String resourceId) {
        return enforcePolicy(user, tenantId, resourceId, PATTERNS_EVALUATE_PERMISSION, "PATTERN_EVALUATE");
    }
    
    /**
     * Core policy enforcement logic that validates permissions and generates audit events.
     */
    private PolicyEnforcementResult enforcePolicy(User user, String tenantId, String resourceId, 
                                                  String requiredPermission, String eventType) {
        try {
            // Validate user authentication
            if (user == null || !user.isAuthenticated()) {
                return createDeniedResult("User not authenticated", user, tenantId, resourceId, 
                                        requiredPermission, eventType);
            }
            
            // Check RBAC permissions using PolicyService
            Set<String> userRoles = user.getRoles();
            
            // Create a Principal for PolicyService (tenantId is passed as method parameter)
            Principal principal = new Principal(tenantId, userRoles.stream().toList());
            
            // Use resourceId as the resource for policy check
            boolean hasPermission = policyService.isAuthorized(principal, requiredPermission, resourceId);
            
            if (!hasPermission) {
                return createDeniedResult("Insufficient permissions", user, tenantId, resourceId, 
                                        requiredPermission, eventType);
            }
            
            // Policy enforcement successful
            AuditEvent auditEvent = createAuditEvent(eventType, user.getUserId(), resourceId, 
                                                   "GRANTED", Map.of(
                "tenantId", tenantId,
                "permission", requiredPermission,
                "userRoles", userRoles
            ));
            
            return PolicyEnforcementResult.builder()
                    .granted(true)
                    .reason("Access granted - user has required permission")
                    .auditEvent(auditEvent)
                    .build();
                    
        } catch (Exception e) {
            logger.error("Policy enforcement failed for user {} on resource {}", 
                        user != null ? user.getUserId() : "unknown", resourceId, e);
            return createDeniedResult("Policy enforcement error", user, tenantId, resourceId, 
                                    requiredPermission, eventType);
        }
    }
    
    /**
     * Creates a denied policy enforcement result with audit logging.
     */
    private PolicyEnforcementResult createDeniedResult(String reason, User user, String tenantId, 
                                                      String resourceId, String requiredPermission, 
                                                      String eventType) {
        String userId = user != null ? user.getUserId() : "anonymous";
        Set<String> userRoles = user != null ? user.getRoles() : Set.of();
        
        AuditEvent auditEvent = createAuditEvent(eventType, userId, resourceId, "DENIED", Map.of(
                "tenantId", tenantId,
                "permission", requiredPermission,
                "userRoles", userRoles,
                "reason", reason
        ));
        
        return PolicyEnforcementResult.builder()
                .granted(false)
                .reason(reason)
                .auditEvent(auditEvent)
                .build();
    }
    
    /**
     * Creates an audit event for policy enforcement actions.
     */
    private AuditEvent createAuditEvent(String eventType, String principal, String resource, 
                                       String status, Map<String, Object> details) {
        if (!auditPolicy.getEnabled()) {
            return null; // Audit logging disabled
        }
        
        AuditEvent.Builder builder = AuditEvent.builder()
                .eventType(eventType)
                .timestamp(Instant.now())
                .principal(principal)
                .resourceId(resource)
                .success("SUCCESS".equalsIgnoreCase(status));
        
        if (details != null) {
            builder.details(details);
        }
        builder.detail("action", "POLICY_ENFORCEMENT");
        if (status != null) {
            builder.detail("status", status);
        }
        
        return builder.build();
    }
    
    /**
     * Checks if a user has any of the specified permissions.
     * 
     * @param user the user to check permissions for
     * @param permissions the set of permissions to check (user needs at least one)
     * @return true if user has any of the specified permissions
     */
    public boolean hasAnyPermission(User user, Set<String> permissions) {
        if (user == null || !user.isAuthenticated() || permissions == null || permissions.isEmpty()) {
            return false;
        }
        
        Set<String> userRoles = user.getRoles();
        // Create a Principal and check if any permission is granted using PolicyService
        // Using "default" tenant ID and "*" as wildcard resource
        Principal principal = new Principal("default", userRoles.stream().toList());
        return permissions.stream()
                .anyMatch(permission -> policyService.isAuthorized(principal, permission, "*"));
    }
    
    /**
     * Validates policy compatibility for schema evolution scenarios.
     * Ensures that policy changes don't violate existing compatibility requirements.
     *
     * @param currentPolicy the current compatibility policy
     * @param proposedPolicy the proposed compatibility policy
     * @return true if the policy change is compatible
     */
    public boolean validatePolicyCompatibility(CompatibilityPolicyProto currentPolicy, 
                                             CompatibilityPolicyProto proposedPolicy) {
        // NONE policy allows any change
        if (currentPolicy == CompatibilityPolicyProto.NONE) {
            return true;
        }
        
        // FULL compatibility requires both policies to be the same or more restrictive
        if (currentPolicy == CompatibilityPolicyProto.FULL) {
            return proposedPolicy == CompatibilityPolicyProto.FULL || 
                   proposedPolicy == CompatibilityPolicyProto.BACKWARD ||
                   proposedPolicy == CompatibilityPolicyProto.FORWARD;
        }
        
        // BACKWARD/FORWARD compatibility allows same policy or more restrictive
        if (currentPolicy == CompatibilityPolicyProto.BACKWARD) {
            return proposedPolicy == CompatibilityPolicyProto.BACKWARD ||
                   proposedPolicy == CompatibilityPolicyProto.FULL;
        }
        
        if (currentPolicy == CompatibilityPolicyProto.FORWARD) {
            return proposedPolicy == CompatibilityPolicyProto.FORWARD ||
                   proposedPolicy == CompatibilityPolicyProto.FULL;
        }
        
        return false;
    }
}