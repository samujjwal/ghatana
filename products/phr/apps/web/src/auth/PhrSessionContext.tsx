/**
 * PHR session context for the authenticated actor identity resolved from the
 * backend auth/login response.
 *
 * Stores only the opaque session reference (sessionId and expiresAt). Identity
 * is resolved server-side through Kernel-authenticated session context and fetched
 * from the /me endpoint for use in the UI. Identity fields are read-only.
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

export type PhrRole = 'patient' | 'caregiver' | 'clinician' | 'admin' | 'fchv';

interface PhrIdentity {
  principalId: string;
  tenantId: string;
  role: PhrRole;
  persona?: string;
  tier?: string;
  facilityId?: string;
  name: string;
  permissions: string[];
}

interface PhrSessionContextValue {
  session: PhrSession | null;
  identity: PhrIdentity | null;
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
  const [identity, setIdentity] = useState<PhrIdentity | null>(null);
  const [sessionValidating, setSessionValidating] = useState<boolean>(false);

  const setSession = useCallback((nextSession: PhrSession): void => {
    setSessionState(nextSession);
    try {
      window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(nextSession));
    } catch {
      // Session remains available in memory when browser storage is unavailable.
    }
  }, []);

  const clearSession = useCallback((): void => {
    setSessionState(null);
    setIdentity(null);
    try {
      window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
    } catch {
      // Clearing in-memory state above is sufficient when storage is unavailable.
    }
  }, []);

  useEffect(() => {
    const validateSession = async (): Promise<void> => {
      const stored = loadStoredSession();
      if (!stored) return;

      setSessionValidating(true);
      try {
        // Validate session and fetch identity using Kernel-authenticated session cookie
        const response = await phrFetch('/api/v1/auth/me', {
          context: {
            sessionId: stored.sessionId,
          },
        }) as {
          principalId: string;
          tenantId: string;
          role: string;
          persona?: string;
          tier?: string;
          facilityId?: string;
          name: string;
          permissions: string[];
        };

        // Update identity from server - read-only, no client override
        setIdentity({
          principalId: response.principalId,
          tenantId: response.tenantId,
          role: response.role as PhrRole,
          persona: response.persona,
          tier: response.tier,
          facilityId: response.facilityId,
          name: response.name,
          permissions: response.permissions,
        });
      } catch {
        logWarn('Session validation failed');
        clearSession();
      } finally {
        setSessionValidating(false);
      }
    };

    validateSession();
  }, [clearSession]);

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
    () => ({ session, identity, setSession, clearSession, isAuthenticated: session !== null, sessionValidating }),
    [session, identity, setSession, clearSession, sessionValidating],
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
