/**
 * useAuth Hook
 *
 * Reactive wrapper around the singleton AuthService.
 * Provides real-time auth state, current user, token accessor,
 * and permission/role helpers to any component or hook.
 *
 * @doc.type hook
 * @doc.purpose Auth state access for components and hooks
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useEffect, useCallback } from 'react';

import { authService } from '../services/auth/AuthService';
import type { User, AuthSession } from '../services/auth/AuthService';

export interface UseAuthReturn {
  /** Whether a valid, non-expired session exists */
  isAuthenticated: boolean;
  /** Current user, or null when unauthenticated */
  currentUser: User | null;
  /** Current session, or null when unauthenticated */
  currentSession: AuthSession | null;
  /** Returns the Bearer token string, or null */
  getToken: () => string | null;
  /** Returns Authorization header value ready for fetch, or null */
  getAuthHeader: () => string | null;
  /** Check whether the user holds a specific permission string */
  hasPermission: (permission: string) => boolean;
  /** Check whether the user has a specific role */
  hasRole: (role: string) => boolean;
  /** Trigger logout */
  logout: () => Promise<void>;
}

/**
 * Hook providing reactive access to the current auth state.
 * Re-evaluates whenever the storage event fires (cross-tab sync)
 * or when called after login/logout.
 */
export function useAuth(): UseAuthReturn {
  const [session, setSession] = useState<AuthSession | null>(
    () => authService.getCurrentSession()
  );

  // Re-sync from AuthService on storage changes (cross-tab) and focus
  const syncSession = useCallback(() => {
    setSession(authService.getCurrentSession());
  }, []);

  useEffect(() => {
    window.addEventListener('storage', syncSession);
    window.addEventListener('focus', syncSession);
    return () => {
      window.removeEventListener('storage', syncSession);
      window.removeEventListener('focus', syncSession);
    };
  }, [syncSession]);

  const isAuthenticated = authService.isAuthenticated();

  const getToken = useCallback((): string | null => {
    return authService.getAuthToken();
  }, []);

  const getAuthHeader = useCallback((): string | null => {
    const token = authService.getAuthToken();
    return token ? `Bearer ${token}` : null;
  }, []);

  const hasPermission = useCallback((permission: string): boolean => {
    return authService.hasPermission(permission);
  }, []);

  const hasRole = useCallback((role: string): boolean => {
    return authService.hasRole(role);
  }, []);

  const logout = useCallback(async (): Promise<void> => {
    await authService.logout();
    setSession(null);
  }, []);

  return {
    isAuthenticated,
    currentUser: session?.user ?? null,
    currentSession: session,
    getToken,
    getAuthHeader,
    hasPermission,
    hasRole,
    logout,
  };
}
