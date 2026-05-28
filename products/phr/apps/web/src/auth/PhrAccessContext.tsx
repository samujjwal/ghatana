import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { useOptionalPhrSession } from './PhrSessionContext';

export type PhrRole = 'patient' | 'caregiver' | 'clinician' | 'admin' | 'fchv';

interface PhrAccessContextValue {
  role: PhrRole;
  setRole: (role: PhrRole) => void;
  tenantId: string;
  principalId: string;
}

const STORAGE_KEY = 'phr.currentRole';
const TENANT_ID_KEY = 'phr.tenantId';
const PRINCIPAL_ID_KEY = 'phr.principalId';

const PhrAccessContext = createContext<PhrAccessContextValue | null>(null);

function loadStoredRole(): PhrRole {
  if (typeof window === 'undefined') {
    return 'patient';
  }
  const candidate = window.localStorage.getItem(STORAGE_KEY);
  if (candidate === 'patient' || candidate === 'caregiver' || candidate === 'clinician' || candidate === 'admin' || candidate === 'fchv') {
    return candidate;
  }
  return 'patient';
}

function loadStoredTenantId(): string {
  if (typeof window === 'undefined') {
    return '';
  }
  return window.localStorage.getItem(TENANT_ID_KEY) || '';
}

function loadStoredPrincipalId(): string {
  if (typeof window === 'undefined') {
    return '';
  }
  return window.localStorage.getItem(PRINCIPAL_ID_KEY) || '';
}

export function PhrAccessProvider({ children }: { children: React.ReactNode }): React.ReactElement {
  const sessionContext = useOptionalPhrSession();
  const session = sessionContext?.session ?? null;
  const [role, setRole] = useState<PhrRole>(loadStoredRole);
  const [tenantId] = useState<string>(loadStoredTenantId);
  const [principalId] = useState<string>(loadStoredPrincipalId);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, role);
  }, [role]);

  const value = useMemo<PhrAccessContextValue>(
    () => ({
      role: session?.role ?? role,
      setRole,
      tenantId: session?.tenantId ?? tenantId,
      principalId: session?.principalId ?? principalId,
    }),
    [role, session, tenantId, principalId],
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
