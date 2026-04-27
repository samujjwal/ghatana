/**
 * @tutorputor/auth-client — Token storage interface
 *
 * Platform-agnostic interface for reading and writing token pairs.
 * Concrete implementations live in each surface (web = localStorage,
 * mobile = MMKV + keychain, admin = sessionStorage).
 *
 * @doc.type module
 * @doc.purpose Token storage abstraction
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type { AuthTokenPair } from "./token.js";

// ---------------------------------------------------------------------------
// Storage interface
// ---------------------------------------------------------------------------

/**
 * Platform-agnostic contract for persisting auth token pairs.
 * All surfaces (web, admin, mobile) must satisfy this interface.
 */
export interface AuthTokenStorage {
  /**
   * Persist an auth token pair.
   * Must be safe to call multiple times (idempotent writes).
   */
  store(pair: AuthTokenPair): Promise<void>;

  /**
   * Retrieve the current token pair, or null if not stored / cleared.
   */
  retrieve(): Promise<AuthTokenPair | null>;

  /**
   * Remove all stored auth tokens (on logout / session invalidation).
   */
  clear(): Promise<void>;
}

// ---------------------------------------------------------------------------
// LocalStorage implementation (web / admin)
// ---------------------------------------------------------------------------

const ACCESS_TOKEN_KEY = "tutorputor:access_token";
const REFRESH_TOKEN_KEY = "tutorputor:refresh_token";

/**
 * `AuthTokenStorage` backed by `window.localStorage`.
 * Suitable for web and admin SPA surfaces.
 *
 * Fails gracefully (returns null) when localStorage is unavailable
 * (e.g., private browsing restrictions, SSR environments).
 */
export class LocalStorageAuthTokenStorage implements AuthTokenStorage {
  async store(pair: AuthTokenPair): Promise<void> {
    try {
      window.localStorage.setItem(ACCESS_TOKEN_KEY, pair.accessToken);
      window.localStorage.setItem(REFRESH_TOKEN_KEY, pair.refreshToken);
    } catch {
      // Storage quota or access errors must not crash the auth flow
    }
  }

  async retrieve(): Promise<AuthTokenPair | null> {
    try {
      const accessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY);
      const refreshToken = window.localStorage.getItem(REFRESH_TOKEN_KEY);
      if (!accessToken || !refreshToken) return null;
      return { accessToken, refreshToken };
    } catch {
      return null;
    }
  }

  async clear(): Promise<void> {
    try {
      window.localStorage.removeItem(ACCESS_TOKEN_KEY);
      window.localStorage.removeItem(REFRESH_TOKEN_KEY);
    } catch {
      // Best-effort clear
    }
  }
}

// ---------------------------------------------------------------------------
// In-memory implementation (testing / SSR)
// ---------------------------------------------------------------------------

/**
 * `AuthTokenStorage` backed by an in-memory map.
 * Use in tests or SSR environments where DOM storage is unavailable.
 */
export class InMemoryAuthTokenStorage implements AuthTokenStorage {
  private tokens: AuthTokenPair | null = null;

  async store(pair: AuthTokenPair): Promise<void> {
    this.tokens = pair;
  }

  async retrieve(): Promise<AuthTokenPair | null> {
    return this.tokens;
  }

  async clear(): Promise<void> {
    this.tokens = null;
  }
}
