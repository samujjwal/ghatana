/**
 * Enterprise RBAC & SCIM Integration
 * 
 * Provides SSO role mapping, SCIM user provisioning/deprovisioning,
 * and live RBAC enforcement for enterprise environments.
 * 
 * Features:
 * - SAML/OIDC role assertion parsing and mapping
 * - SCIM 2.0 user/group provisioning
 * - Live role propagation without session restart
 * - Audit logging for all role changes
 * 
 * @module security/enterpriseRBAC
 */

// ============================================================================
// Types
// ============================================================================

/**
 * Supported SSO protocols
 */
export type SSOProtocol = 'saml' | 'oidc';

/**
 * User role in the system
 */
export type UserRole = 'viewer' | 'commenter' | 'editor' | 'admin' | 'owner';

/**
 * SCIM operation type
 */
export type SCIMOperation = 'provision' | 'update' | 'deprovision';

/**
 * SAML assertion containing user identity and roles
 */
export interface SAMLAssertion {
  /** Subject identifier (user ID) */
  subject: string;
  /** User email from assertion */
  email: string;
  /** Display name */
  displayName?: string;
  /** Role attributes from IdP */
  roles: string[];
  /** Assertion issuance timestamp */
  issuedAt: Date;
  /** Assertion expiration */
  expiresAt: Date;
  /** IdP entity ID */
  issuer: string;
}

/**
 * OIDC ID token containing user claims
 */
export interface OIDCToken {
  /** Subject identifier (user ID) */
  sub: string;
  /** User email */
  email: string;
  /** Email verification status */
  email_verified: boolean;
  /** Display name */
  name?: string;
  /** Role claims */
  roles?: string[];
  /** Groups from IdP */
  groups?: string[];
  /** Token issuance timestamp */
  iat: number;
  /** Token expiration */
  exp: number;
  /** Issuer (IdP URL) */
  iss: string;
}

/**
 * Unified SSO identity regardless of protocol
 */
export interface SSOIdentity {
  /** Protocol used */
  protocol: SSOProtocol;
  /** User ID */
  userId: string;
  /** Email address */
  email: string;
  /** Display name */
  displayName?: string;
  /** Roles from IdP */
  idpRoles: string[];
  /** Mapped application roles */
  appRoles: UserRole[];
  /** Groups from IdP */
  groups: string[];
  /** Login timestamp */
  loginAt: Date;
  /** Token expiration */
  expiresAt: Date;
  /** IdP issuer */
  issuer: string;
}

/**
 * Role mapping rule from IdP role to app role
 */
export interface RoleMapping {
  /** Rule ID */
  id: string;
  /** IdP role/group pattern (regex supported) */
  idpPattern: string;
  /** Application role to assign */
  appRole: UserRole;
  /** Optional condition for mapping */
  condition?: string;
  /** Priority for conflicting rules (higher wins) */
  priority: number;
  /** Whether rule is active */
  enabled: boolean;
}

/**
 * SCIM 2.0 user resource
 */
export interface SCIMUser {
  /** SCIM resource ID */
  id: string;
  /** External ID from IdP */
  externalId?: string;
  /** Username */
  userName: string;
  /** Email addresses */
  emails: Array<{
    value: string;
    type?: string;
    primary?: boolean;
  }>;
  /** Name components */
  name?: {
    formatted?: string;
    familyName?: string;
    givenName?: string;
  };
  /** Display name */
  displayName?: string;
  /** Active status */
  active: boolean;
  /** Group memberships */
  groups?: Array<{
    value: string;
    display?: string;
  }>;
  /** Custom enterprise extensions */
  'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'?: {
    department?: string;
    manager?: {
      value: string;
    };
  };
}

/**
 * SCIM operation result
 */
export interface SCIMOperationResult {
  /** Operation type */
  operation: SCIMOperation;
  /** User ID */
  userId: string;
  /** Success status */
  success: boolean;
  /** Error message if failed */
  error?: string;
  /** Roles assigned/updated/removed */
  roles: UserRole[];
  /** Operation timestamp */
  timestamp: Date;
}

/**
 * Role change event for live propagation
 */
export interface RoleChangeEvent {
  /** User ID affected */
  userId: string;
  /** Previous roles */
  previousRoles: UserRole[];
  /** New roles */
  newRoles: UserRole[];
  /** Reason for change */
  reason: string;
  /** Actor who made the change */
  actor: string;
  /** Change timestamp */
  timestamp: Date;
}

/**
 * Audit log entry for role changes
 */
export interface RoleAuditEntry {
  /** Entry ID */
  id: string;
  /** User affected */
  userId: string;
  /** Action performed */
  action: 'sso_login' | 'scim_provision' | 'scim_update' | 'scim_deprovision' | 'role_change';
  /** Previous state */
  before?: {
    roles: UserRole[];
    groups: string[];
  };
  /** New state */
  after?: {
    roles: UserRole[];
    groups: string[];
  };
  /** Actor (system or admin user ID) */
  actor: string;
  /** Reason/context */
  reason: string;
  /** Timestamp */
  timestamp: Date;
  /** SSO protocol if applicable */
  protocol?: SSOProtocol;
  /** SCIM operation if applicable */
  scimOperation?: SCIMOperation;
}

/**
 * Enterprise RBAC configuration
 */
export interface EnterpriseRBACConfig {
  /** Enable live role propagation */
  enableLivePropagation: boolean;
  /** Enable audit logging */
  enableAuditLog: boolean;
  /** Max audit log entries */
  maxAuditLogEntries: number;
  /** Default roles for new users */
  defaultRoles: UserRole[];
  /** Auto-provision users on SSO login */
  autoProvisionOnSSO: boolean;
  /** Role mapping rules */
  roleMappings: RoleMapping[];
}

/**
 * Enterprise RBAC manager state
 */
export interface EnterpriseRBACState {
  /** Configuration */
  config: EnterpriseRBACConfig;
  /** User sessions by user ID */
  userSessions: Map<string, SSOIdentity>;
  /** Role mappings by ID */
  roleMappings: Map<string, RoleMapping>;
  /** Audit log entries */
  auditLog: RoleAuditEntry[];
  /** Role change listeners */
  roleChangeListeners: Array<(event: RoleChangeEvent) => void>;
}

// ============================================================================
// Manager
// ============================================================================

/**
 * Create Enterprise RBAC manager
 */
export function createEnterpriseRBACManager(
  config: Partial<EnterpriseRBACConfig> = {}
): EnterpriseRBACState {
  const defaultConfig: EnterpriseRBACConfig = {
    enableLivePropagation: true,
    enableAuditLog: true,
    maxAuditLogEntries: 10000,
    defaultRoles: ['viewer'],
    autoProvisionOnSSO: true,
    roleMappings: [],
  };

  const state: EnterpriseRBACState = {
    config: { ...defaultConfig, ...config },
    userSessions: new Map(),
    roleMappings: new Map(),
    auditLog: [],
    roleChangeListeners: [],
  };

  // Initialize role mappings
  state.config.roleMappings.forEach((mapping) => {
    state.roleMappings.set(mapping.id, mapping);
  });

  return state;
}

// ============================================================================
// SSO Integration
// ============================================================================

/**
 * Parse SAML assertion and extract user identity
 */
export function parseSAMLAssertion(assertion: SAMLAssertion): SSOIdentity {
  return {
    protocol: 'saml',
    userId: assertion.subject,
    email: assertion.email,
    displayName: assertion.displayName,
    idpRoles: assertion.roles,
    appRoles: [], // Populated by mapRoles
    groups: [], // SAML typically uses roles, not groups
    loginAt: assertion.issuedAt,
    expiresAt: assertion.expiresAt,
    issuer: assertion.issuer,
  };
}

/**
 * Parse OIDC ID token and extract user identity
 */
export function parseOIDCToken(token: OIDCToken): SSOIdentity {
  return {
    protocol: 'oidc',
    userId: token.sub,
    email: token.email,
    displayName: token.name,
    idpRoles: token.roles || [],
    appRoles: [], // Populated by mapRoles
    groups: token.groups || [],
    loginAt: new Date(token.iat * 1000),
    expiresAt: new Date(token.exp * 1000),
    issuer: token.iss,
  };
}

/**
 * Map IdP roles to application roles using mapping rules
 */
export function mapRoles(
  state: EnterpriseRBACState,
  identity: SSOIdentity
): UserRole[] {
  const mappedRoles: UserRole[] = [];
  const allIdpIdentifiers = [...identity.idpRoles, ...identity.groups];

  // Sort mappings by priority (descending)
  const sortedMappings = Array.from(state.roleMappings.values())
    .filter((m) => m.enabled)
    .sort((a, b) => b.priority - a.priority);

  // Apply mappings
  for (const mapping of sortedMappings) {
    for (const idpId of allIdpIdentifiers) {
      const regex = new RegExp(mapping.idpPattern, 'i');
      if (regex.test(idpId)) {
        // Check condition if present (simple eval for now)
        if (mapping.condition) {
          // In production, use a safe expression evaluator
          // For now, skip condition evaluation
          mappedRoles.push(mapping.appRole);
        } else {
          mappedRoles.push(mapping.appRole);
        }
      }
    }
  }

  // Deduplicate and sort by hierarchy (viewer < commenter < editor < admin < owner)
  const roleHierarchy: UserRole[] = ['viewer', 'commenter', 'editor', 'admin', 'owner'];
  const uniqueRoles = [...new Set(mappedRoles)];
  uniqueRoles.sort((a, b) => roleHierarchy.indexOf(a) - roleHierarchy.indexOf(b));

  return uniqueRoles.length > 0 ? uniqueRoles : state.config.defaultRoles;
}

/**
 * Handle SSO login and create user session
 */
export function handleSSOLogin(
  state: EnterpriseRBACState,
  identity: SSOIdentity
): SSOIdentity {
  // Map roles
  identity.appRoles = mapRoles(state, identity);

  // Store session
  state.userSessions.set(identity.userId, identity);

  // Audit log
  if (state.config.enableAuditLog) {
    const entry: RoleAuditEntry = {
      id: `audit_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
      userId: identity.userId,
      action: 'sso_login',
      after: {
        roles: identity.appRoles,
        groups: identity.groups,
      },
      actor: 'system',
      reason: `SSO login via ${identity.protocol.toUpperCase()}`,
      timestamp: new Date(),
      protocol: identity.protocol,
    };
    addAuditEntry(state, entry);
  }

  // Auto-provision if enabled
  if (state.config.autoProvisionOnSSO) {
    // In production, this would create user in database
    // For now, we just log it
  }

  return identity;
}

// ============================================================================
// SCIM Integration
// ============================================================================

/**
 * Provision user via SCIM
 */
export function provisionSCIMUser(
  state: EnterpriseRBACState,
  user: SCIMUser
): SCIMOperationResult {
  const userId = user.id;
  const email = user.emails[0]?.value || '';

  try {
    // Map groups to roles
    const idpGroups = user.groups?.map((g) => g.value) || [];
    const identity: SSOIdentity = {
      protocol: 'oidc', // SCIM typically used with OIDC
      userId,
      email,
      displayName: user.displayName || user.name?.formatted,
      idpRoles: [],
      appRoles: [],
      groups: idpGroups,
      loginAt: new Date(),
      expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000), // 24 hours
      issuer: 'scim',
    };

    identity.appRoles = mapRoles(state, identity);
    state.userSessions.set(userId, identity);

    // Audit log
    if (state.config.enableAuditLog) {
      const entry: RoleAuditEntry = {
        id: `audit_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
        userId,
        action: 'scim_provision',
        after: {
          roles: identity.appRoles,
          groups: idpGroups,
        },
        actor: 'system',
        reason: 'SCIM user provisioning',
        timestamp: new Date(),
        scimOperation: 'provision',
      };
      addAuditEntry(state, entry);
    }

    // Notify listeners
    if (state.config.enableLivePropagation) {
      notifyRoleChange(state, {
        userId,
        previousRoles: [],
        newRoles: identity.appRoles,
        reason: 'SCIM provisioning',
        actor: 'system',
        timestamp: new Date(),
      });
    }

    return {
      operation: 'provision',
      userId,
      success: true,
      roles: identity.appRoles,
      timestamp: new Date(),
    };
  } catch (error) {
    return {
      operation: 'provision',
      userId,
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error',
      roles: [],
      timestamp: new Date(),
    };
  }
}

/**
 * Update user via SCIM
 */
export function updateSCIMUser(
  state: EnterpriseRBACState,
  user: SCIMUser
): SCIMOperationResult {
  const userId = user.id;
  const existingSession = state.userSessions.get(userId);

  try {
    if (!existingSession) {
      throw new Error(`User ${userId} not found`);
    }

    const previousRoles = existingSession.appRoles;
    const idpGroups = user.groups?.map((g) => g.value) || [];

    // Update identity
    existingSession.groups = idpGroups;
    existingSession.displayName = user.displayName || user.name?.formatted;
    existingSession.appRoles = mapRoles(state, existingSession);

    // Audit log
    if (state.config.enableAuditLog) {
      const entry: RoleAuditEntry = {
        id: `audit_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
        userId,
        action: 'scim_update',
        before: {
          roles: previousRoles,
          groups: existingSession.groups,
        },
        after: {
          roles: existingSession.appRoles,
          groups: idpGroups,
        },
        actor: 'system',
        reason: 'SCIM user update',
        timestamp: new Date(),
        scimOperation: 'update',
      };
      addAuditEntry(state, entry);
    }

    // Notify listeners if roles changed
    if (
      state.config.enableLivePropagation &&
      JSON.stringify(previousRoles) !== JSON.stringify(existingSession.appRoles)
    ) {
      notifyRoleChange(state, {
        userId,
        previousRoles,
        newRoles: existingSession.appRoles,
        reason: 'SCIM update',
        actor: 'system',
        timestamp: new Date(),
      });
    }

    return {
      operation: 'update',
      userId,
      success: true,
      roles: existingSession.appRoles,
      timestamp: new Date(),
    };
  } catch (error) {
    return {
      operation: 'update',
      userId,
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error',
      roles: existingSession?.appRoles || [],
      timestamp: new Date(),
    };
  }
}

/**
 * Deprovision user via SCIM
 */
export function deprovisionSCIMUser(
  state: EnterpriseRBACState,
  userId: string
): SCIMOperationResult {
  const existingSession = state.userSessions.get(userId);

  try {
    if (!existingSession) {
      throw new Error(`User ${userId} not found`);
    }

    const previousRoles = existingSession.appRoles;

    // Remove session
    state.userSessions.delete(userId);

    // Audit log
    if (state.config.enableAuditLog) {
      const entry: RoleAuditEntry = {
        id: `audit_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
        userId,
        action: 'scim_deprovision',
        before: {
          roles: previousRoles,
          groups: existingSession.groups,
        },
        actor: 'system',
        reason: 'SCIM user deprovisioning',
        timestamp: new Date(),
        scimOperation: 'deprovision',
      };
      addAuditEntry(state, entry);
    }

    // Notify listeners
    if (state.config.enableLivePropagation) {
      notifyRoleChange(state, {
        userId,
        previousRoles,
        newRoles: [],
        reason: 'SCIM deprovisioning',
        actor: 'system',
        timestamp: new Date(),
      });
    }

    return {
      operation: 'deprovision',
      userId,
      success: true,
      roles: [],
      timestamp: new Date(),
    };
  } catch (error) {
    return {
      operation: 'deprovision',
      userId,
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error',
      roles: existingSession?.appRoles || [],
      timestamp: new Date(),
    };
  }
}

// ============================================================================
// Live Role Propagation
// ============================================================================

/**
 * Subscribe to role change events
 */
export function subscribeToRoleChanges(
  state: EnterpriseRBACState,
  listener: (event: RoleChangeEvent) => void
): () => void {
  state.roleChangeListeners.push(listener);

  // Return unsubscribe function
  return () => {
    const index = state.roleChangeListeners.indexOf(listener);
    if (index !== -1) {
      state.roleChangeListeners.splice(index, 1);
    }
  };
}

/**
 * Notify all listeners of role change
 */
function notifyRoleChange(state: EnterpriseRBACState, event: RoleChangeEvent): void {
  for (const listener of state.roleChangeListeners) {
    try {
      listener(event);
    } catch (error) {
      console.error('Role change listener error:', error);
    }
  }
}

/**
 * Force role refresh for a user (admin action)
 */
export function forceRoleRefresh(
  state: EnterpriseRBACState,
  userId: string,
  actor: string
): boolean {
  const session = state.userSessions.get(userId);
  if (!session) {
    return false;
  }

  const previousRoles = session.appRoles;
  session.appRoles = mapRoles(state, session);

  if (JSON.stringify(previousRoles) !== JSON.stringify(session.appRoles)) {
    // Audit log
    if (state.config.enableAuditLog) {
      const entry: RoleAuditEntry = {
        id: `audit_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
        userId,
        action: 'role_change',
        before: {
          roles: previousRoles,
          groups: session.groups,
        },
        after: {
          roles: session.appRoles,
          groups: session.groups,
        },
        actor,
        reason: 'Admin-initiated role refresh',
        timestamp: new Date(),
      };
      addAuditEntry(state, entry);
    }

    // Notify listeners
    if (state.config.enableLivePropagation) {
      notifyRoleChange(state, {
        userId,
        previousRoles,
        newRoles: session.appRoles,
        reason: 'Admin role refresh',
        actor,
        timestamp: new Date(),
      });
    }

    return true;
  }

  return false;
}

// ============================================================================
// Role Mappings
// ============================================================================

/**
 * Add role mapping rule
 */
export function addRoleMapping(
  state: EnterpriseRBACState,
  mapping: Omit<RoleMapping, 'id'>
): RoleMapping {
  const id = `mapping_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  const fullMapping: RoleMapping = { ...mapping, id };
  state.roleMappings.set(id, fullMapping);
  return fullMapping;
}

/**
 * Update role mapping rule
 */
export function updateRoleMapping(
  state: EnterpriseRBACState,
  id: string,
  updates: Partial<RoleMapping>
): RoleMapping | null {
  const mapping = state.roleMappings.get(id);
  if (!mapping) {
    return null;
  }

  const updated = { ...mapping, ...updates, id };
  state.roleMappings.set(id, updated);
  return updated;
}

/**
 * Delete role mapping rule
 */
export function deleteRoleMapping(state: EnterpriseRBACState, id: string): boolean {
  return state.roleMappings.delete(id);
}

/**
 * Get all role mappings
 */
export function getAllRoleMappings(state: EnterpriseRBACState): RoleMapping[] {
  return Array.from(state.roleMappings.values());
}

// ============================================================================
// User Session Management
// ============================================================================

/**
 * Get user session
 */
export function getUserSession(
  state: EnterpriseRBACState,
  userId: string
): SSOIdentity | null {
  return state.userSessions.get(userId) || null;
}

/**
 * Get all active sessions
 */
export function getAllSessions(state: EnterpriseRBACState): SSOIdentity[] {
  return Array.from(state.userSessions.values());
}

/**
 * Check if user has role
 */
export function userHasRole(
  state: EnterpriseRBACState,
  userId: string,
  role: UserRole
): boolean {
  const session = state.userSessions.get(userId);
  return session?.appRoles.includes(role) || false;
}

/**
 * Invalidate user session (force re-authentication)
 */
export function invalidateSession(state: EnterpriseRBACState, userId: string): boolean {
  return state.userSessions.delete(userId);
}

// ============================================================================
// Audit Log
// ============================================================================

/**
 * Add audit entry
 */
function addAuditEntry(state: EnterpriseRBACState, entry: RoleAuditEntry): void {
  state.auditLog.push(entry);

  // Enforce max entries
  if (state.auditLog.length > state.config.maxAuditLogEntries) {
    state.auditLog.shift();
  }
}

/**
 * Get audit log
 */
export function getAuditLog(state: EnterpriseRBACState): RoleAuditEntry[] {
  return [...state.auditLog];
}

/**
 * Get audit log by user
 */
export function getAuditLogByUser(
  state: EnterpriseRBACState,
  userId: string
): RoleAuditEntry[] {
  return state.auditLog.filter((entry) => entry.userId === userId);
}

/**
 * Get audit log by action
 */
export function getAuditLogByAction(
  state: EnterpriseRBACState,
  action: RoleAuditEntry['action']
): RoleAuditEntry[] {
  return state.auditLog.filter((entry) => entry.action === action);
}

/**
 * Clear audit log
 */
export function clearAuditLog(state: EnterpriseRBACState): void {
  state.auditLog = [];
}

// ============================================================================
// Configuration
// ============================================================================

/**
 * Get configuration
 */
export function getConfig(state: EnterpriseRBACState): EnterpriseRBACConfig {
  return { ...state.config };
}

/**
 * Update configuration
 */
export function updateConfig(
  state: EnterpriseRBACState,
  updates: Partial<EnterpriseRBACConfig>
): EnterpriseRBACConfig {
  state.config = { ...state.config, ...updates };
  return { ...state.config };
}
