/**
 * Secure Token Storage
 *
 * Provides a secure abstraction over authentication token storage.
 * Tokens are kept in memory (primary) with sessionStorage fallback.
 *
 * Security model:
 * - RECOMMENDED: Use httpOnly SameSite=Strict cookies (requires backend support).
 *   When cookies are used, this module becomes a no-op pass-through.
 * - CURRENT: Memory-first storage with sessionStorage fallback.
 *   sessionStorage is tab-scoped (cleared on tab close) and is safer than
 *   localStorage because it is not shared across tabs or persisted to disk
 *   beyond the user session.
 *
 * XSS Mitigation: In addition to using sessionStorage, the application must
 * ensure a strict Content-Security-Policy header is served by the backend to
 * prevent injected scripts from stealing tokens.
 *
 * Migration path:
 * 1. Add `Set-Cookie: auth_token=...; HttpOnly; SameSite=Strict; Secure` on the backend.
 * 2. Remove the explicit token header injection from ApiClient.
 * 3. Rely on the browser to attach cookies automatically.
 *
 * @doc.type service
 * @doc.purpose Secure authentication token management
 * @doc.layer frontend
 * @doc.pattern Repository Pattern
 */

const TOKEN_KEY = 'auth_token';
const EXPIRY_KEY = 'auth_token_expiry';
const AUTH_MODE_KEY = 'dc:auth:mode';
const REFRESH_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes before expiry

export type AuthMode = 'anonymous' | 'header-token' | 'cookie-session';

/** In-memory cache — cleared when the page is refreshed. */
let memoryToken: string | null = null;
let memoryExpiry: number | null = null;
let refreshCallback: ((token: string) => Promise<string>) | null = null;

function readAuthMode(): AuthMode {
  try {
    const storedMode = sessionStorage.getItem(AUTH_MODE_KEY);
    if (storedMode === 'header-token' || storedMode === 'cookie-session') {
      return storedMode;
    }
  } catch {
    return memoryToken ? 'header-token' : 'anonymous';
  }

  return memoryToken ? 'header-token' : 'anonymous';
}

function writeAuthMode(mode: Exclude<AuthMode, 'anonymous'> | null): void {
  try {
    if (mode === null) {
      sessionStorage.removeItem(AUTH_MODE_KEY);
      return;
    }
    sessionStorage.setItem(AUTH_MODE_KEY, mode);
  } catch {
    // Ignore storage errors and keep auth state in memory.
  }
}

/**
 * TokenStorage provides a layered, security-conscious store for auth tokens.
 */
export const TokenStorage = {
  /**
   * Persist a token. Expiry is in seconds from now.
   */
  set(token: string, expiresInSeconds?: number): void {
    memoryToken = token;
    memoryExpiry = expiresInSeconds ? Date.now() + expiresInSeconds * 1000 : null;
    writeAuthMode('header-token');

    try {
      sessionStorage.setItem(TOKEN_KEY, token);
      if (memoryExpiry) {
        sessionStorage.setItem(EXPIRY_KEY, String(memoryExpiry));
      } else {
        sessionStorage.removeItem(EXPIRY_KEY);
      }
    } catch {
      // sessionStorage may be blocked in some environments (private browsing).
      // Memory-only storage is still acceptable.
    }
  },

  /**
   * Marks the current browser session as cookie-backed.
   * The httpOnly cookie remains browser-managed and is never exposed here.
   */
  enableCookieSession(): void {
    memoryToken = null;
    memoryExpiry = null;
    try {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(EXPIRY_KEY);
    } catch {
      // Ignore storage cleanup failures.
    }
    writeAuthMode('cookie-session');
  },

  /**
   * Get the current token if valid. Returns null when expired or absent.
   */
  get(): string | null {
    if (readAuthMode() === 'cookie-session') {
      return null;
    }

    // 1. Check memory cache first (fastest path, no DOM I/O).
    if (memoryToken) {
      if (memoryExpiry && Date.now() > memoryExpiry) {
        TokenStorage.clear();
        return null;
      }
      return memoryToken;
    }

    // 2. Attempt sessionStorage rehydration (e.g., after hot-reload in dev).
    try {
      const storedToken = sessionStorage.getItem(TOKEN_KEY);
      if (!storedToken) {
        if (readAuthMode() === 'header-token') {
          writeAuthMode(null);
        }
        return null;
      }

      const storedExpiry = sessionStorage.getItem(EXPIRY_KEY);
      if (storedExpiry && Date.now() > Number(storedExpiry)) {
        TokenStorage.clear();
        return null;
      }

      // Rehydrate memory cache.
      memoryToken = storedToken;
      memoryExpiry = storedExpiry ? Number(storedExpiry) : null;
      return memoryToken;
    } catch {
      return null;
    }
  },

  /**
   * Remove the stored token from all storage layers.
   */
  clear(): void {
    memoryToken = null;
    memoryExpiry = null;
    writeAuthMode(null);
    try {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(EXPIRY_KEY);
    } catch {
      // Ignore storage errors on clear.
    }
  },

  /**
   * Check whether a non-expired token is available.
   */
  isAuthenticated(): boolean {
    return this.authMode() !== 'anonymous';
  },

  authMode(): AuthMode {
    const mode = readAuthMode();
    if (mode === 'cookie-session') {
      return mode;
    }
    return this.get() !== null ? 'header-token' : 'anonymous';
  },

  /**
   * Returns the number of milliseconds until the token expires.
   * Returns null if there is no expiry or no token.
   */
  expiresIn(): number | null {
    if (this.authMode() === 'cookie-session') {
      return null;
    }
    if (!memoryExpiry) return null;
    const remaining = memoryExpiry - Date.now();
    return remaining > 0 ? remaining : null;
  },

  /**
   * Register a callback for token refresh. The callback should return a new token.
   * This is called when the token is near expiry (within REFRESH_THRESHOLD_MS).
   */
  setRefreshCallback(callback: (token: string) => Promise<string>): void {
    refreshCallback = callback;
  },

  /**
   * Check if token needs refresh and trigger refresh if callback is registered.
   * Returns true if refresh was triggered, false otherwise.
   */
  async checkAndRefresh(): Promise<boolean> {
    const remaining = this.expiresIn();
    if (!remaining || remaining > REFRESH_THRESHOLD_MS || !refreshCallback) {
      return false;
    }

    const currentToken = this.get();
    if (!currentToken) return false;

    try {
      const newToken = await refreshCallback(currentToken);
      this.set(newToken);
      return true;
    } catch (error) {
      console.error('[TokenStorage] Token refresh failed:', error);
      this.clear();
      return false;
    }
  },

  /**
   * Returns true if the token will expire within the threshold and needs refresh.
   */
  needsRefresh(): boolean {
    if (this.authMode() === 'cookie-session') {
      return false;
    }
    const remaining = this.expiresIn();
    return remaining !== null && remaining <= REFRESH_THRESHOLD_MS;
  },
};

export default TokenStorage;
