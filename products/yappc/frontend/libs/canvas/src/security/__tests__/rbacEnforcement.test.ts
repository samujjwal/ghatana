/**
 * Tests for RBAC Enforcer
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  RBACEnforcer,
  type RoleDefinition,
  type AccessPolicy,
  type RedactionRule,
} from '../rbacEnforcement';

describe.skip('RBACEnforcer', () => {
  let enforcer: RBACEnforcer;

  beforeEach(() => {
    enforcer = new RBACEnforcer();
  });

  describe('initialization', () => {
    it('should initialize with default configuration', () => {
      const config = enforcer.getConfig();
      expect(config.enabled).toBe(true);
      expect(config.auditEnabled).toBe(true);
      expect(config.maxAuditEntries).toBe(10000);
      expect(config.defaultRole).toBe('viewer');
      expect(config.redactionEnabled).toBe(true);
    });

    it('should initialize with custom configuration', () => {
      const customEnforcer = new RBACEnforcer({
        enabled: false,
        auditEnabled: false,
        defaultRole: 'editor',
      });

      const config = customEnforcer.getConfig();
      expect(config.enabled).toBe(false);
      expect(config.auditEnabled).toBe(false);
      expect(config.defaultRole).toBe('editor');
      // Other values use defaults
      expect(config.maxAuditEntries).toBe(10000);
    });

    it('should register built-in roles', () => {
      const roles = enforcer.getAllRoles();
      expect(roles.length).toBe(4);

      const roleNames = roles.map((r) => r.name).sort();
      expect(roleNames).toEqual(['admin', 'commenter', 'editor', 'viewer']);
    });
  });

  describe('role management', () => {
    it('should define custom role', () => {
      const role = enforcer.defineRole(
        'analyst',
        'Data Analyst',
        'Can read and analyze data',
        ['read']
      );

      expect(role.name).toBe('analyst');
      expect(role.displayName).toBe('Data Analyst');
      expect(role.permissions).toEqual(['read']);
      expect(role.isBuiltIn).toBe(false);
    });

    it('should define role with inheritance', () => {
      const role = enforcer.defineRole(
        'power-editor',
        'Power Editor',
        'Editor with delete rights',
        ['delete'],
        'editor'
      );

      expect(role.inheritsFrom).toBe('editor');
    });

    it('should throw error when redefining built-in role', () => {
      expect(() => {
        enforcer.defineRole('admin', 'Custom Admin', 'desc', ['read']);
      }).toThrow('Cannot redefine built-in role: admin');
    });

    it('should throw error when parent role not found', () => {
      expect(() => {
        enforcer.defineRole(
          'custom',
          'Custom',
          'desc',
          ['read'],
          'non-existent'
        );
      }).toThrow('Parent role not found: non-existent');
    });

    it('should allow redefining custom role', () => {
      enforcer.defineRole('custom', 'Custom', 'desc', ['read']);
      const updated = enforcer.defineRole('custom', 'Custom2', 'desc2', ['write']);

      expect(updated.displayName).toBe('Custom2');
      expect(updated.permissions).toEqual(['write']);
    });

    it('should get role definition', () => {
      const role = enforcer.getRole('admin');

      expect(role).toBeDefined();
      expect(role?.name).toBe('admin');
      expect(role?.permissions).toContain('admin');
    });

    it('should return undefined for non-existent role', () => {
      const role = enforcer.getRole('non-existent');
      expect(role).toBeUndefined();
    });

    it('should get all roles', () => {
      enforcer.defineRole('custom1', 'Custom 1', 'desc', ['read']);
      enforcer.defineRole('custom2', 'Custom 2', 'desc', ['write']);

      const roles = enforcer.getAllRoles();
      expect(roles.length).toBe(6); // 4 built-in + 2 custom
    });

    it('should delete custom role', () => {
      enforcer.defineRole('custom', 'Custom', 'desc', ['read']);
      const deleted = enforcer.deleteRole('custom');

      expect(deleted).toBe(true);
      expect(enforcer.getRole('custom')).toBeUndefined();
    });

    it('should return false when deleting non-existent role', () => {
      const deleted = enforcer.deleteRole('non-existent');
      expect(deleted).toBe(false);
    });

    it('should throw error when deleting built-in role', () => {
      expect(() => {
        enforcer.deleteRole('admin');
      }).toThrow('Cannot delete built-in role: admin');
    });

    it('should get effective permissions without inheritance', () => {
      const perms = enforcer.getEffectivePermissions('admin');
      expect(perms).toEqual(['read', 'write', 'delete', 'admin']);
    });

    it('should get effective permissions with inheritance', () => {
      const perms = enforcer.getEffectivePermissions('editor');
      // Editor has read/write, inherits read from viewer
      expect(perms).toContain('read');
      expect(perms).toContain('write');
    });

    it('should return empty array for non-existent role', () => {
      const perms = enforcer.getEffectivePermissions('non-existent');
      expect(perms).toEqual([]);
    });

    it('should resolve multi-level inheritance', () => {
      enforcer.defineRole('level1', 'Level 1', 'desc', ['read']);
      enforcer.defineRole('level2', 'Level 2', 'desc', ['write'], 'level1');
      enforcer.defineRole('level3', 'Level 3', 'desc', ['delete'], 'level2');

      const perms = enforcer.getEffectivePermissions('level3');
      expect(perms).toContain('read');
      expect(perms).toContain('write');
      expect(perms).toContain('delete');
    });
  });

  describe('policy management', () => {
    it('should create policy', () => {
      const policy = enforcer.createPolicy('canvas', 'canvas-1');

      expect(policy.resourceType).toBe('canvas');
      expect(policy.resourceId).toBe('canvas-1');
      expect(policy.rolePermissions.size).toBe(0);
      expect(policy.redactionRules).toEqual([]);
    });

    it('should create policy with redaction rules', () => {
      const rules: RedactionRule[] = [
        {
          fieldPath: 'email',
          allowedRoles: ['admin'],
          strategy: 'mask',
          maskValue: '***',
        },
      ];

      const policy = enforcer.createPolicy('canvas', 'canvas-1', rules);
      expect(policy.redactionRules).toEqual(rules);
    });

    it('should create policy with metadata', () => {
      const metadata = { owner: 'user-1', team: 'engineering' };
      const policy = enforcer.createPolicy('canvas', 'canvas-1', [], metadata);

      expect(policy.metadata).toEqual(metadata);
    });

    it('should get policy for resource', () => {
      enforcer.createPolicy('canvas', 'canvas-1');
      const policy = enforcer.getPolicy('canvas', 'canvas-1');

      expect(policy).toBeDefined();
      expect(policy?.resourceId).toBe('canvas-1');
    });

    it('should return undefined for non-existent policy', () => {
      const policy = enforcer.getPolicy('canvas', 'non-existent');
      expect(policy).toBeUndefined();
    });

    it('should grant permissions to role', () => {
      enforcer.grantPermissions('canvas', 'canvas-1', 'editor', ['read', 'write']);

      const policy = enforcer.getPolicy('canvas', 'canvas-1');
      expect(policy?.rolePermissions.get('editor')).toEqual(['read', 'write']);
    });

    it('should create policy if not exists when granting', () => {
      enforcer.grantPermissions('canvas', 'canvas-1', 'viewer', ['read']);

      const policy = enforcer.getPolicy('canvas', 'canvas-1');
      expect(policy).toBeDefined();
    });

    it('should throw error when granting to non-existent role', () => {
      expect(() => {
        enforcer.grantPermissions('canvas', 'canvas-1', 'non-existent', ['read']);
      }).toThrow('Role not found: non-existent');
    });

    it('should revoke permissions from role', () => {
      enforcer.grantPermissions('canvas', 'canvas-1', 'editor', ['read', 'write']);
      const revoked = enforcer.revokePermissions('canvas', 'canvas-1', 'editor');

      expect(revoked).toBe(true);
      const policy = enforcer.getPolicy('canvas', 'canvas-1');
      expect(policy?.rolePermissions.has('editor')).toBe(false);
    });

    it('should return false when revoking from non-existent policy', () => {
      const revoked = enforcer.revokePermissions(
        'canvas',
        'non-existent',
        'editor'
      );
      expect(revoked).toBe(false);
    });

    it('should add redaction rule to policy', () => {
      const rule: RedactionRule = {
        fieldPath: 'email',
        allowedRoles: ['admin'],
        strategy: 'mask',
      };

      enforcer.addRedactionRule('canvas', 'canvas-1', rule);

      const policy = enforcer.getPolicy('canvas', 'canvas-1');
      expect(policy?.redactionRules).toContainEqual(rule);
    });

    it('should create policy if not exists when adding redaction rule', () => {
      const rule: RedactionRule = {
        fieldPath: 'password',
        allowedRoles: ['admin'],
        strategy: 'remove',
      };

      enforcer.addRedactionRule('canvas', 'canvas-1', rule);

      const policy = enforcer.getPolicy('canvas', 'canvas-1');
      expect(policy).toBeDefined();
      expect(policy?.redactionRules).toContainEqual(rule);
    });
  });

  describe('permission checking', () => {
    it('should grant permission when user has required permissions', () => {
      const result = enforcer.hasPermission(
        'user-1',
        'admin',
        'canvas',
        'canvas-1',
        ['read']
      );

      expect(result.granted).toBe(true);
      expect(result.reason).toBeUndefined();
    });

    it('should deny permission when user lacks required permissions', () => {
      const result = enforcer.hasPermission(
        'user-1',
        'viewer',
        'canvas',
        'canvas-1',
        ['write']
      );

      expect(result.granted).toBe(false);
      expect(result.reason).toContain('Missing permissions');
      expect(result.missingPermissions).toEqual(['write']);
    });

    it('should deny permission for invalid role', () => {
      const result = enforcer.hasPermission(
        'user-1',
        'invalid-role',
        'canvas',
        'canvas-1',
        ['read']
      );

      expect(result.granted).toBe(false);
      expect(result.reason).toContain('Invalid role');
    });

    it('should check multiple required permissions', () => {
      const result = enforcer.hasPermission(
        'user-1',
        'viewer',
        'canvas',
        'canvas-1',
        ['read', 'write', 'delete']
      );

      expect(result.granted).toBe(false);
      expect(result.missingPermissions).toEqual(['write', 'delete']);
    });

    it('should grant permission based on inherited permissions', () => {
      const result = enforcer.hasPermission(
        'user-1',
        'editor',
        'canvas',
        'canvas-1',
        ['read']
      );

      expect(result.granted).toBe(true);
    });

    it('should grant permission based on policy-specific permissions', () => {
      enforcer.grantPermissions('canvas', 'canvas-1', 'viewer', ['write']);

      const result = enforcer.hasPermission(
        'user-1',
        'viewer',
        'canvas',
        'canvas-1',
        ['write']
      );

      expect(result.granted).toBe(true);
    });

    it('should allow all permissions when RBAC disabled', () => {
      enforcer.updateConfig({ enabled: false });

      const result = enforcer.hasPermission(
        'user-1',
        'viewer',
        'canvas',
        'canvas-1',
        ['admin']
      );

      expect(result.granted).toBe(true);
    });

    it('should enforce permission and throw on denial', () => {
      expect(() => {
        enforcer.enforcePermission(
          'user-1',
          'viewer',
          'delete',
          'canvas',
          'canvas-1',
          ['delete']
        );
      }).toThrow('Access denied');
    });

    it('should enforce permission successfully when granted', () => {
      expect(() => {
        enforcer.enforcePermission(
          'user-1',
          'admin',
          'read',
          'canvas',
          'canvas-1',
          ['read']
        );
      }).not.toThrow();
    });

    it('should log denied access when enforcing', () => {
      try {
        enforcer.enforcePermission(
          'user-1',
          'viewer',
          'delete',
          'canvas',
          'canvas-1',
          ['delete'],
          { ip: '192.168.1.1' }
        );
      } catch {
        // Expected
      }

      const log = enforcer.getAuditLog();
      expect(log.length).toBe(1);
      expect(log[0].userId).toBe('user-1');
      expect(log[0].action).toBe('delete');
      expect(log[0].context).toEqual({ ip: '192.168.1.1' });
    });
  });

  describe('field-level redaction', () => {
    it('should remove field when strategy is remove', () => {
      const rule: RedactionRule = {
        fieldPath: 'password',
        allowedRoles: ['admin'],
        strategy: 'remove',
      };

      enforcer.createPolicy('canvas', 'canvas-1', [rule]);

      const data = { name: 'Canvas', password: 'secret123' };
      const redacted = enforcer.applyRedaction(data, 'viewer', 'canvas', 'canvas-1');

      expect(redacted.name).toBe('Canvas');
      expect(redacted.password).toBeUndefined();
    });

    it('should mask field with default value', () => {
      const rule: RedactionRule = {
        fieldPath: 'email',
        allowedRoles: ['admin'],
        strategy: 'mask',
      };

      enforcer.createPolicy('canvas', 'canvas-1', [rule]);

      const data = { name: 'User', email: 'user@example.com' };
      const redacted = enforcer.applyRedaction(data, 'viewer', 'canvas', 'canvas-1');

      expect(redacted.email).toBe('[REDACTED]');
    });

    it('should mask field with custom value', () => {
      const rule: RedactionRule = {
        fieldPath: 'phone',
        allowedRoles: ['admin'],
        strategy: 'mask',
        maskValue: '***-***-****',
      };

      enforcer.createPolicy('canvas', 'canvas-1', [rule]);

      const data = { name: 'User', phone: '555-123-4567' };
      const redacted = enforcer.applyRedaction(data, 'viewer', 'canvas', 'canvas-1');

      expect(redacted.phone).toBe('***-***-****');
    });

    it('should hash field value', () => {
      const rule: RedactionRule = {
        fieldPath: 'ssn',
        allowedRoles: ['admin'],
        strategy: 'hash',
      };

      enforcer.createPolicy('canvas', 'canvas-1', [rule]);

      const data = { name: 'User', ssn: '123-45-6789' };
      const redacted = enforcer.applyRedaction(data, 'viewer', 'canvas', 'canvas-1');

      expect(redacted.ssn).toMatch(/^\[HASH:[A-F0-9]+\]$/);
    });

    it('should not redact for allowed roles', () => {
      const rule: RedactionRule = {
        fieldPath: 'salary',
        allowedRoles: ['admin', 'manager'],
        strategy: 'mask',
      };

      enforcer.createPolicy('canvas', 'canvas-1', [rule]);

      const data = { name: 'Employee', salary: 75000 };
      const redacted = enforcer.applyRedaction(data, 'admin', 'canvas', 'canvas-1');

      expect(redacted.salary).toBe(75000);
    });

    it('should handle nested field paths', () => {
      const rule: RedactionRule = {
        fieldPath: 'user.email',
        allowedRoles: ['admin'],
        strategy: 'mask',
      };

      enforcer.createPolicy('canvas', 'canvas-1', [rule]);

      const data = { user: { name: 'Alice', email: 'alice@example.com' } };
      const redacted = enforcer.applyRedaction(data, 'viewer', 'canvas', 'canvas-1');

      expect((redacted.user as unknown).email).toBe('[REDACTED]');
      expect((redacted.user as unknown).name).toBe('Alice');
    });

    it('should handle multiple redaction rules', () => {
      const rules: RedactionRule[] = [
        { fieldPath: 'email', allowedRoles: ['admin'], strategy: 'mask' },
        { fieldPath: 'phone', allowedRoles: ['admin'], strategy: 'remove' },
      ];

      enforcer.createPolicy('canvas', 'canvas-1', rules);

      const data = { name: 'User', email: 'user@test.com', phone: '555-1234' };
      const redacted = enforcer.applyRedaction(data, 'viewer', 'canvas', 'canvas-1');

      expect(redacted.email).toBe('[REDACTED]');
      expect(redacted.phone).toBeUndefined();
      expect(redacted.name).toBe('User');
    });

    it('should return original data when no policy exists', () => {
      const data = { name: 'User', email: 'user@test.com' };
      const redacted = enforcer.applyRedaction(data, 'viewer', 'canvas', 'canvas-1');

      expect(redacted).toEqual(data);
    });

    it('should return original data when redaction disabled', () => {
      enforcer.updateConfig({ redactionEnabled: false });

      const rule: RedactionRule = {
        fieldPath: 'email',
        allowedRoles: ['admin'],
        strategy: 'mask',
      };

      enforcer.createPolicy('canvas', 'canvas-1', [rule]);

      const data = { email: 'user@test.com' };
      const redacted = enforcer.applyRedaction(data, 'viewer', 'canvas', 'canvas-1');

      expect(redacted.email).toBe('user@test.com');
    });

    it('should handle non-existent nested paths', () => {
      const rule: RedactionRule = {
        fieldPath: 'user.settings.private',
        allowedRoles: ['admin'],
        strategy: 'remove',
      };

      enforcer.createPolicy('canvas', 'canvas-1', [rule]);

      const data = { name: 'User' }; // No nested path exists
      const redacted = enforcer.applyRedaction(data, 'viewer', 'canvas', 'canvas-1');

      expect(redacted).toEqual(data);
    });
  });

  describe('audit logging', () => {
    it('should log denied access', () => {
      try {
        enforcer.enforcePermission(
          'user-1',
          'viewer',
          'delete',
          'canvas',
          'canvas-1',
          ['delete']
        );
      } catch {
        // Expected
      }

      const log = enforcer.getAuditLog();
      expect(log.length).toBe(1);
      expect(log[0].userId).toBe('user-1');
      expect(log[0].role).toBe('viewer');
      expect(log[0].action).toBe('delete');
      expect(log[0].resourceType).toBe('canvas');
      expect(log[0].resourceId).toBe('canvas-1');
      expect(log[0].requiredPermissions).toEqual(['delete']);
      expect(log[0].reason).toContain('Missing permissions');
    });

    it('should include context in audit log', () => {
      try {
        enforcer.enforcePermission(
          'user-1',
          'viewer',
          'write',
          'node',
          'node-1',
          ['write'],
          { ip: '192.168.1.1', userAgent: 'Mozilla/5.0' }
        );
      } catch {
        // Expected
      }

      const log = enforcer.getAuditLog();
      expect(log[0].context).toEqual({
        ip: '192.168.1.1',
        userAgent: 'Mozilla/5.0',
      });
    });

    it('should get audit log by user', () => {
      try {
        enforcer.enforcePermission('user-1', 'viewer', 'write', 'canvas', 'c1', [
          'write',
        ]);
      } catch {}
      try {
        enforcer.enforcePermission('user-2', 'viewer', 'delete', 'canvas', 'c2', [
          'delete',
        ]);
      } catch {}

      const userLog = enforcer.getAuditLogByUser('user-1');
      expect(userLog.length).toBe(1);
      expect(userLog[0].userId).toBe('user-1');
    });

    it('should get audit log by resource', () => {
      try {
        enforcer.enforcePermission('user-1', 'viewer', 'write', 'canvas', 'c1', [
          'write',
        ]);
      } catch {}
      try {
        enforcer.enforcePermission('user-2', 'viewer', 'delete', 'canvas', 'c2', [
          'delete',
        ]);
      } catch {}

      const resourceLog = enforcer.getAuditLogByResource('canvas', 'c1');
      expect(resourceLog.length).toBe(1);
      expect(resourceLog[0].resourceId).toBe('c1');
    });

    it('should get audit log by action', () => {
      try {
        enforcer.enforcePermission('user-1', 'viewer', 'write', 'canvas', 'c1', [
          'write',
        ]);
      } catch {}
      try {
        enforcer.enforcePermission('user-2', 'viewer', 'write', 'canvas', 'c2', [
          'write',
        ]);
      } catch {}
      try {
        enforcer.enforcePermission('user-3', 'viewer', 'delete', 'canvas', 'c3', [
          'delete',
        ]);
      } catch {}

      const actionLog = enforcer.getAuditLogByAction('write');
      expect(actionLog.length).toBe(2);
      expect(actionLog.every((e) => e.action === 'write')).toBe(true);
    });

    it('should clear audit log', () => {
      try {
        enforcer.enforcePermission('user-1', 'viewer', 'write', 'canvas', 'c1', [
          'write',
        ]);
      } catch {}

      const cleared = enforcer.clearAuditLog();
      expect(cleared).toBe(1);

      const log = enforcer.getAuditLog();
      expect(log.length).toBe(0);
    });

    it('should enforce max audit entries', () => {
      const smallAuditEnforcer = new RBACEnforcer({ maxAuditEntries: 3 });

      for (let i = 0; i < 5; i++) {
        try {
          smallAuditEnforcer.enforcePermission(
            `user-${i}`,
            'viewer',
            'write',
            'canvas',
            `c${i}`,
            ['write']
          );
        } catch {}
      }

      const log = smallAuditEnforcer.getAuditLog();
      expect(log.length).toBe(3);
      // Should keep the last 3 entries (2, 3, 4)
      expect(log[0].userId).toBe('user-2');
      expect(log[2].userId).toBe('user-4');
    });

    it('should not log when audit disabled', () => {
      enforcer.updateConfig({ auditEnabled: false });

      try {
        enforcer.enforcePermission('user-1', 'viewer', 'write', 'canvas', 'c1', [
          'write',
        ]);
      } catch {}

      const log = enforcer.getAuditLog();
      expect(log.length).toBe(0);
    });
  });

  describe('configuration management', () => {
    it('should get current configuration', () => {
      const config = enforcer.getConfig();

      expect(config).toEqual({
        enabled: true,
        auditEnabled: true,
        maxAuditEntries: 10000,
        defaultRole: 'viewer',
        redactionEnabled: true,
      });
    });

    it('should update configuration', () => {
      enforcer.updateConfig({
        enabled: false,
        defaultRole: 'editor',
      });

      const config = enforcer.getConfig();
      expect(config.enabled).toBe(false);
      expect(config.defaultRole).toBe('editor');
      // Other values unchanged
      expect(config.maxAuditEntries).toBe(10000);
    });
  });

  describe('reset', () => {
    it('should reset all state', () => {
      // Setup various state
      enforcer.defineRole('custom', 'Custom', 'desc', ['read']);
      enforcer.createPolicy('canvas', 'canvas-1');
      try {
        enforcer.enforcePermission('user-1', 'viewer', 'write', 'canvas', 'c1', [
          'write',
        ]);
      } catch {}

      enforcer.reset();

      // Verify state cleared
      expect(enforcer.getRole('custom')).toBeUndefined();
      expect(enforcer.getPolicy('canvas', 'canvas-1')).toBeUndefined();
      expect(enforcer.getAuditLog().length).toBe(0);
    });

    it('should preserve built-in roles after reset', () => {
      enforcer.defineRole('custom', 'Custom', 'desc', ['read']);
      enforcer.reset();

      const roles = enforcer.getAllRoles();
      expect(roles.length).toBe(4); // Only built-in roles remain
      expect(enforcer.getRole('admin')).toBeDefined();
    });

    it('should preserve configuration after reset', () => {
      enforcer.updateConfig({ defaultRole: 'editor' });
      enforcer.reset();

      const config = enforcer.getConfig();
      expect(config.defaultRole).toBe('editor');
    });
  });
});
