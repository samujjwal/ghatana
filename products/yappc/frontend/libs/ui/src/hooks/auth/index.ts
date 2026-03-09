/**
 * Authentication Utility Hooks
 * 
 * Collection of reusable authentication hooks for common auth patterns:
 * - Session timeout detection
 * - Token refresh management
 * - Permission checking
 * - Auth state persistence
 * - Login/logout helpers
 * - Protected navigation
 * 
 * @module ui/hooks/auth
 * @doc.type hooks
 * @doc.purpose Authentication utility hooks
 * @doc.layer ui
 */

import { useEffect, useCallback, useRef, useState } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import {
  authUserAtom,
  authTokenAtom,
  authLoadingAtom,
  authErrorAtom,
  authStateAtom,
} from '@ghatana/yappc-canvas';
import { useNavigate, useLocation } from 'react-router-dom';

import type { User } from '@ghatana/yappc-types';

// ============================================================================
// Session Timeout Hook
// ============================================================================

export interface UseSessionTimeoutOptions {
  /** Timeout duration in milliseconds */
  timeout?: number;
  
  /** Warning duration before timeout in milliseconds */
  warningTime?: number;
  
  /** Callback when session times out */
  onTimeout?: () => void;
  
  /** Callback when warning time is reached */
  onWarning?: () => void;
  
  /** Whether to reset on activity */
  resetOnActivity?: boolean;
}

/**
 * Hook to detect session timeout and warn user
 * 
 * @param options - Timeout options
 * @returns Session timeout state and reset function
 * 
 * @example
 * const { isWarning, isTimedOut, resetTimeout } = useSessionTimeout({
 *   timeout: 30 * 60 * 1000, // 30 minutes
 *   warningTime: 5 * 60 * 1000, // 5 minutes before
 *   onTimeout: () => logout(),
 * });
 */
export function useSessionTimeout(options: UseSessionTimeoutOptions = {}) {
  const {
    timeout = 30 * 60 * 1000, // 30 minutes default
    warningTime = 5 * 60 * 1000, // 5 minutes warning
    onTimeout,
    onWarning,
    resetOnActivity = true,
  } = options;
  
  const [isWarning, setIsWarning] = useState(false);
  const [isTimedOut, setIsTimedOut] = useState(false);
  const timeoutIdRef = useRef<NodeJS.Timeout | null>(null);
  const warningIdRef = useRef<NodeJS.Timeout | null>(null);
  const lastActivityRef = useRef<number>(Date.now());
  
  const user = useAtomValue(authUserAtom);
  const isAuthenticated = !!user;
  
  const resetTimeout = useCallback(() => {
    // Clear existing timers
    if (timeoutIdRef.current) clearTimeout(timeoutIdRef.current);
    if (warningIdRef.current) clearTimeout(warningIdRef.current);
    
    // Reset state
    setIsWarning(false);
    setIsTimedOut(false);
    lastActivityRef.current = Date.now();
    
    // Only set timers if authenticated
    if (!isAuthenticated) return;
    
    // Set warning timer
    if (warningTime > 0 && timeout > warningTime) {
      warningIdRef.current = setTimeout(() => {
        setIsWarning(true);
        if (onWarning) onWarning();
      }, timeout - warningTime);
    }
    
    // Set timeout timer
    timeoutIdRef.current = setTimeout(() => {
      setIsTimedOut(true);
      if (onTimeout) onTimeout();
    }, timeout);
  }, [timeout, warningTime, onTimeout, onWarning, isAuthenticated]);
  
  // Activity event handler
  const handleActivity = useCallback(() => {
    if (!resetOnActivity || !isAuthenticated) return;
    
    const now = Date.now();
    const timeSinceLastActivity = now - lastActivityRef.current;
    
    // Only reset if enough time has passed (throttle)
    if (timeSinceLastActivity > 5000) {
      resetTimeout();
    }
  }, [resetOnActivity, resetTimeout, isAuthenticated]);
  
  // Setup and cleanup
  useEffect(() => {
    if (!isAuthenticated) {
      setIsWarning(false);
      setIsTimedOut(false);
      return;
    }
    
    resetTimeout();
    
    // Add activity listeners
    if (resetOnActivity) {
      window.addEventListener('mousemove', handleActivity);
      window.addEventListener('keydown', handleActivity);
      window.addEventListener('click', handleActivity);
      window.addEventListener('scroll', handleActivity);
    }
    
    return () => {
      if (timeoutIdRef.current) clearTimeout(timeoutIdRef.current);
      if (warningIdRef.current) clearTimeout(warningIdRef.current);
      
      window.removeEventListener('mousemove', handleActivity);
      window.removeEventListener('keydown', handleActivity);
      window.removeEventListener('click', handleActivity);
      window.removeEventListener('scroll', handleActivity);
    };
  }, [isAuthenticated, resetTimeout, resetOnActivity, handleActivity]);
  
  return {
    isWarning,
    isTimedOut,
    resetTimeout,
    timeRemaining: isTimedOut ? 0 : timeout - (Date.now() - lastActivityRef.current),
  };
}

// ============================================================================
// Token Refresh Hook
// ============================================================================

export interface UseTokenRefreshOptions {
  /** Refresh interval in milliseconds */
  refreshInterval?: number;
  
  /** Time before expiration to refresh (in milliseconds) */
  refreshBeforeExpiry?: number;
  
  /** Callback to refresh token */
  onRefresh?: (token: string) => Promise<string>;
  
  /** Callback on refresh error */
  onError?: (error: Error) => void;
}

/**
 * Hook to automatically refresh authentication token
 * 
 * @param options - Refresh options
 * @returns Token refresh state
 * 
 * @example
 * const { isRefreshing, refreshToken } = useTokenRefresh({
 *   refreshBeforeExpiry: 5 * 60 * 1000, // 5 minutes
 *   onRefresh: async (token) => await authApi.refreshToken(token),
 * });
 */
export function useTokenRefresh(options: UseTokenRefreshOptions = {}) {
  const {
    refreshInterval = 15 * 60 * 1000, // 15 minutes default
    refreshBeforeExpiry = 5 * 60 * 1000, // 5 minutes before expiry
    onRefresh,
    onError,
  } = options;
  
  const [token, setToken] = useAtom(authTokenAtom);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const refreshTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  
  const refreshToken = useCallback(async () => {
    if (!token || !onRefresh) return;
    
    setIsRefreshing(true);
    
    try {
      const newToken = await onRefresh(token);
      setToken(newToken);
    } catch (error) {
      if (onError) onError(error as Error);
    } finally {
      setIsRefreshing(false);
    }
  }, [token, onRefresh, onError, setToken]);
  
  // Parse JWT token to get expiration
  const getTokenExpiry = useCallback((token: string): number | null => {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp ? payload.exp * 1000 : null;
    } catch {
      return null;
    }
  }, []);
  
  // Setup refresh timer
  useEffect(() => {
    if (!token || !onRefresh) return;
    
    const expiry = getTokenExpiry(token);
    let delay = refreshInterval;
    
    // If token has expiry, calculate refresh time
    if (expiry) {
      const timeUntilExpiry = expiry - Date.now();
      delay = Math.max(timeUntilExpiry - refreshBeforeExpiry, 0);
    }
    
    // Clear existing timeout
    if (refreshTimeoutRef.current) {
      clearTimeout(refreshTimeoutRef.current);
    }
    
    // Set new timeout
    if (delay > 0) {
      refreshTimeoutRef.current = setTimeout(refreshToken, delay);
    } else {
      // Token already expired or will expire soon
      refreshToken();
    }
    
    return () => {
      if (refreshTimeoutRef.current) {
        clearTimeout(refreshTimeoutRef.current);
      }
    };
  }, [token, refreshInterval, refreshBeforeExpiry, onRefresh, getTokenExpiry, refreshToken]);
  
  return {
    isRefreshing,
    refreshToken,
    tokenExpiry: token ? getTokenExpiry(token) : null,
  };
}

// ============================================================================
// Permission Check Hook
// ============================================================================

/**
 * Hook to check user permissions
 * 
 * @param requiredPermissions - Required permissions (string or array)
 * @param requireAll - Whether all permissions are required (default: false)
 * @returns Permission check result
 * 
 * @example
 * const { hasPermission, isLoading } = usePermission('admin');
 * const { hasPermission } = usePermission(['read', 'write'], true);
 */
export function usePermission(
  requiredPermissions: string | string[],
  requireAll: boolean = false
) {
  const user = useAtomValue(authUserAtom);
  const isLoading = useAtomValue(authLoadingAtom);
  
  const userPermissions = (user as unknown)?.permissions || [];
  const permissions = Array.isArray(requiredPermissions) ? requiredPermissions : [requiredPermissions];
  
  const hasPermission = useMemo(() => {
    if (!user) return false;
    
    if (requireAll) {
      return permissions.every(p => userPermissions.includes(p));
    }
    
    return permissions.some(p => userPermissions.includes(p));
  }, [user, userPermissions, permissions, requireAll]);
  
  return {
    hasPermission,
    isLoading,
    userPermissions,
  };
}

// ============================================================================
// Role Check Hook
// ============================================================================

/**
 * Hook to check user roles
 * 
 * @param requiredRoles - Required roles (string or array)
 * @param requireAll - Whether all roles are required (default: false)
 * @returns Role check result
 * 
 * @example
 * const { hasRole, isAdmin } = useRole('admin');
 * const { hasRole } = useRole(['admin', 'moderator']);
 */
export function useRole(
  requiredRoles: string | string[],
  requireAll: boolean = false
) {
  const user = useAtomValue(authUserAtom);
  const isLoading = useAtomValue(authLoadingAtom);
  
  const userRoles = (user as unknown)?.roles || [];
  const roles = Array.isArray(requiredRoles) ? requiredRoles : [requiredRoles];
  
  const hasRole = useMemo(() => {
    if (!user) return false;
    
    if (requireAll) {
      return roles.every(r => userRoles.includes(r));
    }
    
    return roles.some(r => userRoles.includes(r));
  }, [user, userRoles, roles, requireAll]);
  
  const isAdmin = useMemo(() => userRoles.includes('admin'), [userRoles]);
  
  return {
    hasRole,
    isAdmin,
    isLoading,
    userRoles,
  };
}

// ============================================================================
// Auth Persistence Hook
// ============================================================================

export interface UseAuthPersistenceOptions {
  /** Storage key for auth data */
  storageKey?: string;
  
  /** Storage type */
  storage?: 'local' | 'session';
  
  /** Whether to persist user data */
  persistUser?: boolean;
  
  /** Whether to persist token */
  persistToken?: boolean;
}

/**
 * Hook to persist auth state to storage
 * 
 * @param options - Persistence options
 * 
 * @example
 * useAuthPersistence({
 *   storage: 'local',
 *   persistUser: true,
 *   persistToken: true,
 * });
 */
export function useAuthPersistence(options: UseAuthPersistenceOptions = {}) {
  const {
    storageKey = 'auth',
    storage = 'local',
    persistUser = true,
    persistToken = true,
  } = options;
  
  const [user, setUser] = useAtom(authUserAtom);
  const [token, setToken] = useAtom(authTokenAtom);
  
  const storageObj = storage === 'local' ? localStorage : sessionStorage;
  
  // Load from storage on mount
  useEffect(() => {
    try {
      const stored = storageObj.getItem(storageKey);
      if (stored) {
        const data = JSON.parse(stored);
        
        if (persistUser && data.user) {
          setUser(data.user);
        }
        
        if (persistToken && data.token) {
          setToken(data.token);
        }
      }
    } catch (error) {
      console.error('Failed to load auth from storage:', error);
    }
  }, [storageKey, storageObj, persistUser, persistToken, setUser, setToken]);
  
  // Save to storage on change
  useEffect(() => {
    try {
      const data: Record<string, unknown> = {};
      
      if (persistUser && user) {
        data.user = user;
      }
      
      if (persistToken && token) {
        data.token = token;
      }
      
      if (Object.keys(data).length > 0) {
        storageObj.setItem(storageKey, JSON.stringify(data));
      } else {
        storageObj.removeItem(storageKey);
      }
    } catch (error) {
      console.error('Failed to save auth to storage:', error);
    }
  }, [user, token, storageKey, storageObj, persistUser, persistToken]);
  
  const clearStorage = useCallback(() => {
    storageObj.removeItem(storageKey);
  }, [storageObj, storageKey]);
  
  return {
    clearStorage,
  };
}

// ============================================================================
// Protected Navigation Hook
// ============================================================================

export interface UseProtectedNavigationOptions {
  /** Login path for redirect */
  loginPath?: string;
  
  /** Whether to preserve return path */
  preserveReturnPath?: boolean;
}

/**
 * Hook for navigation with authentication checks
 * 
 * @param options - Navigation options
 * @returns Navigation functions with auth checks
 * 
 * @example
 * const { navigateTo, navigateToLogin } = useProtectedNavigation();
 * navigateTo('/dashboard'); // Redirects to login if not authenticated
 */
export function useProtectedNavigation(options: UseProtectedNavigationOptions = {}) {
  const {
    loginPath = '/login',
    preserveReturnPath = true,
  } = options;
  
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAtomValue(authUserAtom);
  const isAuthenticated = !!user;
  
  const navigateTo = useCallback((path: string, requireAuth: boolean = true) => {
    if (requireAuth && !isAuthenticated) {
      // Save return path
      if (preserveReturnPath) {
        sessionStorage.setItem('returnPath', path);
      }
      
      // Navigate to login with return URL
      navigate(`${loginPath}?returnUrl=${encodeURIComponent(path)}`, {
        state: { from: path },
      });
      return;
    }
    
    navigate(path);
  }, [isAuthenticated, preserveReturnPath, navigate, loginPath]);
  
  const navigateToLogin = useCallback((returnPath?: string) => {
    const path = returnPath || location.pathname;
    
    if (preserveReturnPath && path !== loginPath) {
      sessionStorage.setItem('returnPath', path);
    }
    
    navigate(`${loginPath}?returnUrl=${encodeURIComponent(path)}`, {
      state: { from: path },
    });
  }, [navigate, loginPath, location, preserveReturnPath]);
  
  const navigateToReturnPath = useCallback((defaultPath: string = '/') => {
    const returnPath = sessionStorage.getItem('returnPath');
    
    if (returnPath) {
      sessionStorage.removeItem('returnPath');
      navigate(returnPath);
    } else {
      navigate(defaultPath);
    }
  }, [navigate]);
  
  return {
    navigateTo,
    navigateToLogin,
    navigateToReturnPath,
    isAuthenticated,
  };
}

// ============================================================================
// Auth Status Hook
// ============================================================================

/**
 * Hook to get comprehensive auth status
 * 
 * @returns Complete auth status
 * 
 * @example
 * const {
 *   isAuthenticated,
 *   isLoading,
 *   user,
 *   hasRole,
 *   hasPermission,
 * } = useAuthStatus();
 */
export function useAuthStatus() {
  const user = useAtomValue(authUserAtom);
  const token = useAtomValue(authTokenAtom);
  const isLoading = useAtomValue(authLoadingAtom);
  const error = useAtomValue(authErrorAtom);
  
  const isAuthenticated = !!user && !!token;
  const userRoles = (user as unknown)?.roles || [];
  const userPermissions = (user as unknown)?.permissions || [];
  
  const hasRole = useCallback(
    (role: string | string[]) => {
      const roles = Array.isArray(role) ? role : [role];
      return roles.some(r => userRoles.includes(r));
    },
    [userRoles]
  );
  
  const hasPermission = useCallback(
    (permission: string | string[]) => {
      const permissions = Array.isArray(permission) ? permission : [permission];
      return permissions.some(p => userPermissions.includes(p));
    },
    [userPermissions]
  );
  
  const hasAllRoles = useCallback(
    (roles: string[]) => {
      return roles.every(r => userRoles.includes(r));
    },
    [userRoles]
  );
  
  const hasAllPermissions = useCallback(
    (permissions: string[]) => {
      return permissions.every(p => userPermissions.includes(p));
    },
    [userPermissions]
  );
  
  return {
    user,
    token,
    isAuthenticated,
    isLoading,
    error,
    userRoles,
    userPermissions,
    hasRole,
    hasPermission,
    hasAllRoles,
    hasAllPermissions,
    isAdmin: userRoles.includes('admin'),
  };
}

// Import useMemo for optimization
import { useMemo } from 'react';
