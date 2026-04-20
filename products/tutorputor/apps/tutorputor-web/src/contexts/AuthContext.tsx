/**
 * TutorPutor - Authentication Context
 *
 * Provides authentication state and methods across the application.
 * Replaces hardcoded mock authentication with proper auth integration.
 *
 * @doc.type context
 * @doc.purpose Authentication state management
 * @doc.layer product
 * @doc.pattern Context Provider
 */

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  role: string;
  tenantId: string;
}

interface AuthState {
  user: AuthUser | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
}

interface AuthContextValue extends AuthState {
  login: (token: string) => Promise<void>;
  logout: () => void;
  refreshToken: () => Promise<void>;
}

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const normalizedPayload = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const paddedPayload = normalizedPayload.padEnd(
      normalizedPayload.length + ((4 - (normalizedPayload.length % 4)) % 4),
      '=',
    );

    return JSON.parse(atob(paddedPayload)) as Record<string, unknown>;
  } catch {
    return null;
  }
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

interface AuthProviderProps {
  children: React.ReactNode;
}

/**
 * Authentication Provider
 * Manages authentication state and provides auth methods
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const [state, setState] = useState<AuthState>({
    user: null,
    token: null,
    isLoading: true,
    isAuthenticated: false,
  });

  /**
   * Parse JWT token to extract user information
   */
  const parseToken = useCallback((token: string): AuthUser | null => {
    const payload = decodeJwtPayload(token);
    if (!payload) {
      return null;
    }

    const id = typeof payload.sub === 'string'
      ? payload.sub
      : typeof payload.userId === 'string'
        ? payload.userId
        : null;
    const tenantId = typeof payload.tenantId === 'string'
      ? payload.tenantId
      : typeof payload.tenant === 'string'
        ? payload.tenant
        : null;

    if (!id || !tenantId) {
      return null;
    }

    return {
      id,
      email: typeof payload.email === 'string' ? payload.email : 'unknown',
      displayName:
        typeof payload.name === 'string'
          ? payload.name
          : typeof payload.displayName === 'string'
            ? payload.displayName
            : 'User',
      role: typeof payload.role === 'string' ? payload.role : 'student',
      tenantId,
    };
  }, []);

  const fetchCurrentUser = useCallback(async (token: string): Promise<AuthUser | null> => {
    const response = await fetch('/api/v1/auth/me', {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    if (!response.ok) {
      return null;
    }

    const user = (await response.json()) as Partial<AuthUser>;
    if (!user.id || !user.tenantId || !user.email || !user.displayName || !user.role) {
      return null;
    }

    return user as AuthUser;
  }, []);

  /**
   * Initialize auth state from localStorage
   */
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const callbackAccessToken = params.get('accessToken');
    const callbackRefreshToken = params.get('refreshToken');

    if (callbackAccessToken) {
      localStorage.setItem('auth_token', callbackAccessToken);
    }
    if (callbackRefreshToken) {
      localStorage.setItem('refresh_token', callbackRefreshToken);
    }
    if (callbackAccessToken || callbackRefreshToken) {
      params.delete('accessToken');
      params.delete('refreshToken');
      const nextSearch = params.toString();
      window.history.replaceState(
        {},
        '',
        `${window.location.pathname}${nextSearch ? `?${nextSearch}` : ''}${window.location.hash}`,
      );
    }

    const token = localStorage.getItem('auth_token');
    if (token) {
      void (async () => {
        const parsedUser = parseToken(token);
        const user = (await fetchCurrentUser(token)) ?? parsedUser;

        if (user) {
          setState({
            user,
            token,
            isLoading: false,
            isAuthenticated: true,
          });
          localStorage.setItem('tenant_id', user.tenantId);
          return;
        }

        localStorage.removeItem('auth_token');
        localStorage.removeItem('refresh_token');
        setState({
          user: null,
          token: null,
          isLoading: false,
          isAuthenticated: false,
        });
      })();
    } else {
      setState({
        user: null,
        token: null,
        isLoading: false,
        isAuthenticated: false,
      });
    }
  }, [fetchCurrentUser, parseToken]);

  /**
   * Login with token
   */
  const login = useCallback(async (token: string) => {
    const parsedUser = parseToken(token);
    const user = (await fetchCurrentUser(token)) ?? parsedUser;
    if (!user) {
      throw new Error('Invalid token');
    }

    localStorage.setItem('auth_token', token);
    localStorage.setItem('tenant_id', user.tenantId);
    
    setState({
      user,
      token,
      isLoading: false,
      isAuthenticated: true,
    });
  }, [fetchCurrentUser, parseToken]);

  /**
   * Logout user
   */
  const logout = useCallback(() => {
    const refreshTokenValue = localStorage.getItem('refresh_token');
    if (refreshTokenValue) {
      void fetch('/api/v1/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ refreshToken: refreshTokenValue }),
      });
    }

    localStorage.removeItem('auth_token');
    localStorage.removeItem('tenant_id');
    localStorage.removeItem('refresh_token');
    
    setState({
      user: null,
      token: null,
      isLoading: false,
      isAuthenticated: false,
    });
  }, []);

  /**
   * Refresh token
   */
  const refreshToken = useCallback(async () => {
    const storedRefreshToken = localStorage.getItem('refresh_token');
    if (!storedRefreshToken) {
      throw new Error('No refresh token available');
    }

    const response = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        refreshToken: storedRefreshToken,
      }),
    });

    if (!response.ok) {
      throw new Error('Failed to refresh token');
    }

    const data = await response.json() as { accessToken: string; refreshToken?: string };
    await login(data.accessToken);
    
    if (data.refreshToken) {
      localStorage.setItem('refresh_token', data.refreshToken);
    }
  }, [login]);

  const value: AuthContextValue = {
    ...state,
    login,
    logout,
    refreshToken,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * Hook to use authentication context
 */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

/**
 * Hook to get tenant ID with proper validation
 * Throws error if not authenticated (no fallback to stub)
 */
export function useTenantId(): string {
  const { user, isAuthenticated } = useAuth();
  
  if (!isAuthenticated || !user) {
    throw new Error('Authentication required: No valid tenant context');
  }
  
  return user.tenantId;
}
