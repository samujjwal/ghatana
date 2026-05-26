/**
 * PHR session context — holds the authenticated actor identity resolved
 * from the backend auth/login response. This replaces the prior
 * demo-link pattern and gates dashboard access on a real session.
 *
 * @doc.type context
 * @doc.purpose Authenticated session management for PHR web app
 * @doc.layer frontend
 * @doc.pattern Context
 */
import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { PhrSession } from '../types';

interface PhrSessionContextValue {
  session: PhrSession | null;
  setSession: (session: PhrSession) => void;
  clearSession: () => void;
  isAuthenticated: boolean;
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

  const value = useMemo<PhrSessionContextValue>(
    () => ({ session, setSession, clearSession, isAuthenticated: session !== null }),
    [session, setSession, clearSession],
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
