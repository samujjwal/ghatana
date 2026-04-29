/**
 * Unified Auth Context
 *
 * Shared authentication state primitive and provider for TutorPutor applications.
 * Used by both web learner app and admin app to ensure consistent auth behavior.
 *
 * @doc.type module
 * @doc.purpose Unified authentication context for all TutorPutor apps
 * @doc.layer platform
 * @doc.pattern Context Provider
 */

import React, { createContext, useContext, useState, useEffect, useCallback } from "react";

export const AUTH_TOKEN_KEY = "tutorputor_auth_token";
export const REFRESH_TOKEN_KEY = "tutorputor_refresh_token";
export const TENANT_ID_KEY = "tutorputor_tenant_id";

export interface TutorPutorJwtUser {
  id: string;
  email: string;
  tenantId: string;
  role: string;
  displayName?: string;
  firstName?: string;
  lastName?: string;
  fullName?: string;
}

export function extractUserFromToken(token: string): TutorPutorJwtUser | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = JSON.parse(atob(parts[1])) as Record<string, unknown>;
    return {
      id: String(payload.sub ?? ""),
      email: String(payload.email ?? ""),
      tenantId: String(payload.tenantId ?? ""),
      role: String(payload.role ?? "student"),
      displayName: payload.name != null ? String(payload.name) : undefined,
    };
  } catch {
    return null;
  }
}

export function persistTokens(accessToken: string, refreshToken?: string): void {
  localStorage.setItem(AUTH_TOKEN_KEY, accessToken);
  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
}

export function readAccessToken(): string | null {
  return localStorage.getItem(AUTH_TOKEN_KEY);
}

export function clearAuthStorage(): void {
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(TENANT_ID_KEY);
}

export function getSafeStorage(): Storage | null {
  try {
    return typeof window !== "undefined" ? window.localStorage : null;
  } catch {
    return null;
  }
}

export interface User {
  id: string;
  email: string;
  tenantId: string;
  role: string;
  displayName?: string;
  firstName?: string;
  lastName?: string;
  fullName?: string;
}

export interface AuthContextValue {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (token: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshToken: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

interface AuthProviderProps {
  children: React.ReactNode;
  onAuthChange?: (isAuthenticated: boolean) => void;
}

export function AuthProvider({ children, onAuthChange }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const readAccessToken = useCallback((): string | null => {
    return localStorage.getItem(AUTH_TOKEN_KEY);
  }, []);

  const persistTokens = useCallback((accessToken: string, refreshToken?: string) => {
    localStorage.setItem(AUTH_TOKEN_KEY, accessToken);
    if (refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    }
  }, []);

  const clearAuthStorage = useCallback(() => {
    localStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }, []);

  const parseToken = useCallback((token: string): User | null => {
    try {
      const parts = token.split(".");
      if (parts.length !== 3) return null;

      const payload = JSON.parse(atob(parts[1]));
      return {
        id: payload.sub,
        email: payload.email,
        tenantId: payload.tenantId,
        role: payload.role || "student",
        displayName: payload.name,
      };
    } catch {
      return null;
    }
  }, []);

  const fetchCurrentUser = useCallback(async (accessToken: string): Promise<User | null> => {
    try {
      const response = await fetch("/api/v1/auth/me", {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      if (response.ok) {
        const data = await response.json();
        return data;
      }
      return null;
    } catch {
      return null;
    }
  }, []);

  const login = useCallback(async (accessToken: string) => {
    const parsedUser = parseToken(accessToken);
    const user = (await fetchCurrentUser(accessToken)) ?? parsedUser;
    if (!user) {
      throw new Error("Invalid token");
    }

    persistTokens(accessToken);
    setToken(accessToken);
    setUser(user);
    setIsLoading(false);
    onAuthChange?.(true);
  }, [parseToken, fetchCurrentUser, persistTokens, onAuthChange]);

  const logout = useCallback(async () => {
    const refreshTokenValue = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (refreshTokenValue) {
      try {
        await fetch("/api/v1/auth/logout", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken: refreshTokenValue }),
        });
      } catch {
        // Logout stays idempotent
      }
    }

    clearAuthStorage();
    setToken(null);
    setUser(null);
    setIsLoading(false);
    onAuthChange?.(false);
  }, [clearAuthStorage, onAuthChange]);

  const refreshToken = useCallback(async () => {
    const refreshTokenValue = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshTokenValue) {
      await logout();
      return;
    }

    try {
      const response = await fetch("/api/v1/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: refreshTokenValue }),
      });

      if (response.ok) {
        const data = await response.json();
        const parsedUser = parseToken(data.accessToken);
        const user = (await fetchCurrentUser(data.accessToken)) ?? parsedUser;
        
        persistTokens(data.accessToken, data.refreshToken);
        setToken(data.accessToken);
        setUser(user);
      } else {
        await logout();
      }
    } catch {
      await logout();
    }
  }, [logout, parseToken, fetchCurrentUser, persistTokens]);

  useEffect(() => {
    const storedToken = readAccessToken();
    if (storedToken) {
      (async () => {
        const parsedUser = parseToken(storedToken);
        const user = (await fetchCurrentUser(storedToken)) ?? parsedUser;
        
        if (user) {
          setToken(storedToken);
          setUser(user);
          onAuthChange?.(true);
        }
        setIsLoading(false);
      })();
    } else {
      setIsLoading(false);
    }
  }, [readAccessToken, parseToken, fetchCurrentUser, onAuthChange]);

  const value: AuthContextValue = {
    user,
    token,
    isAuthenticated: !!user,
    isLoading,
    login,
    logout,
    refreshToken,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
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
    throw new Error("Authentication required: No valid tenant context");
  }
  
  return user.tenantId;
}
