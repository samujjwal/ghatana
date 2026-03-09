package com.ghatana.platform.governance.rbac;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Role-Based Access Control (RBAC) policy for permission enforcement.
 *
 * <h2>Purpose</h2>
 * Provides permission checking for RBAC model where:
 * <ul>
 *   <li>Roles are assigned to principals (users, agents, services)</li>
 *   <li>Permissions are granted to roles</li>
 *   <li>Access decisions based on: principal role → role permissions</li>
 * </ul>
 *
 * <h2>RBAC Model</h2>
 * <pre>
 * Principal (User/Agent) ← Assigned Roles → Role ← Permissions
 *
 * Example:
 * User "alice"        Role: ADMIN       Permissions: {READ_EVENTS, WRITE_EVENTS, 
 *                                                      DELETE_EVENTS, MANAGE_USERS}
 *
 * User "bob"          Role: OPERATOR    Permissions: {READ_EVENTS, WRITE_EVENTS}
 *
 * Service "agent-1"   Role: SERVICE     Permissions: {READ_PATTERNS, EXECUTE_PATTERNS}
 * </pre>
 *
 * <h2>Key Concepts</h2>
 * <ul>
 *   <li><b>Role:</b> Set of related permissions (e.g., ADMIN, OPERATOR, SERVICE)</li>
 *   <li><b>Permission:</b> Specific action (e.g., READ_EVENTS, WRITE_USERS)</li>
 *   <li><b>Principal:</b> Entity with assigned roles (user, agent, service)</li>
 *   <li><b>Policy:</b> Mapping of roles to their permitted actions</li>
 * </ul>
 *
 * <h2>Permission Formats</h2>
 * Permissions follow resource:action or resource:resource_id:action patterns:
 * <ul>
 *   <li><b>READ_EVENTS</b> - Read any event</li>
 *   <li><b>WRITE_EVENTS</b> - Write/create events</li>
 *   <li><b>DELETE_EVENTS</b> - Delete events</li>
 *   <li><b>MANAGE_USERS</b> - Create/update/delete users</li>
 *   <li><b>PATTERN:READ</b> - Read patterns</li>
 *   <li><b>PATTERN:ID_123:EXECUTE</b> - Execute specific pattern</li>
 *   <li><b>TENANT:ID_456:ADMIN</b> - Administer specific tenant</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * {@code
 * // 1. Create policy from role-to-permissions mapping
 * Map<String, Set<String>> permissions = new HashMap<>();
 * permissions.put("ADMIN", Set.of(
 *     "READ_EVENTS", "WRITE_EVENTS", "DELETE_EVENTS",
 *     "MANAGE_USERS", "MANAGE_AGENTS"
 * ));
 * permissions.put("OPERATOR", Set.of(
 *     "READ_EVENTS", "WRITE_EVENTS"
 * ));
 * permissions.put("VIEWER", Set.of(
 *     "READ_EVENTS"
 * ));
 *
 * RbacPolicy policy = new RbacPolicy(permissions);
 *
 * // 2. Check if role has permission
 * if (policy.isAllowed("ADMIN", "DELETE_EVENTS")) {
 *     // Admin role has DELETE_EVENTS permission
 *     eventService.delete(eventId);
 * }
 *
 * if (!policy.isAllowed("VIEWER", "WRITE_EVENTS")) {
 *     // Viewer cannot write events
 *     throw new AccessDeniedException("Viewer role cannot write events");
 * }
 *
 * // 3. Build from JSON/Map configuration
 * Map<String, List<String>> config = objectMapper.readValue(
 *     jsonConfig,
 *     new TypeReference<Map<String, List<String>>>() {}
 * );
 * RbacPolicy policy = RbacPolicy.fromMap(config);
 *
 * // 4. In HTTP endpoint authorization
 * @GetMapping("/events")
 * public ResponseEntity<List<Event>> listEvents(
 *     @AuthenticationPrincipal User user
 * ) {
 *     String userRole = user.getRole();
 *
 *     if (!rbacPolicy.isAllowed(userRole, "READ_EVENTS")) {
 *         return ResponseEntity.status(403).build();
 *     }
 *
 *     return ResponseEntity.ok(eventService.list());
 * }
 *
 * // 5. In gRPC interceptor for method-level authorization
 * public class AuthorizationInterceptor implements ServerInterceptor {
 *     private final RbacPolicy policy;
 *     private final Map<String, String> methodToPermission;  // Method → Required Permission
 *
 *     @Override
 *     public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
 *         ServerCall<ReqT, RespT> call,
 *         Metadata headers,
 *         ServerCallHandler<ReqT, RespT> next
 *     ) {
 *         String userRole = extractRoleFromHeaders(headers);
 *         String method = call.getMethodDescriptor().getFullMethodName();
 *         String requiredPermission = methodToPermission.get(method);
 *
 *         if (!policy.isAllowed(userRole, requiredPermission)) {
 *             call.close(Status.PERMISSION_DENIED, new Metadata());
 *             return new ServerCall.Listener<ReqT>() {};
 *         }
 *
 *         return next.startCall(call, headers);
 *     }
 * }
 *
 * // 6. Dynamic permission checking with audit logging
 * public void checkPermissionWithAudit(
 *     String userId, String role, String permission, String resourceId
 * ) {
 *     boolean allowed = policy.isAllowed(role, permission);
 *
 *     if (!allowed) {
 *         auditLog.logAccessDenied(userId, role, permission, resourceId);
 *         throw new AccessDeniedException(
 *             String.format("User %s with role %s denied %s on %s",
 *                 userId, role, permission, resourceId)
 *         );
 *     }
 *
 *     auditLog.logAccessGranted(userId, role, permission, resourceId);
 * }
 * }
 *
 * <h2>Configuration Format (JSON)</h2>
 * <pre>
 * {
 *   "ADMIN": [
 *     "READ_EVENTS", "WRITE_EVENTS", "DELETE_EVENTS",
 *     "MANAGE_USERS", "MANAGE_AGENTS", "MANAGE_POLICIES"
 *   ],
 *   "OPERATOR": [
 *     "READ_EVENTS", "WRITE_EVENTS"
 *   ],
 *   "VIEWER": [
 *     "READ_EVENTS"
 *   ],
 *   "SERVICE": [
 *     "READ_PATTERNS", "EXECUTE_PATTERNS"
 *   ]
 * }
 * </pre>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Layer:</b> Governance / Authorization</li>
 *   <li><b>Module:</b> core/governance</li>
 *   <li><b>Usage:</b> Authorization checks in HTTP handlers, gRPC interceptors, service methods</li>
 *   <li><b>Pattern:</b> RBAC model, policy pattern</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>No attribute-based policies (ABAC) - just role and permission</li>
 *   <li>No role hierarchy/inheritance - use permission union for composite roles</li>
 *   <li>No permission groups - enumerate all required permissions per role</li>
 *   <li>No temporal restrictions - permissions always active</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Immutable after construction (read-only policy). Safe for concurrent access.
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>isAllowed():</b> O(1) average case (HashMap lookup + Set contains)</li>
 *   <li><b>Memory:</b> O(total permissions) - all role-permission pairs stored in memory</li>
 * </ul>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Cache policy instance - don't recreate for every request</li>
 *   <li>Use standardized permission names - document your permission taxonomy</li>
 *   <li>Log all permission denials for security auditing</li>
 *   <li>Fail closed - deny access if role/permission not found</li>
 *   <li>Use specific permissions (READ vs DELETE) not generic ones</li>
 * </ul>
 *
 * @see com.ghatana.platform.governance.security.Principal Subject of authorization
 * @see com.ghatana.platform.governance.security.SecurityContext Context containing principal
 * @doc.type class
 * @doc.layer core
 * @doc.purpose RBAC policy for role-based permission checking
 * @doc.pattern authorization-policy rbac-model access-control
 */
public class RbacPolicy {
    private final Map<String, Set<String>> rolePermissions;

    /**
     * Constructs an RbacPolicy with role-to-permissions mapping.
     *
     * <p>Creates an immutable RBAC policy that defines which permissions are
     * granted to each role.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * Map<String, Set<String>> perms = new HashMap<>();
     * perms.put("ADMIN", Set.of("READ", "WRITE", "DELETE"));
     * perms.put("USER", Set.of("READ"));
     * 
     * RbacPolicy policy = new RbacPolicy(perms);
     * assert policy.isAllowed("ADMIN", "DELETE");  // true
     * assert !policy.isAllowed("USER", "WRITE");   // false
     * }</pre>
     *
     * @param rolePermissions mapping of role names to their granted permissions (never null)
     */
    public RbacPolicy(Map<String, Set<String>> rolePermissions) {
        this.rolePermissions = rolePermissions;
    }

    /**
     * Checks if a role has the specified permission.
     *
     * <p>Returns true only if the role exists in the policy AND the role's
     * permission set contains the specified permission. Returns false for:
     * - Unknown role (not in policy)
     * - Role exists but missing permission
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * if (policy.isAllowed(userRole, "DELETE_EVENTS")) {
     *     eventService.delete(eventId);
     * } else {
     *     throw new AccessDeniedException("Insufficient permissions");
     * }
     * }</pre>
     *
     * @param role the role to check (typically from authenticated principal)
     * @param permission the permission to verify (e.g., "READ_EVENTS", "WRITE_USERS")
     * @return true if role has permission, false if role missing or permission not granted
     */
    public boolean isAllowed(String role, String permission) {
        Set<String> permissions = rolePermissions.get(role);
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Factory method to create policy from JSON/config Map.
     *
     * <p>Converts a map with List values (common in JSON deserialization) to
     * the internal Set-based representation. Handles null values safely.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * ObjectMapper mapper = new ObjectMapper();
     * Map<String, List<String>> config = mapper.readValue(
     *     jsonString,
     *     new TypeReference<Map<String, List<String>>>() {}
     * );
     * 
     * RbacPolicy policy = RbacPolicy.fromMap(config);
     * }</pre>
     *
     * <p><b>Example Input:</b>
     * <pre>
     * {
     *   "ADMIN": ["READ", "WRITE", "DELETE"],
     *   "USER": ["READ"],
     *   "GUEST": null  // Handled gracefully
     * }
     * </pre>
     *
     * @param map configuration map with role → permission list (can contain null values)
     * @return new RbacPolicy instance with Set-based permissions
     */
    public static RbacPolicy fromMap(Map<String, List<String>> map) {
        return new RbacPolicy(map.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new HashSet<>(entry.getValue() != null ? entry.getValue() : List.of())
            )));
    }
}
