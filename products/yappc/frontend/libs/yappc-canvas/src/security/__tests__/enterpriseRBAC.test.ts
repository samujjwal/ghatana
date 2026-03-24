/**
 * Tests for Enterprise RBAC & SCIM Integration
 * 
 * @see libs/canvas/src/security/enterpriseRBAC.ts
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createEnterpriseRBACManager,
  parseSAMLAssertion,
  parseOIDCToken,
  mapRoles,
  handleSSOLogin,
  provisionSCIMUser,
  updateSCIMUser,
  deprovisionSCIMUser,
  subscribeToRoleChanges,
  forceRoleRefresh,
  addRoleMapping,
  updateRoleMapping,
  deleteRoleMapping,
  getAllRoleMappings,
  getUserSession,
  getAllSessions,
  userHasRole,
  invalidateSession,
  getAuditLog,
  getAuditLogByUser,
  getAuditLogByAction,
  clearAuditLog,
  getConfig,
  updateConfig,
  type EnterpriseRBACState,
  type SAMLAssertion,
  type OIDCToken,
  type SSOIdentity,
  type SCIMUser,
  type RoleChangeEvent,
} from '../enterpriseRBAC';

describe.skip('Enterprise RBAC & SCIM Integration', () => {
  let state: EnterpriseRBACState;

  beforeEach(() => {
    state = createEnterpriseRBACManager();
  });

  // ============================================================================
  // Manager Creation
  // ============================================================================

  describe('Manager Creation', () => {
    it('should create manager with default config', () => {
      const manager = createEnterpriseRBACManager();

      expect(manager.config.enableLivePropagation).toBe(true);
      expect(manager.config.enableAuditLog).toBe(true);
      expect(manager.config.maxAuditLogEntries).toBe(10000);
      expect(manager.config.defaultRoles).toEqual(['viewer']);
      expect(manager.config.autoProvisionOnSSO).toBe(true);
      expect(manager.userSessions.size).toBe(0);
      expect(manager.auditLog).toEqual([]);
    });

    it('should create manager with custom config', () => {
      const manager = createEnterpriseRBACManager({
        enableLivePropagation: false,
        defaultRoles: ['editor'],
        maxAuditLogEntries: 5000,
      });

      expect(manager.config.enableLivePropagation).toBe(false);
      expect(manager.config.defaultRoles).toEqual(['editor']);
      expect(manager.config.maxAuditLogEntries).toBe(5000);
    });

    it('should initialize role mappings from config', () => {
      const manager = createEnterpriseRBACManager({
        roleMappings: [
          {
            id: 'map1',
            idpPattern: 'admin.*',
            appRole: 'admin',
            priority: 100,
            enabled: true,
          },
        ],
      });

      expect(manager.roleMappings.size).toBe(1);
      expect(manager.roleMappings.get('map1')).toBeDefined();
    });
  });

  // ============================================================================
  // SAML Assertion Parsing
  // ============================================================================

  describe('SAML Assertion Parsing', () => {
    it('should parse SAML assertion correctly', () => {
      const assertion: SAMLAssertion = {
        subject: 'user123',
        email: 'user@example.com',
        displayName: 'Test User',
        roles: ['developers', 'viewers'],
        issuedAt: new Date('2024-01-01T00:00:00Z'),
        expiresAt: new Date('2024-01-01T01:00:00Z'),
        issuer: 'https://idp.example.com',
      };

      const identity = parseSAMLAssertion(assertion);

      expect(identity.protocol).toBe('saml');
      expect(identity.userId).toBe('user123');
      expect(identity.email).toBe('user@example.com');
      expect(identity.displayName).toBe('Test User');
      expect(identity.idpRoles).toEqual(['developers', 'viewers']);
      expect(identity.groups).toEqual([]);
      expect(identity.issuer).toBe('https://idp.example.com');
    });

    it('should handle missing optional fields', () => {
      const assertion: SAMLAssertion = {
        subject: 'user123',
        email: 'user@example.com',
        roles: [],
        issuedAt: new Date(),
        expiresAt: new Date(),
        issuer: 'idp',
      };

      const identity = parseSAMLAssertion(assertion);

      expect(identity.displayName).toBeUndefined();
      expect(identity.idpRoles).toEqual([]);
    });
  });

  // ============================================================================
  // OIDC Token Parsing
  // ============================================================================

  describe('OIDC Token Parsing', () => {
    it('should parse OIDC token correctly', () => {
      const token: OIDCToken = {
        sub: 'user456',
        email: 'user@example.com',
        email_verified: true,
        name: 'Test User',
        roles: ['editor'],
        groups: ['engineering', 'product'],
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
        iss: 'https://oauth.example.com',
      };

      const identity = parseOIDCToken(token);

      expect(identity.protocol).toBe('oidc');
      expect(identity.userId).toBe('user456');
      expect(identity.email).toBe('user@example.com');
      expect(identity.displayName).toBe('Test User');
      expect(identity.idpRoles).toEqual(['editor']);
      expect(identity.groups).toEqual(['engineering', 'product']);
      expect(identity.issuer).toBe('https://oauth.example.com');
    });

    it('should handle missing optional fields', () => {
      const token: OIDCToken = {
        sub: 'user456',
        email: 'user@example.com',
        email_verified: true,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
        iss: 'issuer',
      };

      const identity = parseOIDCToken(token);

      expect(identity.displayName).toBeUndefined();
      expect(identity.idpRoles).toEqual([]);
      expect(identity.groups).toEqual([]);
    });
  });

  // ============================================================================
  // Role Mapping
  // ============================================================================

  describe('Role Mapping', () => {
    beforeEach(() => {
      // Add test mappings
      addRoleMapping(state, {
        idpPattern: '^admin.*',
        appRole: 'admin',
        priority: 100,
        enabled: true,
      });
      addRoleMapping(state, {
        idpPattern: '^editor.*',
        appRole: 'editor',
        priority: 50,
        enabled: true,
      });
      addRoleMapping(state, {
        idpPattern: '.*viewer.*',
        appRole: 'viewer',
        priority: 10,
        enabled: true,
      });
    });

    it('should map IdP roles to app roles', () => {
      const identity: SSOIdentity = {
        protocol: 'saml',
        userId: 'user1',
        email: 'admin@example.com',
        idpRoles: ['admin-group', 'editor-team'],
        appRoles: [],
        groups: [],
        loginAt: new Date(),
        expiresAt: new Date(),
        issuer: 'idp',
      };

      const roles = mapRoles(state, identity);

      expect(roles).toContain('admin');
      expect(roles).toContain('editor');
    });

    it('should respect priority order', () => {
      const identity: SSOIdentity = {
        protocol: 'oidc',
        userId: 'user2',
        email: 'user@example.com',
        idpRoles: [],
        appRoles: [],
        groups: ['admin-all', 'viewer-only'],
        loginAt: new Date(),
        expiresAt: new Date(),
        issuer: 'idp',
      };

      const roles = mapRoles(state, identity);

      // Admin should come before viewer due to hierarchy
      expect(roles.indexOf('admin')).toBeLessThan(roles.indexOf('viewer'));
    });

    it('should return default roles if no mappings match', () => {
      const identity: SSOIdentity = {
        protocol: 'oidc',
        userId: 'user3',
        email: 'user@example.com',
        idpRoles: ['no-match'],
        appRoles: [],
        groups: [],
        loginAt: new Date(),
        expiresAt: new Date(),
        issuer: 'idp',
      };

      const roles = mapRoles(state, identity);

      expect(roles).toEqual(['viewer']);
    });

    it('should deduplicate roles', () => {
      addRoleMapping(state, {
        idpPattern: 'duplicate.*',
        appRole: 'editor',
        priority: 20,
        enabled: true,
      });

      const identity: SSOIdentity = {
        protocol: 'oidc',
        userId: 'user4',
        email: 'user@example.com',
        idpRoles: ['editor-group', 'duplicate-group'],
        appRoles: [],
        groups: [],
        loginAt: new Date(),
        expiresAt: new Date(),
        issuer: 'idp',
      };

      const roles = mapRoles(state, identity);
      const editorCount = roles.filter((r) => r === 'editor').length;

      expect(editorCount).toBe(1);
    });

    it('should ignore disabled mappings', () => {
      addRoleMapping(state, {
        idpPattern: 'disabled.*',
        appRole: 'owner',
        priority: 200,
        enabled: false,
      });

      const identity: SSOIdentity = {
        protocol: 'oidc',
        userId: 'user5',
        email: 'user@example.com',
        idpRoles: ['disabled-role'],
        appRoles: [],
        groups: [],
        loginAt: new Date(),
        expiresAt: new Date(),
        issuer: 'idp',
      };

      const roles = mapRoles(state, identity);

      expect(roles).not.toContain('owner');
    });
  });

  // ============================================================================
  // SSO Login
  // ============================================================================

  describe('SSO Login', () => {
    it('should handle SSO login and create session', () => {
      addRoleMapping(state, {
        idpPattern: 'admin',
        appRole: 'admin',
        priority: 100,
        enabled: true,
      });

      const identity: SSOIdentity = {
        protocol: 'saml',
        userId: 'user1',
        email: 'admin@example.com',
        idpRoles: ['admin'],
        appRoles: [],
        groups: [],
        loginAt: new Date(),
        expiresAt: new Date(),
        issuer: 'idp',
      };

      const result = handleSSOLogin(state, identity);

      expect(result.appRoles).toContain('admin');
      expect(state.userSessions.has('user1')).toBe(true);
    });

    it('should create audit log entry', () => {
      const identity: SSOIdentity = {
        protocol: 'oidc',
        userId: 'user2',
        email: 'user@example.com',
        idpRoles: [],
        appRoles: [],
        groups: [],
        loginAt: new Date(),
        expiresAt: new Date(),
        issuer: 'idp',
      };

      handleSSOLogin(state, identity);

      const logs = getAuditLogByUser(state, 'user2');
      expect(logs.length).toBeGreaterThan(0);
      expect(logs[0].action).toBe('sso_login');
    });

    it('should not create audit log if disabled', () => {
      state.config.enableAuditLog = false;

      const identity: SSOIdentity = {
        protocol: 'saml',
        userId: 'user3',
        email: 'user@example.com',
        idpRoles: [],
        appRoles: [],
        groups: [],
        loginAt: new Date(),
        expiresAt: new Date(),
        issuer: 'idp',
      };

      handleSSOLogin(state, identity);

      expect(state.auditLog.length).toBe(0);
    });
  });

  // ============================================================================
  // SCIM Provisioning
  // ============================================================================

  describe('SCIM Provisioning', () => {
    it('should provision user successfully', () => {
      addRoleMapping(state, {
        idpPattern: 'developers',
        appRole: 'editor',
        priority: 50,
        enabled: true,
      });

      const scimUser: SCIMUser = {
        id: 'scim-user-1',
        userName: 'developer1',
        emails: [{ value: 'dev@example.com', primary: true }],
        displayName: 'Developer One',
        active: true,
        groups: [{ value: 'developers' }],
      };

      const result = provisionSCIMUser(state, scimUser);

      expect(result.success).toBe(true);
      expect(result.operation).toBe('provision');
      expect(result.roles).toContain('editor');
      expect(state.userSessions.has('scim-user-1')).toBe(true);
    });

    it('should create audit log for provisioning', () => {
      const scimUser: SCIMUser = {
        id: 'scim-user-2',
        userName: 'user2',
        emails: [{ value: 'user2@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);

      const logs = getAuditLogByAction(state, 'scim_provision');
      expect(logs.length).toBeGreaterThan(0);
      expect(logs[0].scimOperation).toBe('provision');
    });

    it('should notify role change listeners', () => {
      let notified = false;
      subscribeToRoleChanges(state, () => {
        notified = true;
      });

      const scimUser: SCIMUser = {
        id: 'scim-user-3',
        userName: 'user3',
        emails: [{ value: 'user3@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);

      expect(notified).toBe(true);
    });
  });

  // ============================================================================
  // SCIM Update
  // ============================================================================

  describe('SCIM Update', () => {
    it('should update user roles when groups change', () => {
      addRoleMapping(state, {
        idpPattern: 'admins',
        appRole: 'admin',
        priority: 100,
        enabled: true,
      });

      const scimUser: SCIMUser = {
        id: 'scim-user-4',
        userName: 'user4',
        emails: [{ value: 'user4@example.com' }],
        active: true,
        groups: [{ value: 'viewers' }],
      };

      provisionSCIMUser(state, scimUser);

      const session = getUserSession(state, 'scim-user-4');
      const oldRoles = session?.appRoles || [];

      // Update with admin group
      scimUser.groups = [{ value: 'admins' }];
      const result = updateSCIMUser(state, scimUser);

      expect(result.success).toBe(true);
      expect(result.roles).toContain('admin');
      expect(result.roles).not.toEqual(oldRoles);
    });

    it('should fail if user not found', () => {
      const scimUser: SCIMUser = {
        id: 'non-existent',
        userName: 'ghost',
        emails: [{ value: 'ghost@example.com' }],
        active: true,
      };

      const result = updateSCIMUser(state, scimUser);

      expect(result.success).toBe(false);
      expect(result.error).toContain('not found');
    });

    it('should create audit log for update', () => {
      const scimUser: SCIMUser = {
        id: 'scim-user-5',
        userName: 'user5',
        emails: [{ value: 'user5@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);
      updateSCIMUser(state, scimUser);

      const logs = getAuditLogByAction(state, 'scim_update');
      expect(logs.length).toBeGreaterThan(0);
    });

    it('should notify listeners only if roles changed', () => {
      let notificationCount = 0;
      subscribeToRoleChanges(state, () => {
        notificationCount++;
      });

      const scimUser: SCIMUser = {
        id: 'scim-user-6',
        userName: 'user6',
        emails: [{ value: 'user6@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser); // +1 notification
      updateSCIMUser(state, scimUser); // No role change, no notification

      expect(notificationCount).toBe(1);
    });
  });

  // ============================================================================
  // SCIM Deprovisioning
  // ============================================================================

  describe('SCIM Deprovisioning', () => {
    it('should deprovision user successfully', () => {
      const scimUser: SCIMUser = {
        id: 'scim-user-7',
        userName: 'user7',
        emails: [{ value: 'user7@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);
      const result = deprovisionSCIMUser(state, 'scim-user-7');

      expect(result.success).toBe(true);
      expect(result.operation).toBe('deprovision');
      expect(result.roles).toEqual([]);
      expect(state.userSessions.has('scim-user-7')).toBe(false);
    });

    it('should fail if user not found', () => {
      const result = deprovisionSCIMUser(state, 'non-existent');

      expect(result.success).toBe(false);
      expect(result.error).toContain('not found');
    });

    it('should create audit log for deprovisioning', () => {
      const scimUser: SCIMUser = {
        id: 'scim-user-8',
        userName: 'user8',
        emails: [{ value: 'user8@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);
      deprovisionSCIMUser(state, 'scim-user-8');

      const logs = getAuditLogByAction(state, 'scim_deprovision');
      expect(logs.length).toBeGreaterThan(0);
    });

    it('should notify role change listeners', () => {
      const events: RoleChangeEvent[] = [];
      subscribeToRoleChanges(state, (e) => {
        events.push(e);
      });

      const scimUser: SCIMUser = {
        id: 'scim-user-9',
        userName: 'user9',
        emails: [{ value: 'user9@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);
      deprovisionSCIMUser(state, 'scim-user-9');

      expect(events.length).toBeGreaterThan(0);
      const lastEvent = events[events.length - 1];
      expect(lastEvent.newRoles).toEqual([]);
    });
  });

  // ============================================================================
  // Live Role Propagation
  // ============================================================================

  describe('Live Role Propagation', () => {
    it('should subscribe and unsubscribe from role changes', () => {
      let count = 0;
      const unsubscribe = subscribeToRoleChanges(state, () => {
        count++;
      });

      const scimUser: SCIMUser = {
        id: 'user-sub',
        userName: 'user',
        emails: [{ value: 'user@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);
      expect(count).toBe(1);

      unsubscribe();
      deprovisionSCIMUser(state, 'user-sub');
      expect(count).toBe(1); // Still 1, not incremented after unsubscribe
    });

    it('should notify multiple listeners', () => {
      let count1 = 0;
      let count2 = 0;

      subscribeToRoleChanges(state, () => {
        count1++;
      });
      subscribeToRoleChanges(state, () => {
        count2++;
      });

      const scimUser: SCIMUser = {
        id: 'user-multi',
        userName: 'user',
        emails: [{ value: 'user@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);

      expect(count1).toBe(1);
      expect(count2).toBe(1);
    });

    it('should not notify if propagation disabled', () => {
      state.config.enableLivePropagation = false;
      let notified = false;

      subscribeToRoleChanges(state, () => {
        notified = true;
      });

      const scimUser: SCIMUser = {
        id: 'user-disabled',
        userName: 'user',
        emails: [{ value: 'user@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);

      expect(notified).toBe(false);
    });
  });

  // ============================================================================
  // Force Role Refresh
  // ============================================================================

  describe('Force Role Refresh', () => {
    it('should refresh user roles', () => {
      const scimUser: SCIMUser = {
        id: 'user-refresh',
        userName: 'user',
        emails: [{ value: 'user@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);

      // Add new mapping
      addRoleMapping(state, {
        idpPattern: '.*',
        appRole: 'admin',
        priority: 200,
        enabled: true,
      });

      const result = forceRoleRefresh(state, 'user-refresh', 'admin-user');

      expect(result).toBe(true);
      const session = getUserSession(state, 'user-refresh');
      expect(session?.appRoles).toContain('admin');
    });

    it('should return false if no role change', () => {
      const scimUser: SCIMUser = {
        id: 'user-no-change',
        userName: 'user',
        emails: [{ value: 'user@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);

      const result = forceRoleRefresh(state, 'user-no-change', 'admin-user');

      expect(result).toBe(false);
    });

    it('should return false if user not found', () => {
      const result = forceRoleRefresh(state, 'non-existent', 'admin-user');

      expect(result).toBe(false);
    });

    it('should create audit log for refresh', () => {
      const scimUser: SCIMUser = {
        id: 'user-audit',
        userName: 'user',
        emails: [{ value: 'user@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);

      addRoleMapping(state, {
        idpPattern: '.*',
        appRole: 'editor',
        priority: 150,
        enabled: true,
      });

      forceRoleRefresh(state, 'user-audit', 'admin-user');

      const logs = getAuditLogByAction(state, 'role_change');
      expect(logs.length).toBeGreaterThan(0);
      expect(logs[0].actor).toBe('admin-user');
    });
  });

  // ============================================================================
  // Role Mapping Management
  // ============================================================================

  describe('Role Mapping Management', () => {
    it('should add role mapping', () => {
      const mapping = addRoleMapping(state, {
        idpPattern: 'test.*',
        appRole: 'editor',
        priority: 75,
        enabled: true,
      });

      expect(mapping.id).toBeDefined();
      expect(state.roleMappings.has(mapping.id)).toBe(true);
    });

    it('should update role mapping', () => {
      const mapping = addRoleMapping(state, {
        idpPattern: 'original',
        appRole: 'viewer',
        priority: 10,
        enabled: true,
      });

      const updated = updateRoleMapping(state, mapping.id, {
        idpPattern: 'updated',
        priority: 20,
      });

      expect(updated?.idpPattern).toBe('updated');
      expect(updated?.priority).toBe(20);
      expect(updated?.appRole).toBe('viewer'); // Unchanged
    });

    it('should return null when updating non-existent mapping', () => {
      const result = updateRoleMapping(state, 'non-existent', { priority: 100 });

      expect(result).toBeNull();
    });

    it('should delete role mapping', () => {
      const mapping = addRoleMapping(state, {
        idpPattern: 'delete-me',
        appRole: 'viewer',
        priority: 10,
        enabled: true,
      });

      const result = deleteRoleMapping(state, mapping.id);

      expect(result).toBe(true);
      expect(state.roleMappings.has(mapping.id)).toBe(false);
    });

    it('should return false when deleting non-existent mapping', () => {
      const result = deleteRoleMapping(state, 'non-existent');

      expect(result).toBe(false);
    });

    it('should get all role mappings', () => {
      addRoleMapping(state, {
        idpPattern: 'map1',
        appRole: 'viewer',
        priority: 10,
        enabled: true,
      });
      addRoleMapping(state, {
        idpPattern: 'map2',
        appRole: 'editor',
        priority: 20,
        enabled: true,
      });

      const mappings = getAllRoleMappings(state);

      expect(mappings.length).toBe(2);
    });
  });

  // ============================================================================
  // User Session Management
  // ============================================================================

  describe('User Session Management', () => {
    it('should get user session', () => {
      const scimUser: SCIMUser = {
        id: 'session-user',
        userName: 'user',
        emails: [{ value: 'user@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);

      const session = getUserSession(state, 'session-user');

      expect(session).not.toBeNull();
      expect(session?.userId).toBe('session-user');
    });

    it('should return null for non-existent user', () => {
      const session = getUserSession(state, 'non-existent');

      expect(session).toBeNull();
    });

    it('should get all sessions', () => {
      const user1: SCIMUser = {
        id: 'user1',
        userName: 'user1',
        emails: [{ value: 'user1@example.com' }],
        active: true,
      };
      const user2: SCIMUser = {
        id: 'user2',
        userName: 'user2',
        emails: [{ value: 'user2@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, user1);
      provisionSCIMUser(state, user2);

      const sessions = getAllSessions(state);

      expect(sessions.length).toBe(2);
    });

    it('should check if user has role', () => {
      addRoleMapping(state, {
        idpPattern: 'admins',
        appRole: 'admin',
        priority: 100,
        enabled: true,
      });

      const scimUser: SCIMUser = {
        id: 'role-check-user',
        userName: 'user',
        emails: [{ value: 'user@example.com' }],
        active: true,
        groups: [{ value: 'admins' }],
      };

      provisionSCIMUser(state, scimUser);

      expect(userHasRole(state, 'role-check-user', 'admin')).toBe(true);
      expect(userHasRole(state, 'role-check-user', 'owner')).toBe(false);
    });

    it('should return false for non-existent user', () => {
      expect(userHasRole(state, 'non-existent', 'admin')).toBe(false);
    });

    it('should invalidate session', () => {
      const scimUser: SCIMUser = {
        id: 'invalidate-user',
        userName: 'user',
        emails: [{ value: 'user@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, scimUser);

      const result = invalidateSession(state, 'invalidate-user');

      expect(result).toBe(true);
      expect(state.userSessions.has('invalidate-user')).toBe(false);
    });

    it('should return false when invalidating non-existent session', () => {
      const result = invalidateSession(state, 'non-existent');

      expect(result).toBe(false);
    });
  });

  // ============================================================================
  // Audit Log
  // ============================================================================

  describe('Audit Log', () => {
    it('should get all audit log entries', () => {
      const user1: SCIMUser = {
        id: 'audit-user-1',
        userName: 'user1',
        emails: [{ value: 'user1@example.com' }],
        active: true,
      };
      const user2: SCIMUser = {
        id: 'audit-user-2',
        userName: 'user2',
        emails: [{ value: 'user2@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, user1);
      provisionSCIMUser(state, user2);

      const logs = getAuditLog(state);

      expect(logs.length).toBeGreaterThanOrEqual(2);
    });

    it('should get audit log by user', () => {
      const user1: SCIMUser = {
        id: 'user-filter-1',
        userName: 'user1',
        emails: [{ value: 'user1@example.com' }],
        active: true,
      };
      const user2: SCIMUser = {
        id: 'user-filter-2',
        userName: 'user2',
        emails: [{ value: 'user2@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, user1);
      provisionSCIMUser(state, user2);

      const logs = getAuditLogByUser(state, 'user-filter-1');

      expect(logs.length).toBeGreaterThan(0);
      expect(logs.every((log) => log.userId === 'user-filter-1')).toBe(true);
    });

    it('should get audit log by action', () => {
      const user: SCIMUser = {
        id: 'action-user',
        userName: 'user',
        emails: [{ value: 'user@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, user);
      updateSCIMUser(state, user);

      const provisionLogs = getAuditLogByAction(state, 'scim_provision');
      const updateLogs = getAuditLogByAction(state, 'scim_update');

      expect(provisionLogs.length).toBeGreaterThan(0);
      expect(updateLogs.length).toBeGreaterThan(0);
    });

    it('should clear audit log', () => {
      const user: SCIMUser = {
        id: 'clear-user',
        userName: 'user',
        emails: [{ value: 'user@example.com' }],
        active: true,
      };

      provisionSCIMUser(state, user);
      expect(state.auditLog.length).toBeGreaterThan(0);

      clearAuditLog(state);

      expect(state.auditLog.length).toBe(0);
    });

    it('should enforce max audit log entries', () => {
      state.config.maxAuditLogEntries = 5;

      for (let i = 0; i < 10; i++) {
        const user: SCIMUser = {
          id: `max-user-${i}`,
          userName: `user${i}`,
          emails: [{ value: `user${i}@example.com` }],
          active: true,
        };
        provisionSCIMUser(state, user);
      }

      expect(state.auditLog.length).toBeLessThanOrEqual(5);
    });
  });

  // ============================================================================
  // Configuration
  // ============================================================================

  describe('Configuration', () => {
    it('should get configuration', () => {
      const config = getConfig(state);

      expect(config.enableLivePropagation).toBeDefined();
      expect(config.enableAuditLog).toBeDefined();
      expect(config.maxAuditLogEntries).toBeDefined();
    });

    it('should update configuration', () => {
      const updated = updateConfig(state, {
        enableLivePropagation: false,
        maxAuditLogEntries: 5000,
      });

      expect(updated.enableLivePropagation).toBe(false);
      expect(updated.maxAuditLogEntries).toBe(5000);
      expect(state.config.enableLivePropagation).toBe(false);
    });

    it('should preserve unchanged config values', () => {
      const original = getConfig(state);

      updateConfig(state, {
        enableLivePropagation: false,
      });

      expect(state.config.enableAuditLog).toBe(original.enableAuditLog);
      expect(state.config.maxAuditLogEntries).toBe(original.maxAuditLogEntries);
    });
  });
});
