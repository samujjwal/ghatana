import { describe, expect, it } from 'vitest';

import { hasAdminAccess, isAuthenticatedUser } from '../auth-state';

const baseUser = {
  id: 'user-1',
  email: 'user@yappc.local',
  name: 'Test User',
  tenantId: 'tenant-1',
  workspaceIds: [],
} as const;

describe('auth-state', () => {
  it('treats null as unauthenticated', () => {
    expect(isAuthenticatedUser(null)).toBe(false);
  });

  it('treats a current user as authenticated', () => {
    expect(isAuthenticatedUser({ ...baseUser, role: 'USER' })).toBe(true);
  });

  it('grants admin access only to admins', () => {
    expect(hasAdminAccess({ ...baseUser, role: 'ADMIN' })).toBe(true);
    expect(hasAdminAccess({ ...baseUser, role: 'USER' })).toBe(false);
    expect(hasAdminAccess(null)).toBe(false);
  });
});