/**
 * TutorPutor - Authentication Context
 *
 * Provides authentication state and methods across the application.
 * Uses shared token utilities from `@tutorputor/ui` (F-030).
 *
 * @doc.type context
 * @doc.purpose Authentication state management
 * @doc.layer product
 * @doc.pattern Context Provider
 */

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import {
  AUTH_TOKEN_KEY,
  REFRESH_TOKEN_KEY,
  TENANT_ID_KEY,
  extractUserFromToken,
  persistTokens,
  readAccessToken,
  clearAuthStorage,
  type TutorPutorJwtUser,
} from '@tutorputor/ui';

interface AuthState {
  user: TutorPutorJwtUser | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
}

interface AuthContextValue extends AuthState {
  login: (token: string) => Promise<void>;
  logout: () => void;
  refreshToken: () => Promise<void>;
}

// Local alias so the rest of this file uses the product-specific name
type AuthUser = TutorPutorJwtUser;

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
   * Parse JWT token to extract user information.
   * Delegates to the shared `extractUserFromToken` from `@tutorputor/ui` (F-030).
   */
  const parseToken = useCallback((token: string): AuthUser | null => {
    return extractUserFromToken(token);
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

    // F-002: SSO callback now delivers an opaque short-lived code, not raw tokens.
    // Exchange the code server-side to get the actual access + refresh tokens.
    const ssoCode = params.get("sso_code");
    if (ssoCode) {
      params.delete("sso_code");
      const nextSearch = params.toString();
      window.history.replaceState(
        {},
        "",
        `${window.location.pathname}${nextSearch ? `?${nextSearch}` : ""}${window.location.hash}`,
      );

      void (async () => {
        try {
          const response = await fetch("/api/v1/auth/sso/exchange", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ ssoCode }),
          });
          if (!response.ok) {
            throw new Error(`SSO exchange failed: HTTP ${response.status}`);
          }
          const data = await response.json() as { accessToken: string; refreshToken?: string };
          persistTokens(data.accessToken, data.refreshToken);
          await login(data.accessToken);
        } catch (err) {
          console.error("[AuthContext] SSO code exchange failed", err);
        }
      })();
      return;
    }

    // Legacy path: raw tokens in URL (should no longer be emitted by the server
    // after F-002, but kept for backwards-compatibility during transition).
    const callbackAccessToken = params.get("accessToken");
    const callbackRefreshToken = params.get("refreshToken");

    if (callbackAccessToken) {
      localStorage.setItem(AUTH_TOKEN_KEY, callbackAccessToken);
    }
    if (callbackRefreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, callbackRefreshToken);
    }
    if (callbackAccessToken ?? callbackRefreshToken) {
      params.delete("accessToken");
      params.delete("refreshToken");
      const nextSearch = params.toString();
      window.history.replaceState(
        {},
        "",
        `${window.location.pathname}${nextSearch ? `?${nextSearch}` : ""}${window.location.hash}`,
      );
    }

    const token = readAccessToken();
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
          localStorage.setItem(TENANT_ID_KEY, user.tenantId);
          return;
        }

        clearAuthStorage();
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

    persistTokens(token);
    localStorage.setItem(TENANT_ID_KEY, user.tenantId);
    
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
    const refreshTokenValue = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (refreshTokenValue) {
      void fetch('/api/v1/auth/logout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: refreshTokenValue }),
      });
    }

    clearAuthStorage();
    
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
    const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!storedRefreshToken) {
      throw new Error('No refresh token available');
    }

    const response = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: storedRefreshToken }),
    });

    if (!response.ok) {
      throw new Error('Failed to refresh token');
    }

    const data = await response.json() as { accessToken: string; refreshToken?: string };
    await login(data.accessToken);
    persistTokens(data.accessToken, data.refreshToken);
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
