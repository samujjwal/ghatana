package com.ghatana.platform.security.rbac;

import java.util.Collections;
import java.util.Set;

/**
 * Implementation of PermissionEvaluator that delegates to PolicyService.
 * Migrated from RbacGuard to PolicyService for modern policy-based access control.
 
 *
 * @doc.type class
 * @doc.purpose Rbac permission evaluator
 * @doc.layer core
 * @doc.pattern Component
*/
public class RbacPermissionEvaluator implements PermissionEvaluator {
    private final PolicyService policyService;
    
    public RbacPermissionEvaluator(PolicyService policyService) {
        this.policyService = policyService;
    }
    
    public boolean hasPermission(String role, Set<String> roles, String permission) {
        // For backward compatibility, check if any of the user's roles has the permission
        // on an unspecified resource (wildcard "*")
        return roles.stream()
                .anyMatch(r -> policyService.hasPermission(r, "*", permission));
    }
    
    @Override
    public boolean hasPermission(String role, String permission) {
        return policyService.hasPermission(role, "*", permission);
    }
}
