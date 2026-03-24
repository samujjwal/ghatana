/**
 * Role-Based Access Control (RBAC) Enforcement
 *
 * Provides comprehensive RBAC enforcement for canvas operations with:
 * - Permission checking for CRUD operations
 * - Role-based policy management
 * - Field-level sensitive data redaction
 * - Audit logging for denied access attempts
 * - Resource-level access control
 *
 * @module libs/canvas/src/security/rbacEnforcement
 */

/**
 * Permission types
 */
export type Permission = 'read' | 'write' | 'delete' | 'admin';

/**
 * Built-in role types
 */
export type BuiltInRole = 'admin' | 'editor' | 'viewer' | 'commenter';

/**
 * Resource types
 */
export type ResourceType = 'canvas' | 'node' | 'edge' | 'document' | 'settings';

/**
 * Action types for audit logging
 */
export type Action =
  | 'read'
  | 'create'
  | 'update'
  | 'delete'
  | 'permission_change'
  | 'redaction';

/**
 * Audit log entry for denied access attempts
 */
export interface AccessDeniedEntry {
  /** Unique entry ID */
  id: string;
  /** Timestamp */
  timestamp: number;
  /** User ID */
  userId: string;
  /** User role */
  role: string;
  /** Attempted action */
  action: Action;
  /** Resource type */
  resourceType: ResourceType;
  /** Resource ID */
  resourceId: string;
  /** Required permissions */
  requiredPermissions: Permission[];
  /** Reason for denial */
  reason: string;
  /** Additional context */
  context?: Record<string, unknown>;
}

/**
 * Role definition with permissions
 */
export interface RoleDefinition {
  /** Role name */
  name: string;
  /** Display name */
  displayName: string;
  /** Description */
  description: string;
  /** Granted permissions */
  permissions: Permission[];
  /** Inherits from parent role */
  inheritsFrom?: string;
  /** Whether role can be modified */
  isBuiltIn: boolean;
}

/**
 * Field redaction rule
 */
export interface RedactionRule {
  /** Field path (supports nested with dot notation) */
  fieldPath: string;
  /** Roles that can see unredacted value */
  allowedRoles: string[];
  /** Redaction strategy */
  strategy: 'remove' | 'mask' | 'hash';
  /** Replacement value for mask strategy */
  maskValue?: string;
}

/**
 * Access policy for a resource
 */
export interface AccessPolicy {
  /** Resource type */
  resourceType: ResourceType;
  /** Resource ID */
  resourceId: string;
  /** Role-based permissions */
  rolePermissions: Map<string, Permission[]>;
  /** Field redaction rules */
  redactionRules: RedactionRule[];
  /** Policy metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Permission check result
 */
export interface PermissionCheckResult {
  /** Whether permission is granted */
  granted: boolean;
  /** Reason if denied */
  reason?: string;
  /** Required permissions that are missing */
  missingPermissions?: Permission[];
}

/**
 * RBAC configuration
 */
export interface RBACConfig {
  /** Enable RBAC enforcement */
  enabled: boolean;
  /** Enable audit logging */
  auditEnabled: boolean;
  /** Maximum audit log entries */
  maxAuditEntries: number;
  /** Default role for new users */
  defaultRole: string;
  /** Enable field-level redaction */
  redactionEnabled: boolean;
}

/**
 * RBAC enforcer state
 */
interface RBACState {
  /** Role definitions */
  roles: Map<string, RoleDefinition>;
  /** Access policies by resource */
  policies: Map<string, AccessPolicy>;
  /** Access denied audit log */
  auditLog: AccessDeniedEntry[];
  /** Configuration */
  config: RBACConfig;
}

/**
 * Default built-in roles
 */
const BUILT_IN_ROLES: RoleDefinition[] = [
  {
    name: 'admin',
    displayName: 'Administrator',
    description: 'Full access to all resources and operations',
    permissions: ['read', 'write', 'delete', 'admin'],
    isBuiltIn: true,
  },
  {
    name: 'editor',
    displayName: 'Editor',
    description: 'Can read and modify resources',
    permissions: ['read', 'write'],
    inheritsFrom: 'viewer',
    isBuiltIn: true,
  },
  {
    name: 'viewer',
    displayName: 'Viewer',
    description: 'Read-only access to resources',
    permissions: ['read'],
    isBuiltIn: true,
  },
  {
    name: 'commenter',
    displayName: 'Commenter',
    description: 'Can view and add comments',
    permissions: ['read'],
    inheritsFrom: 'viewer',
    isBuiltIn: true,
  },
];

/**
 * Default configuration
 */
const DEFAULT_CONFIG: RBACConfig = {
  enabled: true,
  auditEnabled: true,
  maxAuditEntries: 10000,
  defaultRole: 'viewer',
  redactionEnabled: true,
};

/**
 * RBAC Enforcer
 *
 * Provides role-based access control enforcement with permission checking,
 * field-level redaction, and audit logging.
 */
export class RBACEnforcer {
  private state: RBACState;

  /**
   *
   */
  constructor(config: Partial<RBACConfig> = {}) {
    this.state = {
      roles: new Map(),
      policies: new Map(),
      auditLog: [],
      config: {
        ...DEFAULT_CONFIG,
        ...config,
      },
    };

    // Register built-in roles
    for (const role of BUILT_IN_ROLES) {
      this.state.roles.set(role.name, role);
    }
  }

  // ==================== Role Management ====================

  /**
   * Define custom role
   */
  defineRole(
    name: string,
    displayName: string,
    description: string,
    permissions: Permission[],
    inheritsFrom?: string
  ): RoleDefinition {
    if (this.state.roles.has(name)) {
      const existing = this.state.roles.get(name)!;
      if (existing.isBuiltIn) {
        throw new Error(`Cannot redefine built-in role: ${name}`);
      }
    }

    // Validate inheritance
    if (inheritsFrom && !this.state.roles.has(inheritsFrom)) {
      throw new Error(`Parent role not found: ${inheritsFrom}`);
    }

    const role: RoleDefinition = {
      name,
      displayName,
      description,
      permissions,
      inheritsFrom,
      isBuiltIn: false,
    };

    this.state.roles.set(name, role);
    return role;
  }

  /**
   * Get role definition
   */
  getRole(name: string): RoleDefinition | undefined {
    return this.state.roles.get(name);
  }

  /**
   * Get all roles
   */
  getAllRoles(): RoleDefinition[] {
    return Array.from(this.state.roles.values());
  }

  /**
   * Delete custom role
   */
  deleteRole(name: string): boolean {
    const role = this.state.roles.get(name);
    if (!role) {
      return false;
    }

    if (role.isBuiltIn) {
      throw new Error(`Cannot delete built-in role: ${name}`);
    }

    return this.state.roles.delete(name);
  }

  /**
   * Get effective permissions for role (including inherited)
   */
  getEffectivePermissions(roleName: string): Permission[] {
    const role = this.state.roles.get(roleName);
    if (!role) {
      return [];
    }

    const permissions = new Set<Permission>(role.permissions);

    // Add inherited permissions
    if (role.inheritsFrom) {
      const parentPerms = this.getEffectivePermissions(role.inheritsFrom);
      for (const perm of parentPerms) {
        permissions.add(perm);
      }
    }

    return Array.from(permissions);
  }

  // ==================== Policy Management ====================

  /**
   * Create access policy for resource
   */
  createPolicy(
    resourceType: ResourceType,
    resourceId: string,
    redactionRules: RedactionRule[] = [],
    metadata?: Record<string, unknown>
  ): AccessPolicy {
    const policyKey = `${resourceType}:${resourceId}`;

    const policy: AccessPolicy = {
      resourceType,
      resourceId,
      rolePermissions: new Map(),
      redactionRules,
      metadata,
    };

    this.state.policies.set(policyKey, policy);
    return policy;
  }

  /**
   * Get policy for resource
   */
  getPolicy(resourceType: ResourceType, resourceId: string): AccessPolicy | undefined {
    const policyKey = `${resourceType}:${resourceId}`;
    return this.state.policies.get(policyKey);
  }

  /**
   * Grant permissions to role for resource
   */
  grantPermissions(
    resourceType: ResourceType,
    resourceId: string,
    roleName: string,
    permissions: Permission[]
  ): void {
    if (!this.state.roles.has(roleName)) {
      throw new Error(`Role not found: ${roleName}`);
    }

    const policyKey = `${resourceType}:${resourceId}`;
    let policy = this.state.policies.get(policyKey);

    if (!policy) {
      policy = this.createPolicy(resourceType, resourceId);
    }

    policy.rolePermissions.set(roleName, permissions);
    this.state.policies.set(policyKey, policy);
  }

  /**
   * Revoke permissions from role for resource
   */
  revokePermissions(
    resourceType: ResourceType,
    resourceId: string,
    roleName: string
  ): boolean {
    const policyKey = `${resourceType}:${resourceId}`;
    const policy = this.state.policies.get(policyKey);

    if (!policy) {
      return false;
    }

    return policy.rolePermissions.delete(roleName);
  }

  /**
   * Add redaction rule to policy
   */
  addRedactionRule(
    resourceType: ResourceType,
    resourceId: string,
    rule: RedactionRule
  ): void {
    const policyKey = `${resourceType}:${resourceId}`;
    let policy = this.state.policies.get(policyKey);

    if (!policy) {
      policy = this.createPolicy(resourceType, resourceId);
    }

    policy.redactionRules.push(rule);
    this.state.policies.set(policyKey, policy);
  }

  // ==================== Permission Checking ====================

  /**
   * Check if user has permission for action
   */
  hasPermission(
    userId: string,
    userRole: string,
    resourceType: ResourceType,
    resourceId: string,
    requiredPermissions: Permission[]
  ): PermissionCheckResult {
    if (!this.state.config.enabled) {
      return { granted: true };
    }

    // Validate role exists
    const role = this.state.roles.get(userRole);
    if (!role) {
      return {
        granted: false,
        reason: `Invalid role: ${userRole}`,
        missingPermissions: requiredPermissions,
      };
    }

    // Get effective permissions (including inherited)
    const effectivePermissions = this.getEffectivePermissions(userRole);

    // Check policy-specific permissions
    const policy = this.getPolicy(resourceType, resourceId);
    if (policy) {
      const policyPerms = policy.rolePermissions.get(userRole);
      if (policyPerms) {
        // Override with policy-specific permissions
        for (const perm of policyPerms) {
          effectivePermissions.push(perm);
        }
      }
    }

    // Check if all required permissions are present
    const missing: Permission[] = [];
    for (const required of requiredPermissions) {
      if (!effectivePermissions.includes(required)) {
        missing.push(required);
      }
    }

    if (missing.length > 0) {
      return {
        granted: false,
        reason: `Missing permissions: ${missing.join(', ')}`,
        missingPermissions: missing,
      };
    }

    return { granted: true };
  }

  /**
   * Check and enforce permission
   * Throws error if permission denied and logs to audit trail
   */
  enforcePermission(
    userId: string,
    userRole: string,
    action: Action,
    resourceType: ResourceType,
    resourceId: string,
    requiredPermissions: Permission[],
    context?: Record<string, unknown>
  ): void {
    const result = this.hasPermission(
      userId,
      userRole,
      resourceType,
      resourceId,
      requiredPermissions
    );

    if (!result.granted) {
      // Log denied access
      this.logAccessDenied(
        userId,
        userRole,
        action,
        resourceType,
        resourceId,
        requiredPermissions,
        result.reason || 'Permission denied',
        context
      );

      throw new Error(
        `Access denied for ${action} on ${resourceType}:${resourceId}: ${result.reason}`
      );
    }
  }

  // ==================== Field-Level Redaction ====================

  /**
   * Apply field-level redaction to data
   */
  applyRedaction<T extends Record<string, unknown>>(
    data: T,
    userRole: string,
    resourceType: ResourceType,
    resourceId: string
  ): T {
    if (!this.state.config.redactionEnabled) {
      return data;
    }

    const policy = this.getPolicy(resourceType, resourceId);
    if (!policy || policy.redactionRules.length === 0) {
      return data;
    }

    const redacted = { ...data };

    for (const rule of policy.redactionRules) {
      // Check if user's role is allowed to see this field
      if (rule.allowedRoles.includes(userRole)) {
        continue;
      }

      // Apply redaction based on strategy
      const fieldValue = this.getNestedField(redacted, rule.fieldPath);
      if (fieldValue === undefined) {
        continue;
      }

      switch (rule.strategy) {
        case 'remove':
          this.deleteNestedField(redacted, rule.fieldPath);
          break;
        case 'mask':
          this.setNestedField(
            redacted,
            rule.fieldPath,
            rule.maskValue || '[REDACTED]'
          );
          break;
        case 'hash':
          const hash = this.hashValue(fieldValue);
          this.setNestedField(redacted, rule.fieldPath, `[HASH:${hash}]`);
          break;
      }
    }

    return redacted;
  }

  /**
   * Get nested field value using dot notation
   */
  private getNestedField(obj: Record<string, unknown>, path: string): unknown {
    const parts = path.split('.');
    let current: unknown = obj;

    for (const part of parts) {
      if (current && typeof current === 'object' && part in current) {
        current = (current as Record<string, unknown>)[part];
      } else {
        return undefined;
      }
    }

    return current;
  }

  /**
   * Set nested field value using dot notation
   */
  private setNestedField(
    obj: Record<string, unknown>,
    path: string,
    value: unknown
  ): void {
    const parts = path.split('.');
    let current: Record<string, unknown> = obj;

    for (let i = 0; i < parts.length - 1; i++) {
      const part = parts[i];
      if (!(part in current)) {
        current[part] = {};
      }
      current = current[part] as Record<string, unknown>;
    }

    current[parts[parts.length - 1]] = value;
  }

  /**
   * Delete nested field using dot notation
   */
  private deleteNestedField(obj: Record<string, unknown>, path: string): void {
    const parts = path.split('.');
    let current: Record<string, unknown> = obj;

    for (let i = 0; i < parts.length - 1; i++) {
      const part = parts[i];
      if (!(part in current)) {
        return;
      }
      current = current[part] as Record<string, unknown>;
    }

    delete current[parts[parts.length - 1]];
  }

  /**
   * Simple hash function for values
   */
  private hashValue(value: unknown): string {
    const str = JSON.stringify(value);
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash; // Convert to 32-bit integer
    }
    return Math.abs(hash).toString(16).toUpperCase();
  }

  // ==================== Audit Logging ====================

  /**
   * Log denied access attempt
   */
  private logAccessDenied(
    userId: string,
    role: string,
    action: Action,
    resourceType: ResourceType,
    resourceId: string,
    requiredPermissions: Permission[],
    reason: string,
    context?: Record<string, unknown>
  ): AccessDeniedEntry {
    if (!this.state.config.auditEnabled) {
      const entry: AccessDeniedEntry = {
        id: '',
        timestamp: Date.now(),
        userId,
        role,
        action,
        resourceType,
        resourceId,
        requiredPermissions,
        reason,
        context,
      };
      return entry;
    }

    const id = `denied-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
    const entry: AccessDeniedEntry = {
      id,
      timestamp: Date.now(),
      userId,
      role,
      action,
      resourceType,
      resourceId,
      requiredPermissions,
      reason,
      context,
    };

    this.state.auditLog.push(entry);

    // Enforce max entries
    if (this.state.auditLog.length > this.state.config.maxAuditEntries) {
      this.state.auditLog.shift();
    }

    return entry;
  }

  /**
   * Get audit log entries
   */
  getAuditLog(): AccessDeniedEntry[] {
    return [...this.state.auditLog];
  }

  /**
   * Get audit log by user
   */
  getAuditLogByUser(userId: string): AccessDeniedEntry[] {
    return this.state.auditLog.filter((e) => e.userId === userId);
  }

  /**
   * Get audit log by resource
   */
  getAuditLogByResource(
    resourceType: ResourceType,
    resourceId: string
  ): AccessDeniedEntry[] {
    return this.state.auditLog.filter(
      (e) => e.resourceType === resourceType && e.resourceId === resourceId
    );
  }

  /**
   * Get audit log by action
   */
  getAuditLogByAction(action: Action): AccessDeniedEntry[] {
    return this.state.auditLog.filter((e) => e.action === action);
  }

  /**
   * Clear audit log
   */
  clearAuditLog(): number {
    const count = this.state.auditLog.length;
    this.state.auditLog = [];
    return count;
  }

  // ==================== Configuration ====================

  /**
   * Get current configuration
   */
  getConfig(): RBACConfig {
    return { ...this.state.config };
  }

  /**
   * Update configuration
   */
  updateConfig(updates: Partial<RBACConfig>): void {
    this.state.config = {
      ...this.state.config,
      ...updates,
    };
  }

  /**
   * Reset enforcer state
   */
  reset(): void {
    this.state.policies.clear();
    this.state.auditLog = [];

    // Reset roles to built-in only
    this.state.roles.clear();
    for (const role of BUILT_IN_ROLES) {
      this.state.roles.set(role.name, role);
    }
  }
}
