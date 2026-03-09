/**
 * Authentication mocks for testing
 */

import { vi } from 'vitest';

import { createUser, createAdminUser } from '../factories/user.factory';

import type { User } from '@ghatana/yappc-types';

/**
 * Mock authentication service for testing
 */
export const mockAuthService = {
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  getCurrentUser: vi.fn(),
  isAuthenticated: vi.fn(),
  hasPermission: vi.fn(),
  refreshToken: vi.fn(),
  updateProfile: vi.fn(),
};

/**
 * Reset all auth mock functions
 */
export function resetAuthMocks() {
  mockAuthService.login.mockReset();
  mockAuthService.logout.mockReset();
  mockAuthService.register.mockReset();
  mockAuthService.getCurrentUser.mockReset();
  mockAuthService.isAuthenticated.mockReset();
  mockAuthService.hasPermission.mockReset();
  mockAuthService.refreshToken.mockReset();
  mockAuthService.updateProfile.mockReset();
}

/**
 * Mock authenticated user
 *
 * @param user - User object (optional, will create one if not provided)
 * @returns The mock user
 */
export function mockAuthenticatedUser(user?: User): User {
  const mockUser = user || createUser();

  mockAuthService.getCurrentUser.mockReturnValue(mockUser);
  mockAuthService.isAuthenticated.mockReturnValue(true);
  mockAuthService.hasPermission.mockImplementation((permission: string) => {
    const role = (mockUser as unknown).role;
    if (role === 'admin') return true;
    if (role === 'user' && ['read', 'write'].includes(permission)) return true;
    return false;
  });

  return mockUser;
}

/**
 * Mock authenticated admin user
 *
 * @param user - Admin user object (optional, will create one if not provided)
 * @returns The mock admin user
 */
export function mockAuthenticatedAdmin(user?: User): User {
  const mockAdmin = user || createAdminUser();

  mockAuthService.getCurrentUser.mockReturnValue(mockAdmin);
  mockAuthService.isAuthenticated.mockReturnValue(true);
  mockAuthService.hasPermission.mockReturnValue(true);

  return mockAdmin;
}

/**
 * Mock unauthenticated state
 */
export function mockUnauthenticated() {
  mockAuthService.getCurrentUser.mockReturnValue(null);
  mockAuthService.isAuthenticated.mockReturnValue(false);
  mockAuthService.hasPermission.mockReturnValue(false);
}

/**
 * Mock authentication tokens
 */
export const mockAuthTokens = {
  accessToken: 'mock-access-token',
  refreshToken: 'mock-refresh-token',
  expiresIn: 3600,
};

/**
 * Mock successful login
 *
 * @param user - User object (optional, will create one if not provided)
 * @returns The mock user and tokens
 */
export function mockSuccessfulLogin(user?: User) {
  const mockUser = user || createUser();

  mockAuthService.login.mockResolvedValue({
    user: mockUser,
    tokens: mockAuthTokens,
  });

  mockAuthenticatedUser(mockUser);

  return { user: mockUser, tokens: mockAuthTokens };
}

/**
 * Mock failed login
 *
 * @param error - Error message or object
 */
export function mockFailedLogin(
  error: string | object = 'Invalid credentials'
) {
  const errorObj = typeof error === 'string' ? { message: error } : error;

  mockAuthService.login.mockRejectedValue(errorObj);
  mockUnauthenticated();
}
