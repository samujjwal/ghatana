/**
 * Permission Testing Utilities
 *
 * Provides mock contexts and helpers for testing RBAC functionality.
 * Use these utilities to test permission-protected components and routes.
 *
 * @package @ghatana/software-org-web
 */

import { type SystemRole, type UserPermissionContext } from '@/types/permissions';

/**
 * Mock permission contexts for different roles
 */
export const mockPermissionContexts: Record<SystemRole, UserPermissionContext> = {
  admin: {
    userId: 'admin-user-1',
    roles: ['admin'],
    tenantId: 'test-tenant-1',
    departmentId: 'test-dept-1',
  },
  lead: {
    userId: 'lead-user-1',
    roles: ['lead'],
    tenantId: 'test-tenant-1',
    departmentId: 'test-dept-1',
    teamId: 'test-team-1',
  },
  engineer: {
    userId: 'engineer-user-1',
    roles: ['engineer'],
    tenantId: 'test-tenant-1',
    departmentId: 'test-dept-1',
    teamId: 'test-team-1',
  },
  viewer: {
    userId: 'viewer-user-1',
    roles: ['viewer'],
    tenantId: 'test-tenant-1',
  },
};

/**
 * Mock user with multiple roles
 */
export const mockMultiRoleContext: UserPermissionContext = {
  userId: 'multi-role-user-1',
  roles: ['lead', 'engineer'],
  tenantId: 'test-tenant-1',
  departmentId: 'test-dept-1',
  teamId: 'test-team-1',
};

/**
 * Mock getCurrentUserContext for testing
 */
export function mockGetCurrentUserContext(role: SystemRole): UserPermissionContext {
  return mockPermissionContexts[role];
}

/**
 * Setup permission mock for usePermissions hook
 * 
 * @example
 * ```typescript
 * import { vi } from 'vitest';
 * import { mockPermissions } from '@/tests/utils/permissionMocks';
 * 
 * vi.mock('@/hooks/usePermissions', () => ({
 *   usePermissions: () => mockPermissions('admin'),
 * }));
 * ```
 */
export function mockPermissions(role: SystemRole) {
  const isAdmin = role === 'admin';
  const isLead = role === 'lead';
  const isEngineer = role === 'engineer';
  const isViewer = role === 'viewer';

  return {
    userContext: mockPermissionContexts[role],
    highestRole: role,
    permissions: [],

    // Permission checking
    hasPermission: (resource: string, action: string) => {
      if (isAdmin) return true;
      if (isViewer) return action === 'read';
      if (isEngineer) return ['read', 'create', 'update', 'execute'].includes(action);
      if (isLead) return ['read', 'create', 'update', 'execute', 'approve'].includes(action);
      return false;
    },
    checkPermission: (resource: string, action: string) => ({
      allowed: isAdmin || (isViewer && action === 'read'),
      reason: undefined,
      requiredRole: undefined,
    }),

    // Role checking
    hasRole: (checkRole: SystemRole) => role === checkRole,
    hasAnyRole: (roles: SystemRole[]) => roles.includes(role),
    hasAllRoles: (roles: SystemRole[]) => roles.every((r) => r === role),

    // Route access
    canAccessRoute: (path: string) => {
      if (path.startsWith('/admin')) return isAdmin;
      if (path.startsWith('/build')) return !isViewer;
      return true;
    },

    // Action checking
    canPerformAction: (actionType: string, resource: string) => {
      if (isAdmin) return true;
      if (isViewer) return false;
      if (isEngineer) return ['create', 'edit', 'execute'].includes(actionType);
      if (isLead) return ['create', 'edit', 'execute', 'approve', 'deploy'].includes(actionType);
      return false;
    },

    // Convenience helpers
    isAdmin,
    isLead,
    isEngineer,
    isViewer,

    // Resource-specific helpers
    canCreateWorkflow: !isViewer,
    canEditWorkflow: !isViewer,
    canDeleteWorkflow: isAdmin || isLead,
    canExecuteWorkflow: !isViewer,
    canApproveWorkflow: isAdmin || isLead,

    canCreateAgent: !isViewer,
    canEditAgent: !isViewer,
    canDeleteAgent: isAdmin || isLead,
    canExecuteAgent: !isViewer,

    canCreateIncident: !isViewer,
    canEditIncident: !isViewer,
    canDeleteIncident: isAdmin || isLead,

    canApproveDeployment: isAdmin || isLead,
    canTriggerDeployment: !isViewer,

    canConfigureOrg: isAdmin,
    canManageRoles: isAdmin,
    canManagePolicies: isAdmin,
  };
}

/**
 * Test data builders for permission-protected resources
 */
export const testDataBuilders = {
  workflow: (overrides = {}) => ({
    id: 'test-workflow-1',
    tenantId: 'test-tenant-1',
    name: 'Test Workflow',
    slug: 'test-workflow',
    description: 'Test workflow for permissions',
    status: 'draft',
    steps: [],
    serviceIds: [],
    policyIds: [],
    ...overrides,
  }),

  agent: (overrides = {}) => ({
    id: 'test-agent-1',
    tenantId: 'test-tenant-1',
    name: 'Test Agent',
    slug: 'test-agent',
    description: 'Test agent for permissions',
    status: 'draft',
    type: 'incident-triage',
    tools: [],
    serviceIds: [],
    ...overrides,
  }),

  team: (overrides = {}) => ({
    id: 'test-team-1',
    tenantId: 'test-tenant-1',
    departmentId: 'test-dept-1',
    name: 'Test Team',
    slug: 'test-team',
    description: 'Test team',
    status: 'ACTIVE',
    ...overrides,
  }),

  role: (overrides = {}) => ({
    id: 'test-role-1',
    tenantId: 'test-tenant-1',
    name: 'Test Role',
    slug: 'test-role',
    description: 'Test role',
    permissions: ['read', 'write'],
    scopes: [],
    isSystem: false,
    active: true,
    ...overrides,
  }),
};

/**
 * Assert permission denied helper
 */
export function assertPermissionDenied(element: HTMLElement) {
  expect(element).toBeDisabled();
  expect(element).toHaveAttribute('title', expect.stringContaining('permission'));
}

/**
 * Assert element hidden based on permission
 */
export function assertHiddenByPermission(queryFn: () => HTMLElement | null) {
  expect(queryFn()).not.toBeInTheDocument();
}
