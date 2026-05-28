/**
 * PHR session context — holds the authenticated actor identity resolved
 * from the backend auth/login response and gates dashboard access on a real session.
 *
 * @doc.type context
 * @doc.purpose Authenticated session management for PHR web app
 * @doc.layer frontend
 * @doc.pattern Context
 */
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { phrFetch } from '../api/requestApi';
import { logWarn } from '../utils/safeLogger';
import type { PhrSession } from '../types';

interface PhrSessionContextValue {
  session: PhrSession | null;
  setSession: (session: PhrSession) => void;
  clearSession: () => void;
  isAuthenticated: boolean;
  sessionValidating: boolean;
}

const SESSION_STORAGE_KEY = 'phr.session';

const PhrSessionContext = createContext<PhrSessionContextValue | null>(null);

function loadStoredSession(): PhrSession | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PhrSession;
    // Reject sessions that are already expired
    if (new Date(parsed.expiresAt) <= new Date()) {
      window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function PhrSessionProvider({ children }: { children: React.ReactNode }): React.ReactElement {
  const [session, setSessionState] = useState<PhrSession | null>(loadStoredSession);
  const [sessionValidating, setSessionValidating] = useState<boolean>(false);

  const setSession = useCallback((nextSession: PhrSession): void => {
    setSessionState(nextSession);
    try {
      window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(nextSession));
    } catch {
      // Silently ignore storage errors; session will still work in memory.
    }
  }, []);

  const clearSession = useCallback((): void => {
    setSessionState(null);
    try {
      window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
    } catch {
      // Silently ignore storage errors.
    }
  }, []);

  // Validate stored session with backend on mount
  useEffect(() => {
    const validateSession = async (): Promise<void> => {
      const stored = loadStoredSession();
      if (!stored) return;

	    setSessionValidating(true);
	    try {
	        const response = await phrFetch('/auth/me', {
	          context: {
	            principalId: stored.principalId,
	            tenantId: stored.tenantId,
	            role: stored.role,
	          },
	        }) as { principalId: string; tenantId: string; role: string; name: string; permissions: string[] };

        // If backend validation succeeds, update session with fresh data
        setSessionState({
          principalId: response.principalId,
          tenantId: response.tenantId,
	          role: response.role as 'patient' | 'caregiver' | 'fchv' | 'clinician' | 'admin',
          name: response.name,
          expiresAt: stored.expiresAt, // Keep existing expiry
        });
      } catch {
        // Backend validation failed - clear session
        logWarn('Session validation failed');
        clearSession();
      } finally {
        setSessionValidating(false);
      }
    };

    validateSession();
  }, [clearSession]);

  // Auto-expire the session if the expiry timestamp is reached during active use.
  useEffect(() => {
    if (!session) return;
    const msUntilExpiry = new Date(session.expiresAt).getTime() - Date.now();
    if (msUntilExpiry <= 0) {
      clearSession();
      return;
    }
    const timer = window.setTimeout(() => {
      clearSession();
    }, msUntilExpiry);
    return () => { window.clearTimeout(timer); };
  }, [session, clearSession]);

  const value = useMemo<PhrSessionContextValue>(
    () => ({ session, setSession, clearSession, isAuthenticated: session !== null, sessionValidating }),
    [session, setSession, clearSession, sessionValidating],
  );

  return <PhrSessionContext.Provider value={value}>{children}</PhrSessionContext.Provider>;
}

export function usePhrSession(): PhrSessionContextValue {
  const context = useContext(PhrSessionContext);
  if (!context) {
    throw new Error('usePhrSession must be used within PhrSessionProvider');
  }
  return context;
}

export function useOptionalPhrSession(): PhrSessionContextValue | null {
  return useContext(PhrSessionContext);
}
