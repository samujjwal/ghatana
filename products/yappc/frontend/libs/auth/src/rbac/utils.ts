/**
 * Role-Based Access Control (RBAC) Utilities
 * Helper functions for RBAC operations
 */

import type {
  Role,
  Permission,
  AuthorizationContext,
  AuthorizationDecision,
  AccessControlList,
} from './types';

/**
 * RBAC utilities class
 * Static methods for RBAC operations
 */
export class RBACUtils {
  /**
   * Check if user has permission
   * @param context - Authorization context
   * @returns Authorization decision
   */
  static authorize(context: AuthorizationContext): AuthorizationDecision {
    const matchingPermissions = context.permissions.filter(
      perm => perm.resource === context.resource && perm.action === context.action
    );

    if (matchingPermissions.length > 0) {
      return {
        allowed: true,
        reason: 'User has required permission',
        matchingPermissions,
      };
    }

    return {
      allowed: false,
      reason: 'User does not have required permission',
      matchingPermissions: [],
    };
  }

  /**
   * Get user permissions from roles
   * @param roles - User roles
   * @returns Flattened permissions array
   */
  static getPermissionsFromRoles(roles: Role[]): Permission[] {
    const permissionsMap = new Map<string, Permission>();

    roles.forEach(role => {
      role.permissions.forEach(permission => {
        permissionsMap.set(permission.id, permission);
      });
    });

    return Array.from(permissionsMap.values());
  }

  /**
   * Check if user has role
   * @param roles - User roles
   * @param roleId - Role ID to check
   * @returns True if user has role
   */
  static hasRole(roles: Role[], roleId: string): boolean {
    return roles.some(role => role.id === roleId);
  }

  /**
   * Check if user has any of the roles
   * @param roles - User roles
   * @param roleIds - Role IDs to check
   * @returns True if user has any of the roles
   */
  static hasAnyRole(roles: Role[], roleIds: string[]): boolean {
    return roleIds.some(roleId => roles.some(role => role.id === roleId));
  }

  /**
   * Check if user has all of the roles
   * @param roles - User roles
   * @param roleIds - Role IDs to check
   * @returns True if user has all of the roles
   */
  static hasAllRoles(roles: Role[], roleIds: string[]): boolean {
    return roleIds.every(roleId => roles.some(role => role.id === roleId));
  }

  /**
   * Create role with permissions
   * @param name - Role name
   * @param description - Role description
   * @param permissions - Permissions for role
   * @returns Created role
   */
  static createRole(name: string, description: string, permissions: Permission[]): Role {
    return {
      id: `role-${Date.now()}`,
      name,
      description,
      permissions,
      isSystem: false,
    };
  }

  /**
   * Add permission to role
   * @param role - Role to modify
   * @param permission - Permission to add
   * @returns Modified role
   */
  static addPermissionToRole(role: Role, permission: Permission): Role {
    if (role.permissions.some(p => p.id === permission.id)) {
      return role;
    }

    return {
      ...role,
      permissions: [...role.permissions, permission],
    };
  }

  /**
   * Remove permission from role
   * @param role - Role to modify
   * @param permissionId - Permission ID to remove
   * @returns Modified role
   */
  static removePermissionFromRole(role: Role, permissionId: string): Role {
    return {
      ...role,
      permissions: role.permissions.filter(p => p.id !== permissionId),
    };
  }

  /**
   * Check ACL access
   * @param acl - Access control list
   * @param userId - User ID
   * @param action - Action to perform
   * @returns True if access is allowed
   */
  static checkACLAccess(acl: AccessControlList, userId: string, action: string): boolean {
    // Owner has full access
    if (acl.ownerId === userId) {
      return true;
    }

    // Check user-specific permissions
    const userPermissions = acl.userPermissions[userId] || [];
    if (userPermissions.includes(action)) {
      return true;
    }

    // Check public access
    if (acl.publicAccess === 'write' || (acl.publicAccess === 'read' && action === 'read')) {
      return true;
    }

    return false;
  }

  /**
   * Grant access to user
   * @param acl - Access control list
   * @param userId - User ID
   * @param action - Action to grant
   * @returns Modified ACL
   */
  static grantAccess(acl: AccessControlList, userId: string, action: string): AccessControlList {
    const userPermissions = acl.userPermissions[userId] || [];

    if (!userPermissions.includes(action)) {
      userPermissions.push(action);
    }

    return {
      ...acl,
      userPermissions: {
        ...acl.userPermissions,
        [userId]: userPermissions,
      },
    };
  }

  /**
   * Revoke access from user
   * @param acl - Access control list
   * @param userId - User ID
   * @param action - Action to revoke
   * @returns Modified ACL
   */
  static revokeAccess(acl: AccessControlList, userId: string, action: string): AccessControlList {
    const userPermissions = (acl.userPermissions[userId] || []).filter(a => a !== action);

    return {
      ...acl,
      userPermissions: {
        ...acl.userPermissions,
        [userId]: userPermissions,
      },
    };
  }
}
