package com.ghatana.platform.security.rbac;

/**
 * Standard permission definitions for the application.
 * These permissions are used for role-based access control (RBAC).
 *
 * @doc.type class
 * @doc.purpose Standard permission constant definitions for RBAC
 * @doc.layer security
 * @doc.pattern ValueObject
 */
public final class Permission {
    /**
     * Permission to read resources.
     */
    public static final String READ = "read";
    
    /**
     * Permission to write (create/update) resources.
     */
    public static final String WRITE = "write";
    
    /**
     * Permission to delete resources.
     */
    public static final String DELETE = "delete";
    
    /**
     * Permission to execute operations.
     */
    public static final String EXECUTE = "execute";
    
    /**
     * Permission to administer resources.
     */
    public static final String ADMIN = "admin";
    
    /**
     * Permission to read audit logs.
     */
    public static final String AUDIT_READ = "audit:read";
    
    /**
     * Permission to write audit logs.
     */
    public static final String AUDIT_WRITE = "audit:write";
    
    /**
     * Permission to read user information.
     */
    public static final String USER_READ = "user:read";
    
    /**
     * Permission to write user information.
     */
    public static final String USER_WRITE = "user:write";
    
    /**
     * Permission to manage roles and permissions.
     */
    public static final String ROLE_MANAGE = "role:manage";
    
    /**
     * Permission to read configuration.
     */
    public static final String CONFIG_READ = "config:read";
    
    /**
     * Permission to write configuration.
     */
    public static final String CONFIG_WRITE = "config:write";
    
    /**
     * Permission to read metrics.
     */
    public static final String METRICS_READ = "metrics:read";
    
    /**
     * Permission to write metrics.
     */
    public static final String METRICS_WRITE = "metrics:write";
    
    /**
     * Permission to read events.
     */
    public static final String EVENT_READ = "event:read";
    
    /**
     * Permission to write events.
     */
    public static final String EVENT_WRITE = "event:write";
    
    /**
     * Permission to process events.
     */
    public static final String EVENT_PROCESS = "event:process";
    
    /**
     * Permission to manage agents.
     */
    public static final String AGENT_MANAGE = "agent:manage";
    
    /**
     * Permission to read agent information.
     */
    public static final String AGENT_READ = "agent:read";
    
    /**
     * Permission to execute agents.
     */
    public static final String AGENT_EXECUTE = "agent:execute";

    // ---- Workspace Permissions ----

    /**
     * Permission to create workspaces.
     */
    public static final String WORKSPACE_CREATE = "workspace.create";

    /**
     * Permission to read workspaces.
     */
    public static final String WORKSPACE_READ = "workspace.read";

    /**
     * Permission to update workspaces.
     */
    public static final String WORKSPACE_UPDATE = "workspace.update";

    /**
     * Permission to delete workspaces.
     */
    public static final String WORKSPACE_DELETE = "workspace.delete";

    /**
     * Permission to manage workspace members.
     */
    public static final String WORKSPACE_MANAGE_MEMBERS = "workspace.manage_members";

    // ---- Project Permissions ----

    /**
     * Permission to create projects.
     */
    public static final String PROJECT_CREATE = "project.create";

    /**
     * Permission to read projects.
     */
    public static final String PROJECT_READ = "project.read";

    /**
     * Permission to update projects.
     */
    public static final String PROJECT_UPDATE = "project.update";

    /**
     * Permission to delete projects.
     */
    public static final String PROJECT_DELETE = "project.delete";

    // ---- Requirement Permissions ----

    /**
     * Permission to create requirements.
     */
    public static final String REQUIREMENT_CREATE = "requirement.create";

    /**
     * Permission to read requirements.
     */
    public static final String REQUIREMENT_READ = "requirement.read";

    /**
     * Permission to update requirements.
     */
    public static final String REQUIREMENT_UPDATE = "requirement.update";

    /**
     * Permission to delete requirements.
     */
    public static final String REQUIREMENT_DELETE = "requirement.delete";

    /**
     * Permission to approve requirements.
     */
    public static final String REQUIREMENT_APPROVE = "requirement.approve";

    // ---- AI Permissions ----

    /**
     * Permission to request AI suggestions.
     */
    public static final String AI_SUGGESTION_REQUEST = "ai.suggestion.request";

    /**
     * Permission to provide AI suggestion feedback.
     */
    public static final String AI_SUGGESTION_FEEDBACK = "ai.suggestion.feedback";

    // ---- User Management Permissions ----

    /**
     * Permission to manage users.
     */
    public static final String USER_MANAGE = "user.manage";

    /**
     * Permission to assign roles.
     */
    public static final String ROLE_ASSIGN = "role.assign";

    // ---- Admin Permissions ----

    /**
     * Permission for system administration.
     */
    public static final String ADMIN_SYSTEM = "admin.system";

    private Permission() {
        // Prevent instantiation
    }
}
