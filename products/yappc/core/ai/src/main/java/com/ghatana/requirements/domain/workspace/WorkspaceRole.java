package com.ghatana.requirements.domain.workspace;

/**
 * Workspace-level roles.
 *
 * <p><b>Purpose</b><br>
 * Defines roles within a workspace. These are separate from system roles
 * (OWNER, ADMIN, MEMBER, VIEWER in core/auth) and allow fine-grained
 * workspace-specific access control.
 *
 * <p><b>Integration</b><br>
 * Workspace roles map to Permission system from core/auth:
 * - OWNER: All workspace permissions
 * - ADMIN: Manage members, settings, projects
 * - MEMBER: Create/edit projects, create requirements
 * - VIEWER: Read-only access
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkspaceRole role = WorkspaceRole.MEMBER;
 * if (role.canEdit()) {
 *     // User can create/modify content
 * }
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Workspace-specific role definitions
 * @doc.layer product
 * @doc.pattern Value Object
 * @see WorkspaceMember
 */
public enum WorkspaceRole {
    /**
     * Full control over workspace including settings, members, content.
     */
    OWNER(true, true, true),

    /**
     * Can manage workspace members and settings, create/edit content.
     */
    ADMIN(true, true, false),

    /**
     * Can create and edit projects, create requirements, collaborate.
     */
    MEMBER(true, false, false),

    /**
     * Read-only access to workspace content.
     */
    VIEWER(false, false, false);

    private final boolean canEdit;
    private final boolean canManage;
    private final boolean canDelete;

    WorkspaceRole(boolean canEdit, boolean canManage, boolean canDelete) {
        this.canEdit = canEdit;
        this.canManage = canManage;
        this.canDelete = canDelete;
    }

    /**
     * Check if role allows editing content.
     *
     * @return true if role can edit
     */
    public boolean canEdit() {
        return canEdit;
    }

    /**
     * Check if role allows managing members and settings.
     *
     * @return true if role can manage
     */
    public boolean canManage() {
        return canManage;
    }

    /**
     * Check if role allows deleting content.
     *
     * @return true if role can delete
     */
    public boolean canDelete() {
        return canDelete;
    }
}