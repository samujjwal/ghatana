/**
 * DMOS auth context.
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
} from 'react';
import {
  clearAuthToken,
  clearRequestContext,
  getAuthToken,
  setAuthToken,
  setRequestContext,
} from '@/lib/http-client';

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
  initialWorkspaceId = localStorage.getItem('dmos_workspace_id'),
  initialTenantId = localStorage.getItem('dmos_tenant_id'),
  initialPrincipalId = localStorage.getItem('dmos_principal_id'),
  initialSessionId = localStorage.getItem('dmos_session_id'),
  initialRoles = [],
}: AuthProviderProps): React.ReactElement {
  const [token, setToken] = useState<string | null>(initialToken);
  const [workspaceId, setWorkspaceId] = useState<string | null>(initialWorkspaceId);
  const [tenantId, setTenantId] = useState<string | null>(initialTenantId);
  const [principalId, setPrincipalId] = useState<string | null>(initialPrincipalId);
  const [sessionId, setSessionId] = useState<string | null>(initialSessionId);
  const [roles, setRoles] = useState<string[]>(initialRoles);

  const login = useCallback(
    (newToken: string, wsId: string, tId: string, pId: string, newSessionId: string, newRoles: string[] = []) => {
      setAuthToken(newToken);
      setRequestContext(tId, pId, newSessionId, newRoles, []);
      localStorage.setItem('dmos_workspace_id', wsId);
      localStorage.setItem('dmos_tenant_id', tId);
      localStorage.setItem('dmos_principal_id', pId);
      localStorage.setItem('dmos_session_id', newSessionId);
      setToken(newToken);
      setWorkspaceId(wsId);
      setTenantId(tId);
      setPrincipalId(pId);
      setSessionId(newSessionId);
      setRoles(newRoles);
    },
    [],
  );

  const logout = useCallback(() => {
    clearAuthToken();
    clearRequestContext();
    localStorage.removeItem('dmos_workspace_id');
    localStorage.removeItem('dmos_tenant_id');
    localStorage.removeItem('dmos_principal_id');
    localStorage.removeItem('dmos_session_id');
    setToken(null);
    setWorkspaceId(null);
    setTenantId(null);
    setPrincipalId(null);
    setSessionId(null);
    setRoles([]);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ token, workspaceId, tenantId, principalId, sessionId, roles, isAuthenticated: token !== null, login, logout }),
    [token, workspaceId, tenantId, principalId, sessionId, roles, login, logout],
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
