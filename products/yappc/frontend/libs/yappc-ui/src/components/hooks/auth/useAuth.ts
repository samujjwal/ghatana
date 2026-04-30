import { useAtom } from 'jotai';
import { useCallback, useEffect, useState } from 'react';

import {
  authStateAtom,
  type AuthState,
} from 'yappc-state';
import type { User } from 'yappc-core/types';

interface AuthUserProfile extends User {
  avatar?: string;
  permissions?: string[];
  workspaces?: string[];
  lastLogin?: number;
}

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface RegisterData extends LoginCredentials {
  name: string;
}

interface AuthSuccessResult {
  success: true;
  user: AuthUserProfile;
  token: string;
}

interface AuthFailureResult {
  success: false;
  error: string;
}

type AuthMutationResult = AuthSuccessResult | AuthFailureResult;

interface AuthResponse {
  user: AuthUserProfile;
  token: string;
}

function readErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function readAuthRole(user: AuthUserProfile | null): string | null {
  if (!user?.role) {
    return null;
  }
  return String(user.role).toLowerCase();
}

function readPermissions(user: AuthUserProfile | null): string[] {
  return Array.isArray(user?.permissions) ? user.permissions : [];
}

function readWorkspaces(user: AuthUserProfile | null): string[] {
  return Array.isArray(user?.workspaces) ? user.workspaces : [];
}

async function parseJsonPayload<T>(
  response: Response,
  context: string
): Promise<T> {
  const raw = await response.text();

  if (!raw) {
    throw new Error(`${context} returned an empty response`);
  }

  try {
    return JSON.parse(raw) as T;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`${context} returned invalid JSON: ${detail}`);
  }
}

function parseStoredAuthUser(userData: string): AuthUserProfile {
  const payload = JSON.parse(userData) as unknown;
  if (typeof payload !== 'object' || payload === null) {
    throw new Error('Stored auth user was invalid');
  }

  return payload as AuthUserProfile;
}

async function parseAuthResponse(response: Response): Promise<AuthResponse> {
  const payload = await parseJsonPayload<unknown>(response, 'authentication');

  if (typeof payload !== 'object' || payload === null) {
    throw new Error('Authentication response was invalid');
  }

  const data = payload as { user?: unknown; token?: unknown; message?: unknown };
  if (typeof data.token !== 'string') {
    throw new Error('Authentication token was missing');
  }
  if (typeof data.user !== 'object' || data.user === null) {
    throw new Error('Authentication user was missing');
  }

  return {
    user: data.user as AuthUserProfile,
    token: data.token,
  };
}

async function readErrorResponse(
  response: Response,
  fallback: string
): Promise<Error> {
  try {
    const payload = await parseJsonPayload<unknown>(response, 'authentication error');
    if (typeof payload === 'object' && payload !== null && 'message' in payload) {
      const message = (payload as { message?: unknown }).message;
      if (typeof message === 'string' && message.length > 0) {
        return new Error(message);
      }
    }
  } catch {
    try {
      const message = await response.text();
      if (message.trim().length > 0) {
        return new Error(message.trim());
      }
    } catch {
      // Keep fallback below.
    }
  }

  return new Error(fallback);
}

async function validateTokenWithServer(token: string): Promise<boolean> {
  try {
    const meResponse = await fetch('/api/auth/me', {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });

    if (meResponse.ok) {
      return true;
    }

    const validateResponse = await fetch('/api/auth/validate', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });

    return validateResponse.ok;
  } catch (error) {
    console.error('Token validation failed:', error);
    return false;
  }
}

export function createDemoUser(userId?: string): AuthUserProfile {
  const id = userId ?? `demo-${Date.now()}`;

  return {
    id,
    email: `demo-${id}@example.com`,
    name: `Demo User ${id.slice(-4)}`,
    avatar: `https://api.dicebear.com/7.x/initials/svg?seed=${id}`,
    role: 'EDITOR' as User['role'],
    permissions: ['canvas.read', 'canvas.write', 'canvas.share'],
    workspaces: ['demo-workspace'],
    lastLogin: Date.now(),
    createdAt: new Date(
      Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000
    ).toISOString(),
  };
}

export function useAuth() {
  const [authState, setAuthState] = useAtom(authStateAtom);
  const [sessionCheckInterval, setSessionCheckInterval] =
    useState<NodeJS.Timeout | null>(null);

  const updateAuthState = useCallback(
    (update: AuthState | ((current: AuthState) => AuthState)) => {
      if (typeof update === 'function') {
        setAuthState(update(authState));
        return;
      }

      setAuthState(update);
    },
    [authState, setAuthState]
  );

  const logout = useCallback(async () => {
    updateAuthState((currentState) => ({ ...currentState, isLoading: true }));

    try {
      if (authState.token) {
        await fetch('/api/auth/logout', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${authState.token}`,
            'Content-Type': 'application/json',
          },
        });
      }
    } catch (error) {
      console.error('Logout notification failed:', error);
    } finally {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_user');

      updateAuthState({
        user: null,
        isAuthenticated: false,
        isLoading: false,
        error: null,
        token: null,
      });

      if (sessionCheckInterval) {
        clearInterval(sessionCheckInterval);
        setSessionCheckInterval(null);
      }
    }
  }, [authState.token, sessionCheckInterval, updateAuthState]);

  const validateSession = useCallback(async (): Promise<boolean> => {
    if (!authState.token) {
      return false;
    }

    try {
      const isValid = await validateTokenWithServer(authState.token);
      if (!isValid) {
        await logout();
        return false;
      }

      return true;
    } catch (error) {
      console.error('Session validation failed:', error);
      return false;
    }
  }, [authState.token, logout]);

  const checkExistingSession = useCallback(async () => {
    updateAuthState((currentState) => ({
      ...currentState,
      isLoading: true,
      error: null,
    }));

    try {
      const token = localStorage.getItem('auth_token');
      const userData = localStorage.getItem('auth_user');

      if (!token || !userData) {
        updateAuthState((currentState) => ({ ...currentState, isLoading: false }));
        return;
      }

      const parsedUser = parseStoredAuthUser(userData);
      const isValid = await validateTokenWithServer(token);

      if (!isValid) {
        await logout();
        return;
      }

      updateAuthState({
        user: parsedUser,
        isAuthenticated: true,
        isLoading: false,
        error: null,
        token,
      });
    } catch (error) {
      console.error('Session check failed:', error);
      updateAuthState((currentState) => ({
        ...currentState,
        isLoading: false,
        error: 'Session validation failed',
      }));
    }
  }, [logout, updateAuthState]);

  useEffect(() => {
    void checkExistingSession();

    const interval = setInterval(() => {
      void validateSession();
    }, 5 * 60 * 1000);

    setSessionCheckInterval(interval);

    return () => {
      clearInterval(interval);
    };
  }, [checkExistingSession, validateSession]);

  const login = useCallback(
    async (credentials: LoginCredentials): Promise<AuthMutationResult> => {
      updateAuthState((currentState) => ({
        ...currentState,
        isLoading: true,
        error: null,
      }));

      try {
        const response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(credentials),
        });

        if (!response.ok) {
          throw await readErrorResponse(response, 'Login failed');
        }

        const { user, token } = await parseAuthResponse(response);
        localStorage.setItem('auth_token', token);
        localStorage.setItem('auth_user', JSON.stringify(user));

        updateAuthState({
          user,
          isAuthenticated: true,
          isLoading: false,
          error: null,
          token,
        });

        return { success: true, user, token };
      } catch (error) {
        const errorMessage = readErrorMessage(error, 'Login failed');
        updateAuthState((currentState) => ({
          ...currentState,
          isLoading: false,
          error: errorMessage,
        }));

        return { success: false, error: errorMessage };
      }
    },
    [updateAuthState]
  );

  const register = useCallback(
    async (userData: RegisterData): Promise<AuthMutationResult> => {
      updateAuthState((currentState) => ({
        ...currentState,
        isLoading: true,
        error: null,
      }));

      try {
        const response = await fetch('/api/auth/register', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(userData),
        });

        if (!response.ok) {
          throw await readErrorResponse(response, 'Registration failed');
        }

        const { user, token } = await parseAuthResponse(response);
        localStorage.setItem('auth_token', token);
        localStorage.setItem('auth_user', JSON.stringify(user));

        updateAuthState({
          user,
          isAuthenticated: true,
          isLoading: false,
          error: null,
          token,
        });

        return { success: true, user, token };
      } catch (error) {
        const errorMessage = readErrorMessage(error, 'Registration failed');
        updateAuthState((currentState) => ({
          ...currentState,
          isLoading: false,
          error: errorMessage,
        }));

        return { success: false, error: errorMessage };
      }
    },
    [updateAuthState]
  );

  const updateProfile = useCallback(
    async (updates: Partial<AuthUserProfile>) => {
      if (!authState.user || !authState.token) {
        throw new Error('User not authenticated');
      }

      updateAuthState((currentState) => ({
        ...currentState,
        isLoading: true,
        error: null,
      }));

      try {
        const response = await fetch('/api/auth/profile', {
          method: 'PATCH',
          headers: {
            Authorization: `Bearer ${authState.token}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(updates),
        });

        if (!response.ok) {
          throw await readErrorResponse(response, 'Profile update failed');
        }

        const updatedUser = await parseJsonPayload<AuthUserProfile>(
          response,
          'profile update'
        );
        localStorage.setItem('auth_user', JSON.stringify(updatedUser));

        updateAuthState((currentState) => ({
          ...currentState,
          user: updatedUser,
          isLoading: false,
        }));

        return { success: true, user: updatedUser };
      } catch (error) {
        const errorMessage = readErrorMessage(error, 'Profile update failed');
        updateAuthState((currentState) => ({
          ...currentState,
          isLoading: false,
          error: errorMessage,
        }));

        return { success: false, error: errorMessage };
      }
    },
    [authState.token, authState.user, updateAuthState]
  );

  const authUser = authState.user as AuthUserProfile | null;

  const hasPermission = useCallback(
    (permission: string): boolean => {
      if (!authUser) {
        return false;
      }
      if (readAuthRole(authUser) === 'admin') {
        return true;
      }
      return readPermissions(authUser).includes(permission);
    },
    [authUser]
  );

  const canAccessWorkspace = useCallback(
    (workspaceId: string): boolean => {
      if (!authUser) {
        return false;
      }
      if (readAuthRole(authUser) === 'admin') {
        return true;
      }
      return readWorkspaces(authUser).includes(workspaceId);
    },
    [authUser]
  );

  const getAuthHeaders = useCallback(() => {
    if (!authState.token) {
      return {};
    }

    return {
      Authorization: `Bearer ${authState.token}`,
      'Content-Type': 'application/json',
    };
  }, [authState.token]);

  const clearError = useCallback(() => {
    updateAuthState((currentState) => ({ ...currentState, error: null }));
  }, [updateAuthState]);

  return {
    ...authState,
    login,
    register,
    logout,
    updateProfile,
    validateSession,
    hasPermission,
    canAccessWorkspace,
    getAuthHeaders,
    clearError,
  };
}

export default useAuth;