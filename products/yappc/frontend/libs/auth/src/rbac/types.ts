/**
 * Role-Based Access Control (RBAC) Types
 * Type definitions for RBAC system
 */

/**
 * Permission interface
 * Represents a single permission in the system
 */
export interface Permission {
  /** Permission ID */
  id: string;
  /** Permission name */
  name: string;
  /** Permission description */
  description: string;
  /** Resource this permission applies to */
  resource: string;
  /** Action this permission allows */
  action: string;
}

/**
 * Role interface
 * Represents a role with associated permissions
 */
export interface Role {
  /** Role ID */
  id: string;
  /** Role name */
  name: string;
  /** Role description */
  description: string;
  /** Permissions assigned to this role */
  permissions: Permission[];
  /** Whether this is a system role (cannot be deleted) */
  isSystem: boolean;
}

/**
 * User role interface
 * Represents a user's role assignment
 */
export interface UserRole {
  /** User ID */
  userId: string;
  /** Role ID */
  roleId: string;
  /** Role object */
  role: Role;
  /** When the role was assigned */
  assignedAt: Date;
  /** When the role expires (if applicable) */
  expiresAt?: Date;
}

/**
 * Access control list (ACL) interface
 * Represents access control for a resource
 */
export interface AccessControlList {
  /** Resource ID */
  resourceId: string;
  /** Resource type */
  resourceType: string;
  /** Owner user ID */
  ownerId: string;
  /** Public access level (none, read, write) */
  publicAccess: 'none' | 'read' | 'write';
  /** User-specific permissions */
  userPermissions: Record<string, string[]>;
  /** Role-specific permissions */
  rolePermissions: Record<string, string[]>;
}

/**
 * Authorization context interface
 * Context for authorization decisions
 */
export interface AuthorizationContext {
  /** User ID */
  userId: string;
  /** User roles */
  roles: Role[];
  /** User permissions */
  permissions: Permission[];
  /** Resource being accessed */
  resource: string;
  /** Action being performed */
  action: string;
  /** Additional context data */
  context?: Record<string, unknown>;
}

/**
 * Authorization decision interface
 * Result of an authorization check
 */
export interface AuthorizationDecision {
  /** Is access allowed */
  allowed: boolean;
  /** Reason for the decision */
  reason: string;
  /** Matching permissions (if allowed) */
  matchingPermissions: Permission[];
}

/**
 * Policy interface
 * Represents an authorization policy
 */
export interface Policy {
  /** Policy ID */
  id: string;
  /** Policy name */
  name: string;
  /** Policy description */
  description: string;
  /** Policy rules */
  rules: PolicyRule[];
  /** Whether policy is active */
  active: boolean;
}

/**
 * Policy rule interface
 * Represents a single rule in a policy
 */
export interface PolicyRule {
  /** Rule ID */
  id: string;
  /** Effect (allow or deny) */
  effect: 'allow' | 'deny';
  /** Principal (who) */
  principal: string;
  /** Action (what) */
  action: string;
  /** Resource (on what) */
  resource: string;
  /** Conditions for the rule */
  conditions?: Record<string, unknown>;
}
