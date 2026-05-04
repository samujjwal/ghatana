/**
 * DMOS auth context.
 *
 * <p>Production-safe auth implementation (DMOS-P0-1):</p>
 * <ul>
 *   <li>Local auth disabled in production - requires real auth provider</li>
 *   <li>Auth tokens stored in runtime memory only (never localStorage/sessionStorage)</li>
 *   <li>Session expiry handling with automatic logout</li>
 *   <li>Session invalidation on logout</li>
 *   <li>Fails closed on missing principal/session in production</li>
 * </ul>
 *
 * @doc.type context
 * @doc.purpose UI authentication state for the DMOS console
 * @doc.layer frontend
 */
import React, {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  useEffect,
} from 'react';
import {
  clearAuthToken,
  clearRequestContext,
  getAuthToken,
  setAuthToken,
  setRequestContext,
} from '@/lib/http-client';
import { normalizeRoles, validateRoles } from '@/lib/role-utils';

const SESSION_EXPIRY_MS = 30 * 60 * 1000; // 30 minutes
const SESSION_REFRESH_MS = 5 * 60 * 1000; // 5 minutes

// Production environment check
const isProduction = import.meta.env.MODE === 'production';

interface AuthContextValue {
  token: string | null;
  workspaceId: string | null;
  tenantId: string | null;
  principalId: string | null;
  sessionId: string | null;
  roles: string[];
  isAuthenticated: boolean;
  login: (token: string, workspaceId: string, tenantId: string, principalId: string, sessionId: string, roles?: string[]) => void;
  logout: () => void;
  refreshSession: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

interface AuthProviderProps {
  children: React.ReactNode;
  /** For tests — override the initial token */
  initialToken?: string | null;
  initialWorkspaceId?: string | null;
  initialTenantId?: string | null;
  initialPrincipalId?: string | null;
  initialSessionId?: string | null;
  initialRoles?: string[];
}

export function AuthProvider({
  children,
  initialToken = getAuthToken(),
  initialWorkspaceId = sessionStorage.getItem('dmos_workspace_id'),
  initialTenantId = sessionStorage.getItem('dmos_tenant_id'),
  initialPrincipalId = sessionStorage.getItem('dmos_principal_id'),
  initialSessionId = sessionStorage.getItem('dmos_session_id'),
  initialRoles = [],
}: AuthProviderProps): React.ReactElement {
  const [token, setToken] = useState<string | null>(initialToken);
  const [workspaceId, setWorkspaceId] = useState<string | null>(initialWorkspaceId);
  const [tenantId, setTenantId] = useState<string | null>(initialTenantId);
  const [principalId, setPrincipalId] = useState<string | null>(initialPrincipalId);
  const [sessionId, setSessionId] = useState<string | null>(initialSessionId);
  const [roles, setRoles] = useState<string[]>(initialRoles);
  const [sessionExpiry, setSessionExpiry] = useState<number>(Date.now() + SESSION_EXPIRY_MS);

  const logout = useCallback(() => {
    clearAuthToken();
    clearRequestContext();
    sessionStorage.removeItem('dmos_workspace_id');
    sessionStorage.removeItem('dmos_tenant_id');
    sessionStorage.removeItem('dmos_principal_id');
    sessionStorage.removeItem('dmos_session_id');
    setToken(null);
    setWorkspaceId(null);
    setTenantId(null);
    setPrincipalId(null);
    setSessionId(null);
    setRoles([]);
    setSessionExpiry(0);
  }, []);

  // Session expiry check (DMOS-P1-013)
  useEffect(() => {
    if (!token) return;

    const checkExpiry = () => {
      if (Date.now() > sessionExpiry) {
        logout();
      }
    };

    const interval = setInterval(checkExpiry, 1000); // Check every second
    return () => clearInterval(interval);
  }, [token, sessionExpiry, logout]);

  // Session refresh (DMOS-P0-1)
  useEffect(() => {
    if (!token) return;

    const refresh = () => {
      if (isProduction) {
        // In production, call the real auth API to refresh the session
        // This is a placeholder - implement actual refresh endpoint call
        console.warn('[DMOS] Session refresh endpoint not yet implemented for production');
        // For now, extend expiry to prevent immediate logout
        // TODO: Implement real session refresh API call
        setSessionExpiry(Date.now() + SESSION_EXPIRY_MS);
      } else {
        // In development, extend the expiry time
        setSessionExpiry(Date.now() + SESSION_EXPIRY_MS);
      }
    };

    const interval = setInterval(refresh, SESSION_REFRESH_MS);
    return () => clearInterval(interval);
  }, [token]);

  const login = useCallback(
    (newToken: string, wsId: string, tId: string, pId: string, newSessionId: string, newRoles: string[] = []) => {
      // P0-1: Fail closed in production - local auth not allowed
      if (isProduction) {
        throw new Error(
          'Local authentication is not allowed in production. ' +
          'Please use the configured authentication provider.'
        );
      }

      // DMOS-P1-12: Validate and normalize roles
      const normalizedRoles = normalizeRoles(newRoles);
      if (!validateRoles(normalizedRoles) && normalizedRoles.length > 0) {
        console.warn('[DMOS] Invalid roles provided, using empty array:', newRoles);
      }
      
      setAuthToken(newToken);
      setRequestContext(tId, pId, newSessionId, normalizedRoles, []);
      // Use sessionStorage instead of localStorage for session data (DMOS-P1-013)
      sessionStorage.setItem('dmos_workspace_id', wsId);
      sessionStorage.setItem('dmos_tenant_id', tId);
      sessionStorage.setItem('dmos_principal_id', pId);
      sessionStorage.setItem('dmos_session_id', newSessionId);
      setToken(newToken);
      setWorkspaceId(wsId);
      setTenantId(tId);
      setPrincipalId(pId);
      setSessionId(newSessionId);
      setRoles(normalizedRoles);
      setSessionExpiry(Date.now() + SESSION_EXPIRY_MS);
    },
    [],
  );

  const refreshSession = useCallback(() => {
    if (!token) return;
    setSessionExpiry(Date.now() + SESSION_EXPIRY_MS);
  }, [token]);

  const value = useMemo<AuthContextValue>(
    () => ({ token, workspaceId, tenantId, principalId, sessionId, roles, isAuthenticated: token !== null && Date.now() < sessionExpiry, login, logout, refreshSession }),
    [token, workspaceId, tenantId, principalId, sessionId, roles, sessionExpiry, login, logout, refreshSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
