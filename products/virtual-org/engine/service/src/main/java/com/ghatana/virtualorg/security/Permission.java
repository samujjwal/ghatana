package com.ghatana.virtualorg.security;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a permission in the system.
 *
 * <p><b>Purpose</b><br>
 * Value object representing a single permission with resource and action components.
 * Used for fine-grained RBAC authorization checks.
 *
 * <p><b>Format</b><br>
 * Permissions follow the format: {@code resource:action}
 *
 * <p><b>Examples</b><br>
 * <pre>
 * task:read        - Read task resources
 * task:write       - Write task resources
 * task:delete      - Delete task resources
 * agent:start      - Start agent execution
 * agent:stop       - Stop agent execution
 * decision:approve - Approve decisions
 * </pre>
 *
 * <p><b>Wildcards</b><br>
 * Supported wildcard patterns:
 * <pre>
 * task:*   - All task actions
 * *:read   - Read any resource
 * *        - All permissions (admin only)
 * </pre>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Permission perm = Permission.parse("task:read");
 * boolean matches = perm.matches("task:read");   // true
 * 
 * Permission wildcard = Permission.parse("task:*");
 * boolean matches = wildcard.matches("task:write"); // true
 * }</pre>
 *
 * <p><b>Validation</b><br>
 * Canonical constructor validates:
 * - resource: non-blank
 * - action: non-blank
 *
 * @param resource Resource type (e.g., "task", "agent", "decision", "*")
 * @param action Action name (e.g., "read", "write", "start", "*")
 * @doc.type record
 * @doc.purpose Permission value object for RBAC resource:action representation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record Permission(
    @NotNull String resource,
    @NotNull String action
) {
    public Permission {
        if (resource.isBlank()) {
            throw new IllegalArgumentException("Resource cannot be blank");
        }
        if (action.isBlank()) {
            throw new IllegalArgumentException("Action cannot be blank");
        }
    }

    /**
     * Parse a permission string (resource:action)
     */
    @NotNull
    public static Permission parse(@NotNull String permissionString) {
        String[] parts = permissionString.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                "Invalid permission format. Expected 'resource:action', got: " + permissionString
            );
        }
        return new Permission(parts[0].trim(), parts[1].trim());
    }

    /**
     * Check if this permission matches another permission (supports wildcards)
     */
    public boolean matches(@NotNull Permission other) {
        boolean resourceMatches = resource.equals("*") || resource.equals(other.resource);
        boolean actionMatches = action.equals("*") || action.equals(other.action);
        return resourceMatches && actionMatches;
    }

    /**
     * Convert to string format
     */
    @Override
    @NotNull
    public String toString() {
        return resource + ":" + action;
    }

    // Common permissions
    public static final Permission TASK_READ = new Permission("task", "read");
    public static final Permission TASK_WRITE = new Permission("task", "write");
    public static final Permission TASK_DELETE = new Permission("task", "delete");
    public static final Permission TASK_ASSIGN = new Permission("task", "assign");

    public static final Permission AGENT_START = new Permission("agent", "start");
    public static final Permission AGENT_STOP = new Permission("agent", "stop");
    public static final Permission AGENT_RESTART = new Permission("agent", "restart");

    public static final Permission DECISION_APPROVE = new Permission("decision", "approve");
    public static final Permission DECISION_REJECT = new Permission("decision", "reject");
    public static final Permission DECISION_ESCALATE = new Permission("decision", "escalate");

    public static final Permission ADMIN_ALL = new Permission("*", "*");
}
