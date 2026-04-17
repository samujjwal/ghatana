import { describe, expect, it } from 'vitest';

import { AuthService, JWTManager, RBACManager } from './index.js';
import type { User } from './index.js';

const rbacManager = new RBACManager();

const testUser: User = {
  id: 'user-1',
  email: 'learner@tutorputor.local',
  tenantId: 'tenant-1',
  roles: [rbacManager.getRole('student')!],
  permissions: [],
  isActive: true,
};

describe('legacy auth adapter', () => {
  it('fails closed by default for password login', async () => {
    const service = new AuthService();

    await expect(service.login('user@example.com', 'secret', 'tenant-1')).rejects.toThrow(
      'Legacy auth adapter is not wired to a user repository',
    );
  });

  it('fails closed by default for refresh token lookups', async () => {
    const supportedRepository = {
      validateCredentials: async (): Promise<User | null> => testUser,
      updateUserLastLogin: async (): Promise<void> => {},
      findUserById: async (): Promise<User | null> => testUser,
    };

    const service = new AuthService(supportedRepository);
    const tokens = await service.login(testUser.email, 'secret', testUser.tenantId);

    const defaultManager = new JWTManager();
    await expect(defaultManager.refreshToken(tokens.refreshToken)).rejects.toThrow(
      'Legacy auth adapter is not wired to a user repository',
    );
  });

  it('supports injected repository implementations without mock fallbacks', async () => {
    const repository = {
      validateCredentials: async (): Promise<User | null> => testUser,
      updateUserLastLogin: async (): Promise<void> => {},
      findUserById: async (): Promise<User | null> => testUser,
    };

    const service = new AuthService(repository);
    const tokens = await service.login(testUser.email, 'secret', testUser.tenantId);

    expect(tokens.tokenType).toBe('Bearer');
    expect(tokens.accessToken).toBeTruthy();

    const refreshManager = new JWTManager(repository);
    const refreshed = await refreshManager.refreshToken(tokens.refreshToken);

    expect(refreshed.accessToken).toBeTruthy();
    expect(refreshed.refreshToken).toBeTruthy();
  });
});