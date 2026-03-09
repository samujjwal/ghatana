import { v4 as uuidv4 } from 'uuid';

const STORAGE_KEY = 'yappc.audit.entries.v1';

export interface AuditEntry {
  id: string;
  timestamp: string;
  user: string;
  action: string;
  summary: string;
  details?: unknown;
}

const readEntries = (): AuditEntry[] => {
  if (typeof window === 'undefined') {
    return [];
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [];
    }

    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }

    return parsed.filter((entry): entry is AuditEntry => typeof entry?.id === 'string');
  } catch (error) {
    console.warn('Failed to read audit entries:', error);
    return [];
  }
};

const writeEntries = (entries: AuditEntry[]) => {
  if (typeof window === 'undefined') {
    return;
  }

  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
  } catch (error) {
    console.warn('Failed to persist audit entries:', error);
  }
};

export const loadAuditEntries = (): AuditEntry[] => {
  return readEntries();
};

export const saveAuditEntry = (entry: Omit<AuditEntry, 'id'> & { id?: string }): AuditEntry => {
  const entries = readEntries();
  const persisted: AuditEntry = {
    ...entry,
    id: entry.id ?? uuidv4(),
  };

  entries.unshift(persisted);
  writeEntries(entries.slice(0, 500));
  return persisted;
};

export const clearAuditEntries = () => {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem(STORAGE_KEY);
  }
};
