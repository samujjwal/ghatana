package com.ghatana.auth.core.port;

import com.ghatana.platform.domain.auth.AuthorizationResult;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserPrincipal;
import io.activej.promise.Promise;

/**
 * Port abstraction for low-level RBAC policy evaluation.
 *
 * <p><b>Purpose</b><br>
 * Evaluates RBAC policies at a lower level than AuthorizationService.
 * While AuthorizationService provides high-level permission/role checks,
 * this interface handles:
 * - Detailed evaluation results (decision + reason + evidence)
 * - Resource-action based authorization (not just permissions)
 * - Dynamic policy evaluation without caching
 * - SLA enforcement (max evaluation time)
 *
 * <p><b>Usage Example</b><br>
 * <pre>{@code
 * RbacPolicyEvaluator evaluator = new RbacPolicyEvaluatorImpl(...);
 *
 * // Simple yes/no check
 * Promise<Boolean> allowed = evaluator.canAccess(
 *     tenantId,
 *     userPrincipal,
 *     "documents",      // resource type
 *     "read"            // action
 * );
 *
 * // Detailed evaluation with reason
 * Promise<AuthorizationResult> result = evaluator.evaluateDetailed(
 *     tenantId,
 *     userPrincipal,
 *     "documents",
 *     "delete"
 * );
 * // result.isGranted() = false
 * // result.getReason() = "User lacks ADMIN role required for document deletion"
 * }</pre>
 *
 * <p><b>Implementation Notes</b><br>
 * Implementations MUST:
 * - Evaluate policies without caching (use AuthorizationService for caching)
 * - Include detailed reasons in AuthorizationResult for debugging
 * - Enforce tenant isolation strictly
 * - Complete evaluation within 100ms (SLA target)
 * - Emit metrics: policy.evaluated, policy.granted, policy.denied, policy.time_ms
 * - Support dynamic policy changes without service restart
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe for concurrent evaluation across multiple tenants.
 * Uses Promise-based async (ActiveJ Eventloop) for non-blocking execution.
 *
 * <p><b>Performance Characteristics</b><br>
 * - Expected evaluation time: <50ms p99
 * - No caching (fresh evaluation always)
 * - Should emit timing metrics for SLO tracking
 *
 * @doc.type interface
 * @doc.purpose RBAC policy evaluation port
 * @doc.layer product
 * @doc.pattern Port
 *
 * @see AuthorizationService for higher-level permission/role checks with caching
 * @see AuthorizationResult for detailed evaluation outcome structure
 */
public interface RbacPolicyEvaluator {

    /**
     * Evaluates whether user can perform an action on a resource.
     *
     * <p>Performs direct policy evaluation without caching. Returns simple yes/no.
     * For detailed results including reason, use {@link #evaluateDetailed}.
     *
     * @param tenantId the tenant ID
     * @param userPrincipal the user performing the action
     * @param resource the resource type being accessed (e.g., "documents", "users")
     * @param action the action being performed (e.g., "read", "write", "delete")
     * @return Promise completing with true if allowed, false if denied
     *
     * @see #evaluateDetailed for detailed evaluation result with reason
     */
    Promise<Boolean> canAccess(TenantId tenantId, UserPrincipal userPrincipal, String resource, String action);

    /**
     * Evaluates user access with detailed outcome including reason.
     *
     * <p>Performs thorough policy evaluation and returns structured result including:
     * - Whether access is granted/denied
     * - Reason for the decision
     * - Evidence used (roles, permissions, policies matched)
     *
     * <p>Useful for:
     * - Debugging authorization issues
     * - Auditing why decisions were made
     * - Explaining to users why they don't have access
     *
     * @param tenantId the tenant ID
     * @param userPrincipal the user performing the action
     * @param resource the resource type being accessed
     * @param action the action being performed
     * @return Promise completing with detailed AuthorizationResult
     *
     * @see AuthorizationResult for result structure
     */
    Promise<AuthorizationResult> evaluateDetailed(TenantId tenantId, UserPrincipal userPrincipal, String resource, String action);

    /**
     * Evaluates multiple resource-action pairs for a user.
     *
     * <p>Batch evaluation for efficiency when checking multiple permissions.
     * Returns string in format "resource:action=GRANTED|DENIED" for each pair.
     *
     * <p>Example result:
     * <pre>
     * documents:read=GRANTED
     * documents:delete=DENIED
     * users:create=GRANTED
     * </pre>
     *
     * @param tenantId the tenant ID
     * @param userPrincipal the user
     * @param resourceActionPairs array of "resource:action" strings (e.g., ["documents:read", "users:delete"])
     * @return Promise completing with map of permission → decision
     */
    Promise<java.util.Map<String, String>> evaluateBatch(TenantId tenantId, UserPrincipal userPrincipal, String... resourceActionPairs);

    /**
     * Checks if a user has ADMIN role (convenience method).
     *
     * <p>Many authorization systems need to check admin status independently.
     * This is a convenience method for that common pattern.
     *
     * @param tenantId the tenant ID
     * @param userPrincipal the user to check
     * @return Promise completing with true if user is ADMIN, false otherwise
     */
    Promise<Boolean> isAdmin(TenantId tenantId, UserPrincipal userPrincipal);

    /**
     * Checks if a user has OWNER role (convenience method).
     *
     * <p>Ownership is often used for resource-specific authorization.
     * This checks if user has OWNER role in this tenant.
     *
     * @param tenantId the tenant ID
     * @param userPrincipal the user to check
     * @return Promise completing with true if user is OWNER, false otherwise
     */
    Promise<Boolean> isOwner(TenantId tenantId, UserPrincipal userPrincipal);
}
