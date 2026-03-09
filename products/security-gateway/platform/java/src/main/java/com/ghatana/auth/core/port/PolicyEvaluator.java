package com.ghatana.auth.core.port;

import com.ghatana.platform.domain.auth.Permission;
import com.ghatana.platform.domain.auth.Role;
import com.ghatana.platform.domain.auth.User;
import com.ghatana.platform.domain.auth.Scope;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;

/**
 * Interface for RBAC/ABAC policy evaluation and authorization decisions.
 *
 * <p><b>Purpose</b><br>
 * Port interface (hexagonal architecture) for authorization policy evaluation.
 * Policy engine module provides implementations (RBAC, ABAC, OPA integration, etc.).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Check permission
 * policyEvaluator.evaluate(user, Permission.of("users:read"), resourceId)
 *     .thenApply(allowed -> {
 *         if (allowed) {
 *             // Grant access
 *         } else {
 *             // Deny access
 *         }
 *         return allowed;
 *     });
 *
 * // Check scope
 * policyEvaluator.evaluateScope(user, Scope.of("orders:write"))
 *     .thenApply(allowed -> {
 *         if (allowed) {
 *             // Scope is granted
 *         }
 *         return allowed;
 *     });
 *
 * // Check role
 * policyEvaluator.evaluateRole(user, Role.of("admin"))
 *     .thenApply(hasRole -> {
 *         if (hasRole) {
 *             // User has admin role
 *         }
 *         return hasRole;
 *     });
 * }</pre>
 *
 * <p><b>Policy Types Supported</b><br>
 * - <b>RBAC</b>: Role-based access control (user has role → permissions granted)
 * - <b>ABAC</b>: Attribute-based access control (context-aware decisions)
 * - <b>Resource-level</b>: Per-resource ownership checks
 * - <b>Hierarchical</b>: Role/permission hierarchies
 *
 * <p><b>Multi-Tenant Isolation</b><br>
 * All operations scoped by TenantId - policies are isolated per tenant.
 *
 * <p><b>Implementation Notes</b><br>
 * - Start with RBAC for Phase 1 (simple role-permission mapping)
 * - Add ABAC for Phase 2 (context-aware policies)
 * - Consider OPA (Open Policy Agent) integration for complex policies
 * - Cache policy decisions for performance (with TTL)
 * - Emit audit events for all authorization decisions
 *
 * @doc.type interface
 * @doc.purpose Policy evaluation port
 * @doc.layer core
 * @doc.pattern Port
 */
public interface PolicyEvaluator {
    
    /**
     * Evaluates if user has permission to perform action on resource.
     *
     * @param tenantId tenant identifier
     * @param user user requesting access
     * @param permission permission to check
     * @param resourceId optional resource identifier (null for global permission)
     * @return Promise of true if allowed, false otherwise
     */
    Promise<Boolean> evaluate(TenantId tenantId, User user, Permission permission, String resourceId);
    
    /**
     * Evaluates if user has a specific scope granted.
     *
     * @param tenantId tenant identifier
     * @param user user requesting access
     * @param scope scope to check
     * @return Promise of true if scope is granted, false otherwise
     */
    Promise<Boolean> evaluateScope(TenantId tenantId, User user, Scope scope);
    
    /**
     * Evaluates if user has a specific role.
     *
     * @param tenantId tenant identifier
     * @param user user requesting access
     * @param role role to check
     * @return Promise of true if user has role, false otherwise
     */
    Promise<Boolean> evaluateRole(TenantId tenantId, User user, Role role);
    
    /**
     * Evaluates ABAC policy with context attributes.
     *
     * @param tenantId tenant identifier
     * @param user user requesting access
     * @param action action being performed (e.g., "read", "write")
     * @param resource resource being accessed
     * @param context contextual attributes (time, location, device, etc.)
     * @return Promise of true if allowed by policy, false otherwise
     */
    Promise<Boolean> evaluateAbac(TenantId tenantId, User user, String action, String resource, 
                                   java.util.Map<String, Object> context);
}
