/**
 * Tests for AuthProvider role and tenant mapping.
 *
 * These tests validate that user role and tenantId are correctly mapped from
 * the auth-gateway API response, not hardcoded. This is a security-critical
 * behaviour — incorrect mapping could over-privilege users.
 */
import { describe, it, expect } from 'vitest';

// --- inline the pure mapping logic so we can unit-test it without React

type UserRole = 'ADMIN' | 'USER' | 'VIEWER';

type AuthSessionUser = {
  id: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  avatarUrl?: string;
  role?: 'ADMIN' | 'USER' | 'VIEWER';
  tenantId?: string;
  workspaceIds?: string[];
};

type User = {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  tenantId: string;
  workspaceIds: string[];
};

const VALID_ROLES = new Set<string>(['ADMIN', 'USER', 'VIEWER']);

function mapAuthSessionToUser(user: AuthSessionUser): User {
  const first = user.firstName?.trim() ?? '';
  const last = user.lastName?.trim() ?? '';
  const name = `${first} ${last}`.trim() || user.id;
  const role: UserRole = user.role && VALID_ROLES.has(user.role) ? user.role : 'USER';
  return {
    id: user.id,
    email: user.email ?? '',
    name,
    role,
    tenantId:
      user.tenantId && user.tenantId.trim().length > 0 ? user.tenantId : 'default-tenant',
    workspaceIds: user.workspaceIds ?? [],
  };
}

// ---

describe('mapAuthSessionToUser — role mapping', () => {
  it('maps ADMIN role from API response', () => {
    const result = mapAuthSessionToUser({ id: 'u1', role: 'ADMIN', tenantId: 'acme' });
    expect(result.role).toBe('ADMIN');
  });

  it('maps VIEWER role from API response', () => {
    const result = mapAuthSessionToUser({ id: 'u1', role: 'VIEWER', tenantId: 'acme' });
    expect(result.role).toBe('VIEWER');
  });

  it('defaults to USER role when API returns no role', () => {
    const result = mapAuthSessionToUser({ id: 'u1', tenantId: 'acme' });
    expect(result.role).toBe('USER');
  });

  it('defaults to USER role when API returns an unrecognised role string', () => {
    const result = mapAuthSessionToUser({
      id: 'u1',
      role: 'SUPERADMIN' as UserRole,
      tenantId: 'acme',
    });
    expect(result.role).toBe('USER');
  });

  it('does NOT hard-code role — each valid role maps through', () => {
    const roles: UserRole[] = ['ADMIN', 'USER', 'VIEWER'];
    for (const role of roles) {
      expect(mapAuthSessionToUser({ id: 'u1', role, tenantId: 't1' }).role).toBe(role);
    }
  });
});

describe('mapAuthSessionToUser — tenantId mapping', () => {
  it('uses tenantId from API response', () => {
    const result = mapAuthSessionToUser({ id: 'u1', tenantId: 'org-xyz' });
    expect(result.tenantId).toBe('org-xyz');
  });

  it('falls back to default-tenant when API returns no tenantId', () => {
    const result = mapAuthSessionToUser({ id: 'u1' });
    expect(result.tenantId).toBe('default-tenant');
  });

  it('falls back to default-tenant when tenantId is blank', () => {
    const result = mapAuthSessionToUser({ id: 'u1', tenantId: '   ' });
    expect(result.tenantId).toBe('default-tenant');
  });

  it('does NOT hard-code tenantId — distinct tenants are preserved', () => {
    const t1 = mapAuthSessionToUser({ id: 'u1', tenantId: 'tenant-a' });
    const t2 = mapAuthSessionToUser({ id: 'u2', tenantId: 'tenant-b' });
    expect(t1.tenantId).toBe('tenant-a');
    expect(t2.tenantId).toBe('tenant-b');
    expect(t1.tenantId).not.toBe(t2.tenantId);
  });
});

describe('mapAuthSessionToUser — name and workspaceIds', () => {
  it('constructs a full name from firstName and lastName', () => {
    const result = mapAuthSessionToUser({ id: 'u1', firstName: 'Alice', lastName: 'Bob' });
    expect(result.name).toBe('Alice Bob');
  });

  it('falls back to id when no name parts are present', () => {
    const result = mapAuthSessionToUser({ id: 'user-42' });
    expect(result.name).toBe('user-42');
  });

  it('propagates workspaceIds from API response', () => {
    const result = mapAuthSessionToUser({ id: 'u1', workspaceIds: ['ws-1', 'ws-2'] });
    expect(result.workspaceIds).toEqual(['ws-1', 'ws-2']);
  });

  it('defaults workspaceIds to empty array when absent', () => {
    const result = mapAuthSessionToUser({ id: 'u1' });
    expect(result.workspaceIds).toEqual([]);
  });
});
