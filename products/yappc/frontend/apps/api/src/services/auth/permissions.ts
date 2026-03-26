/**
 * Permission Definitions
 *
 * Static permission matrix for all RBAC roles in the YAPPC platform.
 * Every role inherits from lower roles in the hierarchy (VIEWER < EDITOR < ADMIN < OWNER).
 *
 * @doc.type module
 * @doc.purpose Static RBAC permission matrix
 * @doc.layer product
 * @doc.pattern Value Object
 */

// ============================================================================
// Permission Actions
// ============================================================================

export type ResourceType =
  | 'workspace'
  | 'project'
  | 'canvas'
  | 'page'
  | 'workflow'
  | 'member'
  | 'audit'
  | 'ai';

export type ActionType =
  | 'read'
  | 'create'
  | 'update'
  | 'delete'
  | 'manage_members'
  | 'invite'
  | 'export'
  | 'ai_generate';

export interface Permission {
  resource: ResourceType;
  action: ActionType;
}

// ============================================================================
// Role Hierarchy
// ============================================================================

export type UserRole = 'VIEWER' | 'EDITOR' | 'ADMIN' | 'OWNER';

const ROLE_RANK: Record<UserRole, number> = {
  VIEWER: 1,
  EDITOR: 2,
  ADMIN: 3,
  OWNER: 4,
};

/**
 * Returns true if `role` meets or exceeds `required`.
 */
export function roleAtLeast(role: UserRole, required: UserRole): boolean {
  return ROLE_RANK[role] >= ROLE_RANK[required];
}

// ============================================================================
// Permission Matrix
// ============================================================================

/**
 * Full permission matrix keyed by role.
 * Each role entry includes all permissions granted to that role.
 */
export const PERMISSION_MATRIX: Record<UserRole, Permission[]> = {
  VIEWER: [
    { resource: 'workspace', action: 'read' },
    { resource: 'project', action: 'read' },
    { resource: 'canvas', action: 'read' },
    { resource: 'page', action: 'read' },
    { resource: 'workflow', action: 'read' },
    { resource: 'ai', action: 'read' },
  ],
  EDITOR: [
    // Inherits VIEWER
    { resource: 'workspace', action: 'read' },
    { resource: 'project', action: 'read' },
    { resource: 'project', action: 'create' },
    { resource: 'project', action: 'update' },
    { resource: 'canvas', action: 'read' },
    { resource: 'canvas', action: 'create' },
    { resource: 'canvas', action: 'update' },
    { resource: 'page', action: 'read' },
    { resource: 'page', action: 'create' },
    { resource: 'page', action: 'update' },
    { resource: 'workflow', action: 'read' },
    { resource: 'workflow', action: 'create' },
    { resource: 'workflow', action: 'update' },
    { resource: 'ai', action: 'read' },
    { resource: 'ai', action: 'ai_generate' },
  ],
  ADMIN: [
    // Inherits EDITOR
    { resource: 'workspace', action: 'read' },
    { resource: 'workspace', action: 'update' },
    { resource: 'workspace', action: 'invite' },
    { resource: 'workspace', action: 'manage_members' },
    { resource: 'project', action: 'read' },
    { resource: 'project', action: 'create' },
    { resource: 'project', action: 'update' },
    { resource: 'project', action: 'delete' },
    { resource: 'project', action: 'export' },
    { resource: 'canvas', action: 'read' },
    { resource: 'canvas', action: 'create' },
    { resource: 'canvas', action: 'update' },
    { resource: 'canvas', action: 'delete' },
    { resource: 'page', action: 'read' },
    { resource: 'page', action: 'create' },
    { resource: 'page', action: 'update' },
    { resource: 'page', action: 'delete' },
    { resource: 'workflow', action: 'read' },
    { resource: 'workflow', action: 'create' },
    { resource: 'workflow', action: 'update' },
    { resource: 'workflow', action: 'delete' },
    { resource: 'member', action: 'read' },
    { resource: 'member', action: 'invite' },
    { resource: 'member', action: 'manage_members' },
    { resource: 'audit', action: 'read' },
    { resource: 'ai', action: 'read' },
    { resource: 'ai', action: 'ai_generate' },
  ],
  OWNER: [
    // All permissions
    { resource: 'workspace', action: 'read' },
    { resource: 'workspace', action: 'create' },
    { resource: 'workspace', action: 'update' },
    { resource: 'workspace', action: 'delete' },
    { resource: 'workspace', action: 'manage_members' },
    { resource: 'workspace', action: 'invite' },
    { resource: 'project', action: 'read' },
    { resource: 'project', action: 'create' },
    { resource: 'project', action: 'update' },
    { resource: 'project', action: 'delete' },
    { resource: 'project', action: 'export' },
    { resource: 'canvas', action: 'read' },
    { resource: 'canvas', action: 'create' },
    { resource: 'canvas', action: 'update' },
    { resource: 'canvas', action: 'delete' },
    { resource: 'page', action: 'read' },
    { resource: 'page', action: 'create' },
    { resource: 'page', action: 'update' },
    { resource: 'page', action: 'delete' },
    { resource: 'workflow', action: 'read' },
    { resource: 'workflow', action: 'create' },
    { resource: 'workflow', action: 'update' },
    { resource: 'workflow', action: 'delete' },
    { resource: 'member', action: 'read' },
    { resource: 'member', action: 'invite' },
    { resource: 'member', action: 'manage_members' },
    { resource: 'audit', action: 'read' },
    { resource: 'audit', action: 'export' },
    { resource: 'ai', action: 'read' },
    { resource: 'ai', action: 'ai_generate' },
  ],
};

/**
 * Returns the permission set for a given role.
 */
export function getPermissions(role: UserRole): Permission[] {
  return PERMISSION_MATRIX[role] ?? [];
}

/**
 * Check whether a role is allowed to perform an action on a resource.
 */
export function isAllowed(
  role: UserRole,
  resource: ResourceType,
  action: ActionType
): boolean {
  const permissions = getPermissions(role);
  return permissions.some(
    (p) => p.resource === resource && p.action === action
  );
}
