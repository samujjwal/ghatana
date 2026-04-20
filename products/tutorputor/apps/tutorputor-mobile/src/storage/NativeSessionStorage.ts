type NativeSessionStorage = {
  getString: (key: string) => string | undefined;
  set: (key: string, value: string) => void;
  delete: (key: string) => void;
};

interface StorageShim {
  getItem: (key: string) => string | null;
  setItem: (key: string, value: string) => void;
  removeItem: (key: string) => void;
  clear: () => void;
}

function createSessionStorage(): NativeSessionStorage {
  const { MMKV } = require('react-native-mmkv') as {
    MMKV: new (config: { id: string }) => NativeSessionStorage;
  };

  return new MMKV({ id: 'tutorputor-session' });
}

const sessionStorage = createSessionStorage();
const DEFAULT_TENANT_ID = 'default';

export interface SessionSnapshot {
  accessToken: string | null;
  refreshToken: string | null;
  tenantId: string | null;
}

export interface SessionRequestContext {
  accessToken: string | null;
  tenantId: string;
}

export function getSessionSnapshot(): SessionSnapshot {
  return {
    accessToken: sessionStorage.getString('auth_token') ?? null,
    refreshToken: sessionStorage.getString('refresh_token') ?? null,
    tenantId: sessionStorage.getString('tenant_id') ?? null,
  };
}

export function getSessionRequestContext(): SessionRequestContext {
  const snapshot = getSessionSnapshot();

  return {
    accessToken: snapshot.accessToken,
    tenantId: snapshot.tenantId ?? DEFAULT_TENANT_ID,
  };
}

export function createSessionHeaders(
  extraHeaders: Record<string, string> = {},
): Record<string, string> {
  const session = getSessionRequestContext();

  return {
    ...(session.accessToken
      ? { Authorization: `Bearer ${session.accessToken}` }
      : {}),
    'X-Tenant-ID': session.tenantId,
    ...extraHeaders,
  };
}

export function setSessionValue(key: string, value: string): void {
  sessionStorage.set(key, value);
}

export function getSessionValue(key: string): string | null {
  return sessionStorage.getString(key) ?? null;
}

export function removeSessionValue(key: string): void {
  sessionStorage.delete(key);
}

export function clearSession(): void {
  sessionStorage.delete('auth_token');
  sessionStorage.delete('refresh_token');
  sessionStorage.delete('tenant_id');
}

export function installNativeSessionStorageShim(): void {
  const globalScope = globalThis as typeof globalThis & {
    localStorage?: StorageShim;
  };

  if (typeof globalScope.localStorage !== 'undefined') {
    return;
  }

  Object.defineProperty(globalScope, 'localStorage', {
    configurable: true,
    value: {
      getItem: (key: string): string | null => getSessionValue(key),
      setItem: (key: string, value: string): void => {
        setSessionValue(key, value);
      },
      removeItem: (key: string): void => {
        removeSessionValue(key);
      },
      clear: (): void => {
        clearSession();
      },
    },
  });
}
