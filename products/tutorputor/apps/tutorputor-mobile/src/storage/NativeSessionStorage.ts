// @ts-nocheck - React Native MMKV types
import { MMKV } from 'react-native-mmkv';

/**
 * Secure storage configuration for session data.
 */
interface SecureStorageConfig {
  /** Storage ID for isolation */
  id: string;
  /** Encryption key for sensitive data */
  encryptionKey?: string;
}

/**
 * Native session storage interface.
 */
type NativeSessionStorage = {
  getString: (key: string) => string | undefined;
  set: (key: string, value: string) => void;
  delete: (key: string) => void;
};

/**
 * Storage shim interface for localStorage compatibility.
 */
interface StorageShim {
  getItem: (key: string) => string | null;
  setItem: (key: string, value: string) => void;
  removeItem: (key: string) => void;
  clear: () => void;
}

/**
 * Create secure session storage with optional encryption.
 */
function createSessionStorage(config?: SecureStorageConfig): NativeSessionStorage {
  const mmkvConfig: { id: string; encryptionKey?: string } = {
    id: config?.id || 'tutorputor-session',
  };

  // Use encryption if key is provided
  if (config?.encryptionKey) {
    mmkvConfig.encryptionKey = config.encryptionKey;
  }

  // @ts-ignore - MMKV constructor types
  return new MMKV(mmkvConfig);
}

// Initialize secure storage
// Encryption key can be provided via environment or passed directly
const encryptionKey = typeof process !== 'undefined' && process.env?.SESSION_ENCRYPTION_KEY
  ? process.env.SESSION_ENCRYPTION_KEY
  : undefined;

const sessionStorage = createSessionStorage({
  id: 'tutorputor-session',
  encryptionKey,
});

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
