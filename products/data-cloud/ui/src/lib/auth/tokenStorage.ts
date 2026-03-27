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

/** In-memory cache — cleared when the page is refreshed. */
let memoryToken: string | null = null;
let memoryExpiry: number | null = null;

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
   * Get the current token if valid. Returns null when expired or absent.
   */
  get(): string | null {
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
      if (!storedToken) return null;

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
    return TokenStorage.get() !== null;
  },

  /**
   * Returns the number of milliseconds until the token expires.
   * Returns null if there is no expiry or no token.
   */
  expiresIn(): number | null {
    if (!memoryExpiry) return null;
    const remaining = memoryExpiry - Date.now();
    return remaining > 0 ? remaining : null;
  },
};

export default TokenStorage;
