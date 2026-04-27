/**
 * useAuth Hook Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useAuth } from '../useAuth';

// ---------------------------------------------------------------------------
// Mock AuthService singleton
// ---------------------------------------------------------------------------

const mockAuthService = vi.hoisted(() => ({
  getCurrentSession: vi.fn(),
  isAuthenticated: vi.fn(),
  getAuthToken: vi.fn(),
  hasPermission: vi.fn(),
  hasRole: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('../../services/auth/AuthService', () => ({
  authService: mockAuthService,
}));

const mockSession = {
  user: {
    id: 'user-1',
    email: 'test@example.com',
    name: 'Test User',
    roles: ['user', 'admin'],
    permissions: ['read', 'write', 'approvals:decide'],
  },
  token: 'mock-token-123',
  expiresAt: new Date(Date.now() + 3600 * 1000).toISOString(),
};

describe('useAuth', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthService.getCurrentSession.mockReturnValue(null);
    mockAuthService.isAuthenticated.mockReturnValue(false);
    mockAuthService.getAuthToken.mockReturnValue(null);
    mockAuthService.hasPermission.mockReturnValue(false);
    mockAuthService.hasRole.mockReturnValue(false);
    mockAuthService.logout.mockResolvedValue(undefined);
  });

  it('returns unauthenticated state when no session exists', () => {
    const { result } = renderHook(() => useAuth());

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.currentUser).toBeNull();
    expect(result.current.currentSession).toBeNull();
  });

  it('returns authenticated state when session exists', () => {
    mockAuthService.getCurrentSession.mockReturnValue(mockSession);
    mockAuthService.isAuthenticated.mockReturnValue(true);

    const { result } = renderHook(() => useAuth());

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.currentUser).toBe(mockSession.user);
    expect(result.current.currentSession).toBe(mockSession);
  });

  it('getToken returns token from auth service', () => {
    mockAuthService.getAuthToken.mockReturnValue('my-token');

    const { result } = renderHook(() => useAuth());

    expect(result.current.getToken()).toBe('my-token');
  });

  it('getToken returns null when not authenticated', () => {
    mockAuthService.getAuthToken.mockReturnValue(null);

    const { result } = renderHook(() => useAuth());

    expect(result.current.getToken()).toBeNull();
  });

  it('getAuthHeader returns Bearer header when token exists', () => {
    mockAuthService.getAuthToken.mockReturnValue('abc-token');

    const { result } = renderHook(() => useAuth());

    expect(result.current.getAuthHeader()).toBe('Bearer abc-token');
  });

  it('getAuthHeader returns null when no token', () => {
    mockAuthService.getAuthToken.mockReturnValue(null);

    const { result } = renderHook(() => useAuth());

    expect(result.current.getAuthHeader()).toBeNull();
  });

  it('hasPermission delegates to auth service', () => {
    mockAuthService.hasPermission.mockImplementation(
      (p: string) => p === 'approvals:decide'
    );

    const { result } = renderHook(() => useAuth());

    expect(result.current.hasPermission('approvals:decide')).toBe(true);
    expect(result.current.hasPermission('admin:delete')).toBe(false);
  });

  it('hasRole delegates to auth service', () => {
    mockAuthService.hasRole.mockImplementation(
      (r: string) => r === 'admin'
    );

    const { result } = renderHook(() => useAuth());

    expect(result.current.hasRole('admin')).toBe(true);
    expect(result.current.hasRole('guest')).toBe(false);
  });

  it('logout calls auth service and clears session', async () => {
    mockAuthService.getCurrentSession.mockReturnValue(mockSession);

    const { result } = renderHook(() => useAuth());

    expect(result.current.currentSession).toBe(mockSession);

    await act(async () => {
      await result.current.logout();
    });

    expect(mockAuthService.logout).toHaveBeenCalledOnce();
    expect(result.current.currentSession).toBeNull();
  });

  it('syncs session on storage event', () => {
    mockAuthService.getCurrentSession.mockReturnValue(null);

    const { result } = renderHook(() => useAuth());

    expect(result.current.currentSession).toBeNull();

    // Simulate another tab updating session
    mockAuthService.getCurrentSession.mockReturnValue(mockSession);

    act(() => {
      window.dispatchEvent(new Event('storage'));
    });

    expect(result.current.currentSession).toBe(mockSession);
  });

  it('syncs session on window focus', () => {
    mockAuthService.getCurrentSession.mockReturnValue(null);

    const { result } = renderHook(() => useAuth());

    mockAuthService.getCurrentSession.mockReturnValue(mockSession);

    act(() => {
      window.dispatchEvent(new Event('focus'));
    });

    expect(result.current.currentSession).toBe(mockSession);
  });

  it('removes event listeners on unmount', () => {
    const removeSpy = vi.spyOn(window, 'removeEventListener');

    const { unmount } = renderHook(() => useAuth());
    unmount();

    expect(removeSpy).toHaveBeenCalledWith('storage', expect.any(Function));
    expect(removeSpy).toHaveBeenCalledWith('focus', expect.any(Function));
  });
});
