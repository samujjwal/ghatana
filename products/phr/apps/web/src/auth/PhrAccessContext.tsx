import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';

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
    return 'demo-tenant';
  }
  return window.localStorage.getItem(TENANT_ID_KEY) || 'demo-tenant';
}

function loadStoredPrincipalId(): string {
  if (typeof window === 'undefined') {
    return 'demo-user';
  }
  return window.localStorage.getItem(PRINCIPAL_ID_KEY) || 'demo-user';
}

export function PhrAccessProvider({ children }: { children: React.ReactNode }): React.ReactElement {
  const [role, setRole] = useState<PhrRole>(loadStoredRole);
  const [tenantId] = useState<string>(loadStoredTenantId);
  const [principalId] = useState<string>(loadStoredPrincipalId);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, role);
  }, [role]);

  const value = useMemo<PhrAccessContextValue>(() => ({ role, setRole, tenantId, principalId }), [role, tenantId, principalId]);
  return <PhrAccessContext.Provider value={value}>{children}</PhrAccessContext.Provider>;
}

export function usePhrAccess(): PhrAccessContextValue {
  const context = useContext(PhrAccessContext);
  if (!context) {
    throw new Error('usePhrAccess must be used within PhrAccessProvider');
  }
  return context;
}
