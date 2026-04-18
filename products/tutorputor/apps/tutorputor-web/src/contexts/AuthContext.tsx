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
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;

      const payload = JSON.parse(atob(parts[1]));
      
      return {
        id: payload.sub || payload.userId,
        email: payload.email || 'unknown',
        displayName: payload.name || payload.displayName || 'User',
        role: payload.role || 'student',
        tenantId: payload.tenantId || payload.tenant,
      };
    } catch (error) {
      console.error('Failed to parse JWT token:', error);
      return null;
    }
  }, []);

  /**
   * Initialize auth state from localStorage
   */
  useEffect(() => {
    const token = localStorage.getItem('auth_token');
    if (token) {
      const user = parseToken(token);
      if (user) {
        setState({
          user,
          token,
          isLoading: false,
          isAuthenticated: true,
        });
        // Store tenant_id for API clients
        localStorage.setItem('tenant_id', user.tenantId);
      } else {
        // Invalid token, clear it
        localStorage.removeItem('auth_token');
        setState({
          user: null,
          token: null,
          isLoading: false,
          isAuthenticated: false,
        });
      }
    } else {
      setState({
        user: null,
        token: null,
        isLoading: false,
        isAuthenticated: false,
      });
    }
  }, [parseToken]);

  /**
   * Login with token
   */
  const login = useCallback(async (token: string) => {
    const user = parseToken(token);
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
  }, [parseToken]);

  /**
   * Logout user
   */
  const logout = useCallback(() => {
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
    const currentToken = localStorage.getItem('auth_token');
    if (!currentToken) {
      throw new Error('No token to refresh');
    }

    // Call refresh endpoint
    const response = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${currentToken}`,
      },
      body: JSON.stringify({
        refreshToken: localStorage.getItem('refresh_token'),
      }),
    });

    if (!response.ok) {
      throw new Error('Failed to refresh token');
    }

    const data = await response.json();
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
