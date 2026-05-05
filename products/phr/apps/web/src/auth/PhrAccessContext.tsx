import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';

export type PhrRole = 'patient' | 'caregiver' | 'clinician' | 'admin';

interface PhrAccessContextValue {
  role: PhrRole;
  setRole: (role: PhrRole) => void;
}

const STORAGE_KEY = 'phr.currentRole';

const PhrAccessContext = createContext<PhrAccessContextValue | null>(null);

function loadStoredRole(): PhrRole {
  if (typeof window === 'undefined') {
    return 'patient';
  }
  const candidate = window.localStorage.getItem(STORAGE_KEY);
  if (candidate === 'patient' || candidate === 'caregiver' || candidate === 'clinician' || candidate === 'admin') {
    return candidate;
  }
  return 'patient';
}

export function PhrAccessProvider({ children }: { children: React.ReactNode }): React.ReactElement {
  const [role, setRole] = useState<PhrRole>(loadStoredRole);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, role);
  }, [role]);

  const value = useMemo<PhrAccessContextValue>(() => ({ role, setRole }), [role]);
  return <PhrAccessContext.Provider value={value}>{children}</PhrAccessContext.Provider>;
}

export function usePhrAccess(): PhrAccessContextValue {
  const context = useContext(PhrAccessContext);
  if (!context) {
    throw new Error('usePhrAccess must be used within PhrAccessProvider');
  }
  return context;
}
