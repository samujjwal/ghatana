/**
 * AuthContext Tests
 *
 * Tests for authentication context and hooks.
 *
 * @doc.type test
 * @doc.purpose Test authentication context functionality
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth, useTenantId } from '../AuthContext';

interface MockUser {
  id: string;
  email: string;
  displayName: string;
  role: string;
  tenantId: string;
}

function createJwt(overrides: Record<string, unknown> = {}): string {
  const payload = {
    sub: 'user-123',
    email: 'test@example.com',
    name: 'Test User',
    role: 'student',
    tenantId: 'tenant-abc',
    ...overrides,
  };

  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const encodedPayload = btoa(JSON.stringify(payload))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');

  return `${header}.${encodedPayload}.signature`;
}

function createAuthUser(overrides: Partial<MockUser> = {}): MockUser {
  return {
    id: 'user-123',
    email: 'test@example.com',
    displayName: 'Test User',
    role: 'student',
    tenantId: 'tenant-abc',
    ...overrides,
  };
}

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear();
    window.history.replaceState({}, '', '/');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({}),
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe('AuthProvider', () => {
    it('should initialize with no user when no token in localStorage', () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });
      
      expect(result.current.user).toBeNull();
      expect(result.current.token).toBeNull();
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.isLoading).toBe(false);
    });

    it('should parse valid JWT token from localStorage', async () => {
      const validToken = createJwt();
      
      localStorage.setItem('auth_token', validToken);
      
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });
      
      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });
      
      expect(result.current.user).toEqual({
        id: 'user-123',
        email: 'test@example.com',
        displayName: 'Test User',
        role: 'student',
        tenantId: 'tenant-abc',
      });
      expect(result.current.isAuthenticated).toBe(true);
    });

    it('should prefer the backend session profile over decoded token claims', async () => {
      const validToken = createJwt({
        email: 'stale@example.com',
        name: 'Stale User',
        tenantId: 'tenant-stale',
      });
      const serverUser = createAuthUser({
        email: 'fresh@example.com',
        displayName: 'Fresh User',
        tenantId: 'tenant-fresh',
      });

      localStorage.setItem('auth_token', validToken);
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue({
          ok: true,
          json: async () => serverUser,
        }),
      );

      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isAuthenticated).toBe(true);
      });

      expect(result.current.user).toEqual(serverUser);
      expect(localStorage.getItem('tenant_id')).toBe('tenant-fresh');
      expect(fetch).toHaveBeenCalledWith('/api/v1/auth/me', {
        headers: {
          Authorization: `Bearer ${validToken}`,
        },
      });
    });

    it('should clear invalid token from localStorage', async () => {
      localStorage.setItem('auth_token', 'invalid-token');
      
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });
      
      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });
      
      expect(result.current.user).toBeNull();
      expect(result.current.isAuthenticated).toBe(false);
      expect(localStorage.getItem('auth_token')).toBeNull();
    });
  });

  describe('login', () => {
    it('should set user and token on successful login', async () => {
      const validToken = createJwt();
      
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });
      
      await act(async () => {
        await result.current.login(validToken);
      });
      
      expect(result.current.user).toEqual({
        id: 'user-123',
        email: 'test@example.com',
        displayName: 'Test User',
        role: 'student',
        tenantId: 'tenant-abc',
      });
      expect(result.current.token).toBe(validToken);
      expect(result.current.isAuthenticated).toBe(true);
      expect(localStorage.getItem('auth_token')).toBe(validToken);
      expect(localStorage.getItem('tenant_id')).toBe('tenant-abc');
    });

    it('should throw error on invalid token', async () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });

      let thrownError: unknown;
      try {
        await result.current.login('invalid-token');
      } catch (error) {
        thrownError = error;
      }

      expect(thrownError).toBeInstanceOf(Error);
      expect((thrownError as Error).message).toBe('Invalid token');
    });
  });

  describe('logout', () => {
    it('should clear user and token on logout', async () => {
      const validToken = createJwt();
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({}),
      });
      vi.stubGlobal('fetch', fetchMock);
      
      localStorage.setItem('auth_token', validToken);
      localStorage.setItem('refresh_token', 'refresh-token-123');
      
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });
      
      await waitFor(() => {
        expect(result.current.isAuthenticated).toBe(true);
      });
      
      act(() => {
        result.current.logout();
      });
      
      expect(result.current.user).toBeNull();
      expect(result.current.token).toBeNull();
      expect(result.current.isAuthenticated).toBe(false);
      expect(localStorage.getItem('auth_token')).toBeNull();
      expect(localStorage.getItem('tenant_id')).toBeNull();
      expect(localStorage.getItem('refresh_token')).toBeNull();
      expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ refreshToken: 'refresh-token-123' }),
      });
    });
  });

  describe('refreshToken', () => {
    it('should refresh the session with only the stored refresh token', async () => {
      const nextAccessToken = createJwt({
        sub: 'user-456',
        email: 'refreshed@example.com',
        name: 'Refreshed User',
        tenantId: 'tenant-refresh',
      });
      const nextRefreshToken = 'refresh-token-next';
      const serverUser = createAuthUser({
        id: 'user-456',
        email: 'refreshed@example.com',
        displayName: 'Refreshed User',
        tenantId: 'tenant-refresh',
      });
      const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
        const href = typeof input === 'string' ? input : input.toString();

        if (href === '/api/v1/auth/refresh') {
          return {
            ok: true,
            json: async () => ({
              accessToken: nextAccessToken,
              refreshToken: nextRefreshToken,
            }),
          } as Response;
        }

        if (href === '/api/v1/auth/me') {
          return {
            ok: true,
            json: async () => serverUser,
          } as Response;
        }

        return {
          ok: false,
          json: async () => ({}),
        } as Response;
      });
      vi.stubGlobal('fetch', fetchMock);
      localStorage.setItem('refresh_token', 'refresh-token-current');

      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await result.current.refreshToken();
      });

      expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/refresh', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ refreshToken: 'refresh-token-current' }),
      });
      expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/me', {
        headers: {
          Authorization: `Bearer ${nextAccessToken}`,
        },
      });
      expect(result.current.user).toEqual(serverUser);
      expect(result.current.token).toBe(nextAccessToken);
      expect(result.current.isAuthenticated).toBe(true);
      expect(localStorage.getItem('auth_token')).toBe(nextAccessToken);
      expect(localStorage.getItem('refresh_token')).toBe(nextRefreshToken);
      expect(localStorage.getItem('tenant_id')).toBe('tenant-refresh');
    });

    it('should reject refresh when no refresh token is stored', async () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      let thrownError: unknown;
      try {
        await result.current.refreshToken();
      } catch (error) {
        thrownError = error;
      }

      expect(thrownError).toBeInstanceOf(Error);
      expect((thrownError as Error).message).toBe('No refresh token available');
    });
  });

  describe('useTenantId', () => {
    it('should persist tenant ID when authenticated', async () => {
      const validToken = createJwt();
      
      localStorage.setItem('auth_token', validToken);
      
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      renderHook(() => useAuth(), { wrapper });
      
      await waitFor(() => {
        expect(localStorage.getItem('tenant_id')).toBe('tenant-abc');
      });
    });

    it('should consume callback tokens from the URL on startup', async () => {
      const accessToken = createJwt();
      const refreshToken = 'refresh-token-123';
      window.history.replaceState(
        {},
        '',
        `/?accessToken=${encodeURIComponent(accessToken)}&refreshToken=${encodeURIComponent(refreshToken)}`,
      );

      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isAuthenticated).toBe(true);
      });

      expect(localStorage.getItem('auth_token')).toBe(accessToken);
      expect(localStorage.getItem('refresh_token')).toBe(refreshToken);
      expect(window.location.search).toBe('');
    });

    it('should throw error when not authenticated', () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      expect(() => {
        renderHook(() => useTenantId(), { wrapper });
      }).toThrow('Authentication required: No valid tenant context');
    });
  });
});
