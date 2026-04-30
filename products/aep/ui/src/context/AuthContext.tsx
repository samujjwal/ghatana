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

interface RolesResponse {
  roles: string[];
  sub?: string;
  retrievedAt: string;
}

/** Known AEP user roles. */
export type UserRole = 'admin' | 'operator' | 'viewer' | 'auditor';

/**
 * F-007: Decodes the JWT payload to check whether the token's `exp` claim is
 * still in the future. Does NOT verify the signature — that is the backend's
 * responsibility. The check is purely a UI-side freshness guard so stale tokens
 * do not keep `isAuthenticated` truthy after they have expired.
 */
function isJwtTokenFresh(token: string): boolean {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return false;
    const payloadJson = atob(parts[1]!.replace(/-/g, '+').replace(/_/g, '/'));
    const payload = JSON.parse(payloadJson) as Record<string, unknown>;
    const exp = payload['exp'];
    if (typeof exp !== 'number') return true; // no exp claim → treat as valid
    return exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

interface AuthContextValue {
  authToken: string | null;
  sessionToken: string | null;
  isAuthenticated: boolean;
  isBootstrappingSession: boolean;
  isVerifyingAuth: boolean;
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

function normalizeRoles(rawRoles: string[]): UserRole[] {
  return rawRoles
    .map((r) => r.toLowerCase() as UserRole)
    .filter((r): r is UserRole =>
      r === 'admin' || r === 'operator' || r === 'viewer' || r === 'auditor',
    );
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [authTokenState, setAuthTokenState] = useState<string | null>(() => getAuthToken());
  const [sessionTokenState, setSessionTokenState] = useState<string | null>(() => getSessionToken());
  const [sessionExpiresAt, setSessionExpiresAt] = useState<number | null>(() => getSessionExpiry());
  const [isBootstrappingSession, setIsBootstrappingSession] = useState(false);
  const [isVerifyingAuth, setIsVerifyingAuth] = useState<boolean>(() => getAuthToken() !== null);
  const [roles, setRoles] = useState<UserRole[]>([]);
  const attemptedSessionBootstrap = useRef<string | null>(null);
  const verifiedAuthToken = useRef<string | null>(null);
  const warnedRef = useRef(false);

  const clearClientAuthState = useCallback((): void => {
    attemptedSessionBootstrap.current = null;
    verifiedAuthToken.current = null;
    warnedRef.current = false;
    clearAuthState();
    setAuthTokenState(null);
    setSessionTokenState(null);
    setSessionExpiresAt(null);
    setRoles([]);
    setIsVerifyingAuth(false);
  }, []);

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

  // ── Session expiry guard ───────────────────────────────────────────
  useEffect(() => {
    if (!sessionExpiresAt) return;

    const intervalId = window.setInterval(() => {
      const remainingMs = sessionExpiresAt - Date.now();
      if (remainingMs <= 0) {
        clearInterval(intervalId);
        clearClientAuthState();
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
  }, [clearClientAuthState, sessionExpiresAt]);

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

  const fetchVerifiedRoles = useCallback(async (): Promise<UserRole[]> => {
    const response = await apiClient.get<RolesResponse>('/api/v1/auth/roles');
    return normalizeRoles(response.data.roles ?? []);
  }, []);

  useEffect(() => {
    if (!authTokenState) {
      setRoles([]);
      setIsVerifyingAuth(false);
      return;
    }

    if (verifiedAuthToken.current === authTokenState) {
      setIsVerifyingAuth(false);
      return;
    }

    if (!isJwtTokenFresh(authTokenState)) {
      clearClientAuthState();
      return;
    }

    let cancelled = false;
    setIsVerifyingAuth(true);

    void (async () => {
      try {
        const verifiedRoles = await fetchVerifiedRoles();
        if (cancelled) {
          return;
        }
        setRoles(verifiedRoles);
        verifiedAuthToken.current = authTokenState;
        setIsVerifyingAuth(false);

        if (!sessionTokenState && attemptedSessionBootstrap.current !== authTokenState) {
          attemptedSessionBootstrap.current = authTokenState;
          try {
            await bootstrapSession();
          } catch {
            // Sessions are continuation tokens only; verified JWT auth remains usable without one.
          }
        }
      } catch {
        if (cancelled) {
          return;
        }
        clearClientAuthState();
        toast.error('Your authentication token could not be verified. Please sign in again.');
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [authTokenState, clearClientAuthState, fetchVerifiedRoles, sessionTokenState]);

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
      // F-007: isAuthenticated becomes true only after the backend has accepted the JWT.
      isAuthenticated: !isVerifyingAuth && authTokenState !== null && isJwtTokenFresh(authTokenState),
      isBootstrappingSession,
      isVerifyingAuth,
      roles,
      hasRole,
      hasAnyRole,
      async loginWithToken(token: string): Promise<void> {
        const normalizedToken = token.trim();
        if (!normalizedToken) {
          throw new Error('JWT access token is required');
        }

        attemptedSessionBootstrap.current = null;
        warnedRef.current = false;
        setIsVerifyingAuth(true);
        setAuthToken(normalizedToken);
        setAuthTokenState(normalizedToken);
        try {
          const verifiedRoles = await fetchVerifiedRoles();
          setRoles(verifiedRoles);
          verifiedAuthToken.current = normalizedToken;
          setIsVerifyingAuth(false);
          try {
            await bootstrapSession();
          } catch {
            // Session bootstrap is optional once the JWT has been verified.
          }
        } catch {
          clearClientAuthState();
          throw new Error('Unable to verify JWT access token');
        }
      },
      async loginWithPlatform(): Promise<void> {
        await bootstrapPlatformSession();
        if (getAuthToken()) {
          try {
            const verifiedRoles = await fetchVerifiedRoles();
            setRoles(verifiedRoles);
            verifiedAuthToken.current = getAuthToken();
          } catch {
            clearClientAuthState();
          }
        }
      },
      logout(): void {
        clearClientAuthState();
      },
    }),
    [authTokenState, clearClientAuthState, fetchVerifiedRoles, hasAnyRole, hasRole, isBootstrappingSession, isVerifyingAuth, roles, sessionTokenState],
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
