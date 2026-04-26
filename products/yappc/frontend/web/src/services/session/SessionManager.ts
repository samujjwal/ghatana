/**
 * Session Manager
 *
 * Centralized session storage and token access.
 * All auth/session/token reads and writes must go through this module.
 * Direct `localStorage.getItem('auth_token')` and similar patterns are banned.
 *
 * @doc.type service
 * @doc.purpose Centralized authentication session storage
 * @doc.layer product
 * @doc.pattern Service
 */

import { logger } from '../../utils/Logger';

const AUTH_SESSION_KEY = 'auth-session';
const AUTH_TOKEN_KEY = 'auth_token';

export interface StoredSession {
  token?: string;
  refreshToken?: string;
  expiresAt?: string;
}

function canUseStorage(): boolean {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';
}

/**
 * Read the raw stored session object from storage.
 */
export function readStoredSession(): StoredSession | null {
  if (!canUseStorage()) {
    return null;
  }
  try {
    const raw = window.localStorage.getItem(AUTH_SESSION_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as unknown;
    if (typeof parsed !== 'object' || parsed === null) {
      return null;
    }
    return parsed as StoredSession;
  } catch {
    return null;
  }
}

/**
 * Persist a session object to storage.
 */
export function persistStoredSession(session: StoredSession): void {
  if (!canUseStorage()) {
    return;
  }
  window.localStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(session));
}

/**
 * Remove the session from storage.
 */
export function clearStoredSession(): void {
  if (!canUseStorage()) {
    return;
  }
  window.localStorage.removeItem(AUTH_SESSION_KEY);
}

/**
 * Retrieve the access token from the preferred secure source.
 * Priority: httpOnly cookie → localStorage auth-session → legacy localStorage auth_token.
 */
export function getAccessToken(): string | null {
  // Try cookie first (httpOnly, more secure)
  const cookieToken = getAccessTokenFromCookie();
  if (cookieToken) {
    return cookieToken;
  }

  // Fall back to structured session in localStorage
  const session = readStoredSession();
  if (session?.token) {
    return session.token;
  }

  // Legacy fallback for backward compatibility
  if (canUseStorage()) {
    const legacyToken = window.localStorage.getItem(AUTH_TOKEN_KEY);
    if (legacyToken) {
      logger.warn('Legacy auth_token used; migrate to auth-session', 'session');
      return legacyToken;
    }
  }

  return null;
}

/**
 * Retrieve the refresh token from storage.
 */
export function getRefreshToken(): string | null {
  const session = readStoredSession();
  return session?.refreshToken ?? null;
}

function getAccessTokenFromCookie(): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const match = document.cookie.match(/(?:^|;\s*)access_token=([^;]+)/);
  return match?.[1] ?? null;
}
