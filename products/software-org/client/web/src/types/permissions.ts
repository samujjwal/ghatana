/**
 * Permissions & RBAC Type Definitions
 *
 * Defines the permission system for role-based access control.
 * Follows the principle of least privilege.
 *
 * @package @ghatana/software-org-web
 */

/**
 * System roles with hierarchical permissions
 */
export type SystemRole = 'admin' | 'lead' | 'engineer' | 'viewer';

/**
 * Permission actions
 */
export type PermissionAction = 
  | 'create'
  | 'read'
  | 'update'
  | 'delete'
  | 'approve'
  | 'deploy'
  | 'configure'
  | 'execute';

/**
 * Resource types that can be permission-controlled
 */
export type PermissionResource =
  | 'organization'
  | 'department'
  | 'team'
  | 'service'
  | 'persona'
  | 'role'
  | 'workflow'
  | 'agent'
  | 'incident'
  | 'queue'
  | 'kpi'
  | 'report'
  | 'policy'
  | 'deployment'
  | 'hitl-action';

/**
 * Permission scope (where the permission applies)
 */
export interface PermissionScope {
  tenantId?: string;
  departmentId?: string;
  teamId?: string;
  serviceId?: string;
}

/**
 * Single permission definition
 */
export interface Permission {
  resource: PermissionResource;
  action: PermissionAction;
  scope?: PermissionScope;
}

/**
 * Permission check result
 */
export interface PermissionCheckResult {
  allowed: boolean;
  reason?: string;
  requiredRole?: SystemRole;
}

/**
 * Role permission matrix
 * Defines what each role can do across all resources
 */
export interface RolePermissions {
  [key: string]: Permission[];
}

/**
 * Permission policy (for advanced rules)
 */
export interface PermissionPolicy {
  id: string;
  name: string;
  description: string;
  rules: PermissionRule[];
  active: boolean;
  priority: number;
}

/**
 * Permission rule (condition-based)
 */
export interface PermissionRule {
  id: string;
  condition: string; // e.g., "role === 'admin' && tenantId === user.tenantId"
  permissions: Permission[];
  effect: 'allow' | 'deny';
}

/**
 * User permission context
 */
export interface UserPermissionContext {
  userId: string;
  roles: SystemRole[];
  tenantId?: string;
  departmentId?: string;
  teamId?: string;
  customPermissions?: Permission[];
}

/**
 * Navigation item permission requirements
 */
export interface NavItemPermission {
  path: string;
  requiredRole?: SystemRole;
  requiredPermission?: Permission;
  hide?: boolean; // Hide vs disable
}

/**
 * Action button permission requirements
 */
export interface ActionPermission {
  action: string;
  requiredRole?: SystemRole;
  requiredPermission?: Permission;
  disabled?: boolean;
  tooltip?: string; // Tooltip when disabled
}

/**
 * Permission matrix for all system roles
 */
export const ROLE_PERMISSION_MATRIX: Record<SystemRole, RolePermissions> = {
  admin: {
    organization: [
      { resource: 'organization', action: 'create' },
      { resource: 'organization', action: 'read' },
      { resource: 'organization', action: 'update' },
      { resource: 'organization', action: 'delete' },
      { resource: 'organization', action: 'configure' },
    ],
    department: [
      { resource: 'department', action: 'create' },
      { resource: 'department', action: 'read' },
      { resource: 'department', action: 'update' },
      { resource: 'department', action: 'delete' },
    ],
    team: [
      { resource: 'team', action: 'create' },
      { resource: 'team', action: 'read' },
      { resource: 'team', action: 'update' },
      { resource: 'team', action: 'delete' },
    ],
    service: [
      { resource: 'service', action: 'create' },
      { resource: 'service', action: 'read' },
      { resource: 'service', action: 'update' },
      { resource: 'service', action: 'delete' },
      { resource: 'service', action: 'deploy' },
    ],
    persona: [
      { resource: 'persona', action: 'create' },
      { resource: 'persona', action: 'read' },
      { resource: 'persona', action: 'update' },
      { resource: 'persona', action: 'delete' },
      { resource: 'persona', action: 'configure' },
    ],
    role: [
      { resource: 'role', action: 'create' },
      { resource: 'role', action: 'read' },
      { resource: 'role', action: 'update' },
      { resource: 'role', action: 'delete' },
    ],
    workflow: [
      { resource: 'workflow', action: 'create' },
      { resource: 'workflow', action: 'read' },
      { resource: 'workflow', action: 'update' },
      { resource: 'workflow', action: 'delete' },
      { resource: 'workflow', action: 'execute' },
      { resource: 'workflow', action: 'approve' },
    ],
    agent: [
      { resource: 'agent', action: 'create' },
      { resource: 'agent', action: 'read' },
      { resource: 'agent', action: 'update' },
      { resource: 'agent', action: 'delete' },
      { resource: 'agent', action: 'configure' },
      { resource: 'agent', action: 'execute' },
    ],
    incident: [
      { resource: 'incident', action: 'create' },
      { resource: 'incident', action: 'read' },
      { resource: 'incident', action: 'update' },
      { resource: 'incident', action: 'delete' },
    ],
    queue: [
      { resource: 'queue', action: 'create' },
      { resource: 'queue', action: 'read' },
      { resource: 'queue', action: 'update' },
      { resource: 'queue', action: 'delete' },
    ],
    kpi: [
      { resource: 'kpi', action: 'create' },
      { resource: 'kpi', action: 'read' },
      { resource: 'kpi', action: 'update' },
      { resource: 'kpi', action: 'delete' },
      { resource: 'kpi', action: 'configure' },
    ],
    report: [
      { resource: 'report', action: 'create' },
      { resource: 'report', action: 'read' },
      { resource: 'report', action: 'update' },
      { resource: 'report', action: 'delete' },
    ],
    policy: [
      { resource: 'policy', action: 'create' },
      { resource: 'policy', action: 'read' },
      { resource: 'policy', action: 'update' },
      { resource: 'policy', action: 'delete' },
      { resource: 'policy', action: 'configure' },
    ],
    deployment: [
      { resource: 'deployment', action: 'create' },
      { resource: 'deployment', action: 'read' },
      { resource: 'deployment', action: 'approve' },
      { resource: 'deployment', action: 'deploy' },
    ],
    'hitl-action': [
      { resource: 'hitl-action', action: 'read' },
      { resource: 'hitl-action', action: 'approve' },
    ],
  },
  lead: {
    organization: [{ resource: 'organization', action: 'read' }],
    department: [
      { resource: 'department', action: 'read' },
      { resource: 'department', action: 'update' },
    ],
    team: [
      { resource: 'team', action: 'create' },
      { resource: 'team', action: 'read' },
      { resource: 'team', action: 'update' },
    ],
    service: [
      { resource: 'service', action: 'create' },
      { resource: 'service', action: 'read' },
      { resource: 'service', action: 'update' },
      { resource: 'service', action: 'deploy' },
    ],
    persona: [
      { resource: 'persona', action: 'read' },
      { resource: 'persona', action: 'update' },
    ],
    role: [{ resource: 'role', action: 'read' }],
    workflow: [
      { resource: 'workflow', action: 'create' },
      { resource: 'workflow', action: 'read' },
      { resource: 'workflow', action: 'update' },
      { resource: 'workflow', action: 'execute' },
      { resource: 'workflow', action: 'approve' },
    ],
    agent: [
      { resource: 'agent', action: 'create' },
      { resource: 'agent', action: 'read' },
      { resource: 'agent', action: 'update' },
      { resource: 'agent', action: 'execute' },
    ],
    incident: [
      { resource: 'incident', action: 'create' },
      { resource: 'incident', action: 'read' },
      { resource: 'incident', action: 'update' },
    ],
    queue: [
      { resource: 'queue', action: 'create' },
      { resource: 'queue', action: 'read' },
      { resource: 'queue', action: 'update' },
    ],
    kpi: [
      { resource: 'kpi', action: 'read' },
      { resource: 'kpi', action: 'update' },
    ],
    report: [
      { resource: 'report', action: 'create' },
      { resource: 'report', action: 'read' },
    ],
    policy: [{ resource: 'policy', action: 'read' }],
    deployment: [
      { resource: 'deployment', action: 'create' },
      { resource: 'deployment', action: 'read' },
      { resource: 'deployment', action: 'approve' },
      { resource: 'deployment', action: 'deploy' },
    ],
    'hitl-action': [
      { resource: 'hitl-action', action: 'read' },
      { resource: 'hitl-action', action: 'approve' },
    ],
  },
  engineer: {
    organization: [{ resource: 'organization', action: 'read' }],
    department: [{ resource: 'department', action: 'read' }],
    team: [{ resource: 'team', action: 'read' }],
    service: [
      { resource: 'service', action: 'read' },
      { resource: 'service', action: 'update' },
    ],
    persona: [{ resource: 'persona', action: 'read' }],
    role: [{ resource: 'role', action: 'read' }],
    workflow: [
      { resource: 'workflow', action: 'create' },
      { resource: 'workflow', action: 'read' },
      { resource: 'workflow', action: 'update' },
      { resource: 'workflow', action: 'execute' },
    ],
    agent: [
      { resource: 'agent', action: 'create' },
      { resource: 'agent', action: 'read' },
      { resource: 'agent', action: 'execute' },
    ],
    incident: [
      { resource: 'incident', action: 'create' },
      { resource: 'incident', action: 'read' },
      { resource: 'incident', action: 'update' },
    ],
    queue: [
      { resource: 'queue', action: 'read' },
      { resource: 'queue', action: 'update' },
    ],
    kpi: [{ resource: 'kpi', action: 'read' }],
    report: [{ resource: 'report', action: 'read' }],
    policy: [{ resource: 'policy', action: 'read' }],
    deployment: [
      { resource: 'deployment', action: 'create' },
      { resource: 'deployment', action: 'read' },
    ],
    'hitl-action': [{ resource: 'hitl-action', action: 'read' }],
  },
  viewer: {
    organization: [{ resource: 'organization', action: 'read' }],
    department: [{ resource: 'department', action: 'read' }],
    team: [{ resource: 'team', action: 'read' }],
    service: [{ resource: 'service', action: 'read' }],
    persona: [{ resource: 'persona', action: 'read' }],
    role: [{ resource: 'role', action: 'read' }],
    workflow: [{ resource: 'workflow', action: 'read' }],
    agent: [{ resource: 'agent', action: 'read' }],
    incident: [{ resource: 'incident', action: 'read' }],
    queue: [{ resource: 'queue', action: 'read' }],
    kpi: [{ resource: 'kpi', action: 'read' }],
    report: [{ resource: 'report', action: 'read' }],
    policy: [{ resource: 'policy', action: 'read' }],
    deployment: [{ resource: 'deployment', action: 'read' }],
    'hitl-action': [{ resource: 'hitl-action', action: 'read' }],
  },
};
