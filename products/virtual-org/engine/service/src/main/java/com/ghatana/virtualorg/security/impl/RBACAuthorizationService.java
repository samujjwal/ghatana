package com.ghatana.virtualorg.security.impl;

import com.ghatana.virtualorg.security.AuthorizationService;
import com.ghatana.virtualorg.security.Permission;
import com.ghatana.virtualorg.security.Principal;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Role-Based Access Control (RBAC) authorization service.
 *
 * <p><b>Purpose</b><br>
 * Adapter implementing {@link AuthorizationService} using role-based permissions
 * with wildcard support and role hierarchy.
 *
 * <p><b>Architecture Role</b><br>
 * Security adapter managing authorization policies. Provides:
 * - Role definitions with permission sets
 * - Principal role assignments
 * - Permission checking with wildcard matching (*, task:*)
 * - Role hierarchy for permission escalation
 *
 * <p><b>Predefined Roles</b><br>
 * - **admin**: Full system access (*)
 * - **senior-engineer**: Complex tasks, code review approval
 * - **engineer**: Standard task execution
 * - **junior-engineer**: Simple tasks (read-only sensitive ops)
 * - **architect**: Architectural decisions, design approval
 * - **qa**: Test execution, result approval
 * - **devops**: Deployment, infrastructure management
 * - **product-manager**: Task creation, priority setting
 *
 * <p><b>Permission Wildcards</b><br>
 * - {@code *}: All permissions (admin only)
 * - {@code task:*}: All task actions
 * - {@code *:read}: Read any resource
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RBACAuthorizationService authz = new RBACAuthorizationService(eventloop);
 * 
 * // Check authorization
 * boolean canRead = authz.isAuthorized(
 *     principal,
 *     "task:read",
 *     "task-123"
 * ).getResult();
 * 
 * // Add custom role
 * authz.addRole("custom-role", Set.of(
 *     Permission.parse("task:read"),
 *     Permission.parse("agent:start")
 * ));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose RBAC authorization adapter with role hierarchy and wildcards
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class RBACAuthorizationService implements AuthorizationService {
    private static final Logger logger = LoggerFactory.getLogger(RBACAuthorizationService.class);

    private final Eventloop eventloop;
    private final Map<String, Set<Permission>> rolePermissions;
    private final Map<String, Set<String>> principalRoles;
    private final Map<String, Set<String>> roleHierarchy; // role -> can escalate to

    public RBACAuthorizationService(@NotNull Eventloop eventloop) {
        this.eventloop = eventloop;
        this.rolePermissions = new ConcurrentHashMap<>();
        this.principalRoles = new ConcurrentHashMap<>();
        this.roleHierarchy = new ConcurrentHashMap<>();

        initializeDefaultRoles();
        logger.info("RBAC authorization service initialized with {} roles",
            rolePermissions.size());
    }

    @Override
    @NotNull
    public Promise<Boolean> isAuthorized(
        @NotNull Principal principal,
        @NotNull String action,
        @NotNull String resourceId
    ) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                Permission required = Permission.parse(action);

                // Get all permissions for principal
                Set<Permission> principalPermissions = getAllPermissionsForPrincipal(principal);

                // Check if any permission matches
                boolean authorized = principalPermissions.stream()
                    .anyMatch(p -> p.matches(required));

                if (authorized) {
                    logger.debug("Authorized: {} {} on {}",
                        principal.id(), action, resourceId);
                } else {
                    logger.warn("Denied: {} {} on {}",
                        principal.id(), action, resourceId);
                }

                return authorized;

            } catch (Exception e) {
                logger.error("Authorization check failed", e);
                return false; // Deny on error
            }
        });
    }

    @Override
    @NotNull
    public Promise<Boolean> hasPermission(
        @NotNull Principal principal,
        @NotNull String permission
    ) {
        return isAuthorized(principal, permission, "");
    }

    @Override
    @NotNull
    public Promise<Set<String>> getPermissions(@NotNull Principal principal) {
        return Promise.ofBlocking(eventloop, () -> {
            Set<Permission> permissions = getAllPermissionsForPrincipal(principal);
            Set<String> permissionStrings = new HashSet<>();
            for (Permission p : permissions) {
                permissionStrings.add(p.toString());
            }
            return permissionStrings;
        });
    }

    @Override
    @NotNull
    public Promise<Boolean> grantRole(@NotNull String principalId, @NotNull String role) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                if (!rolePermissions.containsKey(role)) {
                    logger.warn("Attempted to grant non-existent role: {}", role);
                    return false;
                }

                principalRoles.computeIfAbsent(principalId, k -> new HashSet<>())
                    .add(role);

                logger.info("Granted role '{}' to principal '{}'", role, principalId);
                return true;

            } catch (Exception e) {
                logger.error("Failed to grant role", e);
                return false;
            }
        });
    }

    @Override
    @NotNull
    public Promise<Boolean> revokeRole(@NotNull String principalId, @NotNull String role) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                Set<String> roles = principalRoles.get(principalId);
                if (roles != null) {
                    boolean removed = roles.remove(role);
                    if (removed) {
                        logger.info("Revoked role '{}' from principal '{}'", role, principalId);
                    }
                    return removed;
                }
                return false;

            } catch (Exception e) {
                logger.error("Failed to revoke role", e);
                return false;
            }
        });
    }

    @Override
    @NotNull
    public Promise<Boolean> canEscalate(@NotNull Principal principal, @NotNull String targetRole) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                // Check if any of principal's roles can escalate to target role
                for (String role : principal.roles()) {
                    Set<String> canEscalateTo = roleHierarchy.get(role);
                    if (canEscalateTo != null && canEscalateTo.contains(targetRole)) {
                        logger.debug("Principal {} can escalate from {} to {}",
                            principal.id(), role, targetRole);
                        return true;
                    }
                }

                logger.debug("Principal {} cannot escalate to {}",
                    principal.id(), targetRole);
                return false;

            } catch (Exception e) {
                logger.error("Escalation check failed", e);
                return false;
            }
        });
    }

    // Private helper methods

    @NotNull
    private Set<Permission> getAllPermissionsForPrincipal(@NotNull Principal principal) {
        Set<Permission> permissions = new HashSet<>();

        for (String role : principal.roles()) {
            Set<Permission> rolePerms = rolePermissions.get(role);
            if (rolePerms != null) {
                permissions.addAll(rolePerms);
            }
        }

        return permissions;
    }

    private void initializeDefaultRoles() {
        // Admin role - full access
        defineRole("admin", Set.of(Permission.ADMIN_ALL));

        // Architect role
        defineRole("architect", Set.of(
            Permission.TASK_READ,
            Permission.TASK_WRITE,
            new Permission("task", "architecture"),
            new Permission("decision", "approve"),
            new Permission("design", "*"),
            Permission.AGENT_START,
            Permission.AGENT_STOP
        ));

        // Senior Engineer role
        defineRole("senior-engineer", Set.of(
            Permission.TASK_READ,
            Permission.TASK_WRITE,
            Permission.TASK_ASSIGN,
            new Permission("code", "write"),
            new Permission("code", "review"),
            new Permission("code", "approve"),
            new Permission("test", "write"),
            new Permission("test", "execute"),
            new Permission("git", "*")
        ));

        // Engineer role
        defineRole("engineer", Set.of(
            Permission.TASK_READ,
            Permission.TASK_WRITE,
            new Permission("code", "write"),
            new Permission("code", "review"),
            new Permission("test", "write"),
            new Permission("test", "execute"),
            new Permission("git", "commit"),
            new Permission("git", "push"),
            new Permission("git", "pull")
        ));

        // Junior Engineer role
        defineRole("junior-engineer", Set.of(
            Permission.TASK_READ,
            new Permission("code", "write"),
            new Permission("test", "write"),
            new Permission("git", "commit"),
            new Permission("git", "push")
        ));

        // QA role
        defineRole("qa", Set.of(
            Permission.TASK_READ,
            Permission.TASK_WRITE,
            new Permission("test", "*"),
            new Permission("code", "read"),
            new Permission("bug", "*")
        ));

        // DevOps role
        defineRole("devops", Set.of(
            Permission.TASK_READ,
            Permission.TASK_WRITE,
            new Permission("deployment", "*"),
            new Permission("infrastructure", "*"),
            new Permission("monitoring", "*"),
            Permission.AGENT_START,
            Permission.AGENT_STOP,
            Permission.AGENT_RESTART
        ));

        // Product Manager role
        defineRole("product-manager", Set.of(
            Permission.TASK_READ,
            Permission.TASK_WRITE,
            Permission.TASK_DELETE,
            Permission.TASK_ASSIGN,
            new Permission("requirement", "*"),
            new Permission("priority", "set"),
            Permission.DECISION_APPROVE
        ));

        // Define role hierarchy (escalation paths)
        defineEscalation("junior-engineer", Set.of("engineer", "senior-engineer"));
        defineEscalation("engineer", Set.of("senior-engineer", "architect"));
        defineEscalation("senior-engineer", Set.of("architect"));
        defineEscalation("architect", Set.of("admin"));
        defineEscalation("qa", Set.of("senior-engineer"));
        defineEscalation("devops", Set.of("architect"));
        defineEscalation("product-manager", Set.of("admin"));

        logger.info("Initialized {} default roles with hierarchy", rolePermissions.size());
    }

    private void defineRole(@NotNull String role, @NotNull Set<Permission> permissions) {
        rolePermissions.put(role, new HashSet<>(permissions));
        logger.debug("Defined role '{}' with {} permissions", role, permissions.size());
    }

    private void defineEscalation(@NotNull String fromRole, @NotNull Set<String> toRoles) {
        roleHierarchy.put(fromRole, new HashSet<>(toRoles));
        logger.debug("Defined escalation: {} -> {}", fromRole, toRoles);
    }

    /**
     * Add a custom role (for extensibility)
     */
    public void addRole(@NotNull String role, @NotNull Set<Permission> permissions) {
        defineRole(role, permissions);
    }

    /**
     * Add a permission to an existing role
     */
    public void addPermissionToRole(@NotNull String role, @NotNull Permission permission) {
        rolePermissions.computeIfAbsent(role, k -> new HashSet<>())
            .add(permission);
        logger.info("Added permission '{}' to role '{}'", permission, role);
    }
}
