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
} from '@/lib/http-client';
import { isFeatureEnabled } from '@/lib/feature-flags';

interface SessionResponse {
  session?: string;
  expiresInSeconds?: number;
}

interface PlatformSessionResponse {
  session: string;
  expiresInSeconds?: number;
}

interface AuthContextValue {
  authToken: string | null;
  sessionToken: string | null;
  isAuthenticated: boolean;
  isBootstrappingSession: boolean;
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
  const [isBootstrappingSession, setIsBootstrappingSession] = useState(false);
  const attemptedSessionBootstrap = useRef<string | null>(null);

  const bootstrapSession = async (): Promise<void> => {
    const token = getAuthToken();
    if (!token) {
      return;
    }

    setIsBootstrappingSession(true);
    try {
      const issuedSessionToken = await requestSessionToken();
      setSessionTokenState(issuedSessionToken);
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      authToken: authTokenState,
      sessionToken: sessionTokenState,
      isAuthenticated: authTokenState !== null || sessionTokenState !== null,
      isBootstrappingSession,
      async loginWithToken(token: string): Promise<void> {
        const normalizedToken = token.trim();
        if (!normalizedToken) {
          throw new Error('JWT access token is required');
        }

        attemptedSessionBootstrap.current = null;
        setAuthToken(normalizedToken);
        setAuthTokenState(normalizedToken);
        const issuedSessionToken = await requestSessionToken();
        setSessionTokenState(issuedSessionToken);
      },
      async loginWithPlatform(): Promise<void> {
        await bootstrapPlatformSession();
      },
      logout(): void {
        attemptedSessionBootstrap.current = null;
        clearAuthState();
        setAuthTokenState(null);
        setSessionTokenState(null);
      },
    }),
    [authTokenState, isBootstrappingSession, sessionTokenState],
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