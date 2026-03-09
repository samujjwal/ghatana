/**
 * Permissions & RBAC Hook
 *
 * Provides permission checking utilities for role-based access control.
 * Integrates with the backend RBAC system.
 *
 * @package @ghatana/software-org-web
 */

import { useMemo } from 'react';
import {
  type SystemRole,
  type Permission,
  type PermissionAction,
  type PermissionResource,
  type PermissionScope,
  type PermissionCheckResult,
  type UserPermissionContext,
  ROLE_PERMISSION_MATRIX,
} from '@/types/permissions';

/**
 * Mock current user context (TODO: Replace with actual auth context)
 */
const getCurrentUserContext = (): UserPermissionContext => {
  // TODO: Replace with actual auth context from useAuth() or similar
  return {
    userId: 'user-1',
    roles: ['admin'], // Default to admin for development
    tenantId: 'tenant-1',
  };
};

/**
 * Hook for permission checking
 */
export function usePermissions() {
  const userContext = getCurrentUserContext();

  /**
   * Get the highest role in the hierarchy
   */
  const getHighestRole = (roles: SystemRole[]): SystemRole => {
    const roleHierarchy: SystemRole[] = ['admin', 'lead', 'engineer', 'viewer'];
    for (const role of roleHierarchy) {
      if (roles.includes(role)) {
        return role;
      }
    }
    return 'viewer';
  };

  /**
   * Get all permissions for current user
   */
  const userPermissions = useMemo(() => {
    const allPermissions: Permission[] = [];

    // Get permissions from all assigned roles
    for (const role of userContext.roles) {
      const rolePerms = ROLE_PERMISSION_MATRIX[role];
      if (rolePerms) {
        Object.values(rolePerms).forEach((perms) => {
          allPermissions.push(...perms);
        });
      }
    }

    // Add custom permissions if any
    if (userContext.customPermissions) {
      allPermissions.push(...userContext.customPermissions);
    }

    return allPermissions;
  }, [userContext.roles, userContext.customPermissions]);

  /**
   * Check if user has a specific permission
   */
  const hasPermission = (
    resource: PermissionResource,
    action: PermissionAction,
    scope?: PermissionScope
  ): boolean => {
    // Admin has all permissions
    if (userContext.roles.includes('admin')) {
      return true;
    }

    // Check if user has the permission
    const hasMatch = userPermissions.some((perm) => {
      // Check resource and action
      if (perm.resource !== resource || perm.action !== action) {
        return false;
      }

      // If no scope specified, permission applies globally
      if (!scope && !perm.scope) {
        return true;
      }

      // Check scope constraints
      if (scope && perm.scope) {
        // If permission has tenantId scope, check it matches
        if (perm.scope.tenantId && scope.tenantId !== perm.scope.tenantId) {
          return false;
        }
        // If permission has departmentId scope, check it matches
        if (perm.scope.departmentId && scope.departmentId !== perm.scope.departmentId) {
          return false;
        }
        // If permission has teamId scope, check it matches
        if (perm.scope.teamId && scope.teamId !== perm.scope.teamId) {
          return false;
        }
        // If permission has serviceId scope, check it matches
        if (perm.scope.serviceId && scope.serviceId !== perm.scope.serviceId) {
          return false;
        }
      }

      return true;
    });

    return hasMatch;
  };

  /**
   * Check permission with detailed result
   */
  const checkPermission = (
    resource: PermissionResource,
    action: PermissionAction,
    scope?: PermissionScope
  ): PermissionCheckResult => {
    const allowed = hasPermission(resource, action, scope);

    if (allowed) {
      return { allowed: true };
    }

    // Find the minimum role required for this permission
    const roleHierarchy: SystemRole[] = ['viewer', 'engineer', 'lead', 'admin'];
    let requiredRole: SystemRole | undefined;

    for (const role of roleHierarchy) {
      const rolePerms = ROLE_PERMISSION_MATRIX[role];
      const resourcePerms = rolePerms?.[resource];
      if (resourcePerms?.some((p) => p.action === action)) {
        requiredRole = role;
        break;
      }
    }

    return {
      allowed: false,
      reason: `Requires ${requiredRole || 'higher'} role to ${action} ${resource}`,
      requiredRole,
    };
  };

  /**
   * Check if user has role
   */
  const hasRole = (role: SystemRole): boolean => {
    return userContext.roles.includes(role);
  };

  /**
   * Check if user has any of the roles
   */
  const hasAnyRole = (roles: SystemRole[]): boolean => {
    return roles.some((role) => userContext.roles.includes(role));
  };

  /**
   * Check if user has all of the roles
   */
  const hasAllRoles = (roles: SystemRole[]): boolean => {
    return roles.every((role) => userContext.roles.includes(role));
  };

  /**
   * Check if user can access a route
   */
  const canAccessRoute = (path: string): boolean => {
    // Admin routes
    if (path.startsWith('/admin')) {
      return hasRole('admin');
    }

    // Build routes (workflow/agent creation)
    if (path.startsWith('/build')) {
      return hasAnyRole(['admin', 'lead', 'engineer']);
    }

    // Operate routes (viewing dashboards)
    if (path.startsWith('/operate')) {
      return true; // All roles can view
    }

    // Observe routes (viewing metrics)
    if (path.startsWith('/observe')) {
      return true; // All roles can view
    }

    // Default: allow access
    return true;
  };

  /**
   * Check if user can perform an action
   */
  const canPerformAction = (
    actionType: 'create' | 'edit' | 'delete' | 'approve' | 'deploy' | 'execute',
    resource: PermissionResource,
    scope?: PermissionScope
  ): boolean => {
    const actionMap: Record<string, PermissionAction> = {
      create: 'create',
      edit: 'update',
      delete: 'delete',
      approve: 'approve',
      deploy: 'deploy',
      execute: 'execute',
    };

    return hasPermission(resource, actionMap[actionType], scope);
  };

  return {
    // User context
    userContext,
    highestRole: getHighestRole(userContext.roles),
    permissions: userPermissions,

    // Permission checking
    hasPermission,
    checkPermission,

    // Role checking
    hasRole,
    hasAnyRole,
    hasAllRoles,

    // Route access
    canAccessRoute,

    // Action checking
    canPerformAction,

    // Convenience helpers
    isAdmin: hasRole('admin'),
    isLead: hasRole('lead'),
    isEngineer: hasRole('engineer'),
    isViewer: hasRole('viewer'),

    // Resource-specific helpers
    canCreateWorkflow: canPerformAction('create', 'workflow'),
    canEditWorkflow: canPerformAction('edit', 'workflow'),
    canDeleteWorkflow: canPerformAction('delete', 'workflow'),
    canExecuteWorkflow: canPerformAction('execute', 'workflow'),
    canApproveWorkflow: canPerformAction('approve', 'workflow'),

    canCreateAgent: canPerformAction('create', 'agent'),
    canEditAgent: canPerformAction('edit', 'agent'),
    canDeleteAgent: canPerformAction('delete', 'agent'),
    canExecuteAgent: canPerformAction('execute', 'agent'),

    canCreateIncident: canPerformAction('create', 'incident'),
    canEditIncident: canPerformAction('edit', 'incident'),
    canDeleteIncident: canPerformAction('delete', 'incident'),

    canApproveDeployment: canPerformAction('approve', 'deployment'),
    canTriggerDeployment: canPerformAction('deploy', 'deployment'),

    canConfigureOrg: hasPermission('organization', 'configure'),
    canManageRoles: hasPermission('role', 'create'),
    canManagePolicies: hasPermission('policy', 'configure'),
  };
}

/**
 * Hook to get permission tooltip for disabled actions
 */
export function usePermissionTooltip() {
  const { checkPermission } = usePermissions();

  const getTooltip = (
    resource: PermissionResource,
    action: PermissionAction,
    defaultTooltip?: string
  ): string | undefined => {
    const result = checkPermission(resource, action);
    if (result.allowed) {
      return defaultTooltip;
    }
    return result.reason;
  };

  return { getTooltip };
}
