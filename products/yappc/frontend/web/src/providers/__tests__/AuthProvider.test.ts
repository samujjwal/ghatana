/**
 * Tests for AuthProvider role and tenant mapping.
 *
 * These tests validate that user role and tenantId are correctly mapped from
 * the auth-gateway API response, not hardcoded. This is a security-critical
 * behaviour — incorrect mapping could over-privilege users.
 */
import { describe, it, expect } from 'vitest';
import { mapAuthSessionToUser } from '../auth-session';

type UserRole = 'ADMIN' | 'USER' | 'VIEWER';

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
      role: 'SUPERADMIN' as never,
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

  it('falls back to the generated roles array when role is absent', () => {
    const result = mapAuthSessionToUser({ id: 'u1', roles: ['VIEWER'], tenantId: 'acme' });
    expect(result.role).toBe('VIEWER');
  });
});

describe('mapAuthSessionToUser — tenantId mapping', () => {
  it('uses tenantId from API response', () => {
    const result = mapAuthSessionToUser({ id: 'u1', tenantId: 'org-xyz' });
    expect(result.tenantId).toBe('org-xyz');
  });

  it('throws error when API returns no tenantId', () => {
    expect(() => mapAuthSessionToUser({ id: 'u1' })).toThrow(
      'Tenant ID is required and cannot be default-tenant'
    );
  });

  it('throws error when tenantId is blank', () => {
    expect(() => mapAuthSessionToUser({ id: 'u1', tenantId: '   ' })).toThrow(
      'Tenant ID is required and cannot be default-tenant'
    );
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
    const result = mapAuthSessionToUser({ id: 'u1', firstName: 'Alice', lastName: 'Bob', tenantId: 'test-tenant' });
    expect(result.name).toBe('Alice Bob');
  });

  it('falls back to id when no name parts are present', () => {
    const result = mapAuthSessionToUser({ id: 'user-42', tenantId: 'test-tenant' });
    expect(result.name).toBe('user-42');
  });

  it('falls back to the generated name field when first and last name are absent', () => {
    const result = mapAuthSessionToUser({ id: 'u1', name: 'Platform Operator', tenantId: 'test-tenant' });
    expect(result.name).toBe('Platform Operator');
  });

  it('propagates workspaceIds from API response', () => {
    const result = mapAuthSessionToUser({ id: 'u1', workspaceIds: ['ws-1', 'ws-2'], tenantId: 'test-tenant' });
    expect(result.workspaceIds).toEqual(['ws-1', 'ws-2']);
  });

  it('defaults workspaceIds to empty array when absent', () => {
    const result = mapAuthSessionToUser({ id: 'u1', tenantId: 'test-tenant' });
    expect(result.workspaceIds).toEqual([]);
  });
});
