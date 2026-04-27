/**
 * TutorPutor Mobile - Native Session Storage
 *
 * Stores session tokens in MMKV encrypted with a per-device key retrieved
 * from the system keychain. Call `initSessionStorage()` once on app startup
 * (after the keychain key has been resolved) before using any session helpers.
 *
 * @doc.type module
 * @doc.purpose Secure session token storage backed by encrypted MMKV
 * @doc.layer product
 * @doc.pattern Adapter
 */

import { createMMKV } from 'react-native-mmkv';

// ---------------------------------------------------------------------------
// Internal state — module-level singleton, initialised by initSessionStorage
// ---------------------------------------------------------------------------

type MMKVInstance = {
  getString: (key: string) => string | undefined;
  set: (key: string, value: string) => void;
  remove: (key: string) => boolean;
};

let sessionStorage: MMKVInstance | null = null;

function requireStorage(): MMKVInstance {
  if (!sessionStorage) {
    throw new Error(
      '[NativeSessionStorage] Storage not initialised. ' +
        'Call initSessionStorage(encryptionKey) on app startup before reading/writing session data.',
    );
  }
  return sessionStorage;
}

/**
 * Initialise the encrypted MMKV instance.
 * Must be called once during app startup after the keychain-derived encryption
 * key has been resolved (see `SecureKeyManager.getMmkvEncryptionKey()`).
 */
export function initSessionStorage(encryptionKey: string): void {
  sessionStorage = createMMKV({
    id: 'tutorputor-session',
    encryptionKey,
  });
}

/**
 * Storage shim interface for localStorage compatibility.
 */
export interface StorageShim {
  getItem: (key: string) => string | null;
  setItem: (key: string, value: string) => void;
  removeItem: (key: string) => void;
  clear: () => void;
}

export interface SessionSnapshot {
  accessToken: string | null;
  refreshToken: string | null;
  tenantId: string | null;
}

export interface SessionRequestContext {
  accessToken: string | null;
  tenantId: string | null;
}

export function getSessionSnapshot(): SessionSnapshot {
  const store = requireStorage();
  return {
    accessToken: store.getString('auth_token') ?? null,
    refreshToken: store.getString('refresh_token') ?? null,
    tenantId: store.getString('tenant_id') ?? null,
  };
}

export function getSessionRequestContext(): SessionRequestContext {
  const snapshot = getSessionSnapshot();

  return {
    accessToken: snapshot.accessToken,
    tenantId: snapshot.tenantId,
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
    ...(session.tenantId ? { 'X-Tenant-ID': session.tenantId } : {}),
    ...extraHeaders,
  };
}

export function setSessionValue(key: string, value: string): void {
  requireStorage().set(key, value);
}

export function getSessionValue(key: string): string | null {
  return requireStorage().getString(key) ?? null;
}

export function removeSessionValue(key: string): void {
  requireStorage().remove(key);
}

export function clearSession(): void {
  const store = requireStorage();
  store.remove('auth_token');
  store.remove('refresh_token');
  store.remove('tenant_id');
}

/**
 * Securely store auth token with encryption if available.
 */
export function setSecureToken(tokenType: 'access' | 'refresh', token: string): void {
  const key = tokenType === 'access' ? 'auth_token' : 'refresh_token';
  setSessionValue(key, token);
}

/**
 * Securely retrieve auth token.
 */
export function getSecureToken(tokenType: 'access' | 'refresh'): string | null {
  const key = tokenType === 'access' ? 'auth_token' : 'refresh_token';
  return getSessionValue(key);
}

/**
 * Securely remove auth token.
 */
export function removeSecureToken(tokenType: 'access' | 'refresh'): void {
  const key = tokenType === 'access' ? 'auth_token' : 'refresh_token';
  removeSessionValue(key);
}

/**
 * Check if session is valid (has access token).
 */
export function hasValidSession(): boolean {
  return getSecureToken('access') !== null;
}

/**
 * Install native session storage shim to replace localStorage.
 * This should be called early in app initialization.
 */
export function installNativeSessionStorageShim(): void {
  const globalScope = globalThis as typeof globalThis & {
    localStorage?: StorageShim;
  };

  if (typeof globalScope.localStorage !== 'undefined') {
    console.warn('[NativeSessionStorage] localStorage already exists, skipping shim installation');
    return;
  }

  Object.defineProperty(globalScope, 'localStorage', {
    configurable: true,
    enumerable: true,
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

  console.info('[NativeSessionStorage] Secure localStorage shim installed');
}

/**
 * Initialize secure storage for mobile app.
 * Call this in app entry point (e.g., index.tsx or App.tsx).
 */
export function initializeSecureStorage(): void {
  installNativeSessionStorageShim();
  console.info('[NativeSessionStorage] Secure storage initialized');
}
