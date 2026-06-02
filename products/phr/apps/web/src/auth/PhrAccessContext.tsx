import React, { createContext, useContext, useMemo } from 'react';
import type { SessionContext } from '../api/requestApi';
import { useOptionalPhrSession } from './PhrSessionContext';

export type PhrRole = 'patient' | 'caregiver' | 'clinician' | 'admin' | 'fchv';

interface PhrAccessContextValue {
  role: PhrRole;
  tenantId: string;
  principalId: string;
  persona?: string;
  tier?: string;
  facilityId?: string;
  correlationId?: string;
}

const PhrAccessContext = createContext<PhrAccessContextValue | null>(null);

export function PhrAccessProvider({ children }: { children: React.ReactNode }): React.ReactElement {
  const sessionContext = useOptionalPhrSession();
  const identity = sessionContext?.identity ?? null;

  // Identity is read-only from Kernel-authenticated session via /me endpoint
  const value = useMemo<PhrAccessContextValue>(
    () => ({
      role: identity?.role ?? 'patient',
      tenantId: identity?.tenantId ?? '',
      principalId: identity?.principalId ?? '',
      persona: identity?.persona,
      tier: identity?.tier,
      facilityId: identity?.facilityId,
      correlationId: undefined, // Generated per-request
    }),
    [identity],
  );
  return <PhrAccessContext.Provider value={value}>{children}</PhrAccessContext.Provider>;
}

export function usePhrAccess(): PhrAccessContextValue {
  const context = useContext(PhrAccessContext);
  if (!context) {
    throw new Error('usePhrAccess must be used within PhrAccessProvider');
  }
  return context;
}

export function usePhrRequestContext(): SessionContext {
  const { tenantId, principalId, role, persona, tier, facilityId, correlationId } = usePhrAccess();
  return useMemo(() => ({
    tenantId,
    principalId,
    role,
    ...(persona !== undefined && { persona }),
    ...(tier !== undefined && { tier }),
    ...(facilityId !== undefined && { facilityId }),
    ...(correlationId !== undefined && { correlationId }),
  }), [tenantId, principalId, role, persona, tier, facilityId, correlationId]);
}
