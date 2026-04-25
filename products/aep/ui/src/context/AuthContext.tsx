/**
 * AEP auth context.
 *
 * Stores the bearer token used by the UI and opportunistically exchanges it for
 * an AEP session token so repeated API calls can reuse the lightweight session
 * header when the backend supports it.
 *
 * @doc.type context
 * @doc.purpose UI authentication state for the AEP console
 * @doc.layer frontend
 */
import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  apiClient,
  clearAuthState,
  getAuthToken,
  getSessionToken,
  setAuthToken,
  setSessionToken,
  getSessionExpiry,
  setSessionExpiry,
} from '@/lib/http-client';
import { isFeatureEnabled } from '@/lib/feature-flags';
import { toast } from 'sonner';

interface SessionResponse {
  session?: string;
  expiresInSeconds?: number;
}

interface PlatformSessionResponse {
  session: string;
  expiresInSeconds?: number;
}

/** Known AEP user roles. */
export type UserRole = 'admin' | 'operator' | 'viewer' | 'auditor';

interface AuthContextValue {
  authToken: string | null;
  sessionToken: string | null;
  isAuthenticated: boolean;
  isBootstrappingSession: boolean;
  roles: UserRole[];
  hasRole: (role: UserRole) => boolean;
  hasAnyRole: (roles: UserRole[]) => boolean;
  loginWithToken: (token: string) => Promise<void>;
  loginWithPlatform: () => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

async function requestSessionToken(): Promise<string | null> {
  try {
    const response = await apiClient.post<SessionResponse>('/api/v1/session');
    const tokenFromHeader = response.headers.get('X-AEP-Session');
    const token = tokenFromHeader ?? response.data.session ?? null;
    if (token) {
      setSessionToken(token);
    }
    return token;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [authTokenState, setAuthTokenState] = useState<string | null>(() => getAuthToken());
  const [sessionTokenState, setSessionTokenState] = useState<string | null>(() => getSessionToken());
  const [sessionExpiresAt, setSessionExpiresAt] = useState<number | null>(() => getSessionExpiry());
  const [isBootstrappingSession, setIsBootstrappingSession] = useState(false);
  const [roles, setRoles] = useState<UserRole[]>([]);
  const attemptedSessionBootstrap = useRef<string | null>(null);
  const warnedRef = useRef(false);

  const bootstrapSession = async (): Promise<void> => {
    const token = getAuthToken();
    if (!token) {
      return;
    }

    setIsBootstrappingSession(true);
    try {
      const response = await apiClient.post<SessionResponse>('/api/v1/session');
      const tokenFromHeader = response.headers.get('X-AEP-Session');
      const issuedSessionToken = tokenFromHeader ?? response.data.session ?? null;
      if (issuedSessionToken) {
        setSessionToken(issuedSessionToken);
        setSessionTokenState(issuedSessionToken);
        if (response.data.expiresInSeconds) {
          const expiresAt = Date.now() + response.data.expiresInSeconds * 1000;
          setSessionExpiry(expiresAt);
          setSessionExpiresAt(expiresAt);
          warnedRef.current = false;
        }
      }
    } finally {
      setIsBootstrappingSession(false);
    }
  };

  useEffect(() => {
    if (!authTokenState || sessionTokenState || attemptedSessionBootstrap.current === authTokenState) {
      return;
    }

    attemptedSessionBootstrap.current = authTokenState;
    void bootstrapSession();
  }, [authTokenState, sessionTokenState]);

  // ── Session expiry guard ───────────────────────────────────────────
  useEffect(() => {
    if (!sessionExpiresAt) return;

    const intervalId = window.setInterval(() => {
      const remainingMs = sessionExpiresAt - Date.now();
      if (remainingMs <= 0) {
        clearInterval(intervalId);
        clearAuthState();
        setAuthTokenState(null);
        setSessionTokenState(null);
        setSessionExpiresAt(null);
        warnedRef.current = false;
        toast.error('Your session has expired. Please sign in again.');
        window.location.reload();
        return;
      }
      if (remainingMs <= 5 * 60 * 1000 && !warnedRef.current) {
        warnedRef.current = true;
        toast.warning('Your session expires in less than 5 minutes. Save your work and re-authenticate soon.');
      }
    }, 30_000);

    return () => clearInterval(intervalId);
  }, [sessionExpiresAt]);

  const bootstrapPlatformSession = async (): Promise<void> => {
    if (!isFeatureEnabled('LEGACY_JWT_PASTE')) {
      try {
        const response = await apiClient.get<PlatformSessionResponse>('/api/v1/auth/platform-session');
        const platformSession = response.data.session;
        if (platformSession) {
          setSessionToken(platformSession);
          setSessionTokenState(platformSession);
        }
      } catch {
        // Platform session not available — rely on explicit login
      }
    }
  };

  useEffect(() => {
    void bootstrapPlatformSession();
  }, []);

  const hasRole = useCallback(
    (role: UserRole) => roles.includes(role),
    [roles],
  );

  const hasAnyRole = useCallback(
    (targetRoles: UserRole[]) => targetRoles.some((r) => roles.includes(r)),
    [roles],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      authToken: authTokenState,
      sessionToken: sessionTokenState,
      isAuthenticated: authTokenState !== null || sessionTokenState !== null,
      isBootstrappingSession,
      roles,
      hasRole,
      hasAnyRole,
      async loginWithToken(token: string): Promise<void> {
        const normalizedToken = token.trim();
        if (!normalizedToken) {
          throw new Error('JWT access token is required');
        }

        attemptedSessionBootstrap.current = null;
        setAuthToken(normalizedToken);
        setAuthTokenState(normalizedToken);
        await bootstrapSession();
      },
      async loginWithPlatform(): Promise<void> {
        await bootstrapPlatformSession();
      },
      logout(): void {
        attemptedSessionBootstrap.current = null;
        warnedRef.current = false;
        clearAuthState();
        setAuthTokenState(null);
        setSessionTokenState(null);
        setSessionExpiresAt(null);
        setRoles([]);
      },
    }),
    [authTokenState, hasAnyRole, hasRole, isBootstrappingSession, roles, sessionTokenState],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}