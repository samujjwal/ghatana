package com.ghatana.products.yappc.domain.enums;

/**
 * Workspace role enumeration for access control.
 *
 * <p><b>Purpose</b><br>
 * Defines the hierarchy of roles within a workspace, determining user permissions and capabilities.
 *
 * <p><b>Role Hierarchy (highest to lowest)</b><br>
 * - OWNER: Full control, cannot be removed, can delete workspace
 * - ADMIN: Manage members, settings, resources (cannot delete workspace)
 * - MEMBER: Standard user, can create/edit own resources
 * - VIEWER: Read-only access to workspace resources
 *
 * <p><b>Usage</b><br>
 * Used in WorkspaceMember entity to control access permissions via RBAC.
 *
 * @see com.ghatana.products.yappc.domain.model.WorkspaceMember
 * @doc.type enum
 * @doc.purpose Workspace role-based access control
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum WorkspaceRole {
    /**
     * Workspace owner - full administrative control.
     * Can delete workspace, manage all settings, manage members.
     */
    OWNER,

    /**
     * Administrator - manage workspace but cannot delete it.
     * Can manage members, settings, resources.
     */
    ADMIN,

    /**
     * Standard member - create and edit own resources.
     * Can view workspace data, create incidents/alerts, run scans.
     */
    MEMBER,

    /**
     * Read-only viewer - no edit permissions.
     * Can view dashboards, reports, but cannot modify data.
     */
    VIEWER
}
