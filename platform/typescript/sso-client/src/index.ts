/**
 * @ghatana/sso-client — Cross-product SSO client for the Ghatana platform.
 *
 * ## Usage
 *
 * ```ts
 * import { SsoClient } from '@ghatana/sso-client';
 *
 * const sso = new SsoClient({
 *   authServiceBaseUrl: 'https://auth.ghatana.io',
 *   authGatewayBaseUrl: 'https://gateway.ghatana.io',
 *   productId:          'tutorputor',
 * });
 *
 * // Call once on app mount (reads platform_token from URL if present)
 * await sso.init();
 *
 * if (sso.isAuthenticated()) {
 *   const user = sso.getUser();
 *   console.log(`Hello ${user.email}`);
 * } else {
 *   sso.login(); // redirects to OIDC login
 * }
 * ```
 *
 * @module
 */

// ─── Types ───────────────────────────────────────────────────────────────────

/** Decoded claims present in every Ghatana platform JWT. */
export interface PlatformTokenClaims {
  /** Subject — the user's unique identifier. */
  sub: string;
  /** User's primary email address. */
  email: string;
  /** Assigned roles (e.g. ["ROLE_USER", "ROLE_ADMIN"]). */
  roles: string[];
  /** Tenant the user belongs to. */
  tenantId?: string;
  /** Must be "PLATFORM" for cross-product tokens. */
  tokenType: string;
  /** Token issuer (auth-service or auth-gateway). */
  iss: string;
  /** Expiry as Unix epoch seconds. */
  exp: number;
  /** Issued-at as Unix epoch seconds. */
  iat: number;
  /** Session identifier (links to the OIDC session in auth-service). */
  sessionId?: string;
}

/** Simplified user object exposed by the SSO client. */
export interface SsoUser {
  userId: string;
  email: string;
  roles: string[];
  tenantId: string | undefined;
  sessionId: string | undefined;
}

/** Configuration for {@link SsoClient}. */
export interface SsoClientConfig {
  /**
   * Base URL of the auth-service (handles OIDC callbacks, `POST /auth/login`,
   * `GET /auth/me`, `POST /auth/logout`).
   * @example "https://auth.ghatana.io"
   */
  authServiceBaseUrl: string;

  /**
   * Base URL of the auth-gateway (handles `POST /auth/exchange`).
   * @example "https://gateway.ghatana.io"
   */
  authGatewayBaseUrl: string;

  /**
   * Identifier of the calling product (used as the `redirect_uri` hint when
   * initiating the OIDC login flow).
   * @example "tutorputor"
   */
  productId: string;

  /**
   * Override the URL to redirect the browser to after a successful login.
   * Defaults to `window.location.origin`.
   */
  postLoginRedirectUrl?: string;

  /**
   * Query parameter name that the auth-service uses to pass the platform token
   * after a successful OIDC callback.
   * @default "platform_token"
   */
  platformTokenParam?: string;

  /**
   * sessionStorage key used to persist the platform token across page navigation
   * within the same browser tab.
   * @default "ghatana_platform_token"
   */
  storageKey?: string;

  /**
   * Number of milliseconds before token expiry to proactively refresh.
   * @default 60_000 (1 minute)
   */
  refreshThresholdMs?: number;
}

// ─── Internal constants ───────────────────────────────────────────────────────

const DEFAULT_PLATFORM_TOKEN_PARAM = 'platform_token';
const DEFAULT_STORAGE_KEY          = 'ghatana_platform_token';
const DEFAULT_REFRESH_THRESHOLD_MS = 60_000;

// ─── SsoClient ───────────────────────────────────────────────────────────────

/**
 * Lightweight, zero-dependency SSO client for Ghatana products.
 *
 * Responsibilities:
 * - Reads `?platform_token=<jwt>` from the URL after the OIDC redirect and
 *   persists it to `sessionStorage`.
 * - Exposes {@link isAuthenticated}, {@link getUser}, {@link getClaims}.
 * - {@link login} — redirects the browser to the OIDC initiation endpoint.
 * - {@link logout} — calls `POST /auth/logout`, clears local state, redirects.
 * - {@link exchangeForProductToken} — calls `POST /auth/exchange` on the
 *   auth-gateway to swap a product-scoped JWT for a platform token (or vice
 *   versa), supporting inter-product API calls.
 * - Proactive token refresh via a background interval.
 */
export class SsoClient {
  private readonly config: Required<SsoClientConfig>;
  private claims: PlatformTokenClaims | null = null;
  private rawToken: string | null = null;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(config: SsoClientConfig) {
    this.config = {
      platformTokenParam:  config.platformTokenParam  ?? DEFAULT_PLATFORM_TOKEN_PARAM,
      storageKey:          config.storageKey          ?? DEFAULT_STORAGE_KEY,
      refreshThresholdMs:  config.refreshThresholdMs  ?? DEFAULT_REFRESH_THRESHOLD_MS,
      postLoginRedirectUrl: config.postLoginRedirectUrl ?? '',
      ...config,
    };
  }

  // ── Lifecycle ───────────────────────────────────────────────────────────────

  /**
   * Initialises the SSO client. Call this once on app mount.
   *
   * 1. Checks the current URL for a `?platform_token=…` query parameter
   *    (present after a successful OIDC redirect from auth-service).
   * 2. If found, stores in `sessionStorage` and strips from the URL.
   * 3. Loads any previously stored token from `sessionStorage`.
   * 4. Schedules proactive token refresh.
   */
  async init(): Promise<void> {
    // 1. Extract token from URL query param (post-OIDC redirect)
    const urlToken = this.extractTokenFromUrl();
    if (urlToken) {
      sessionStorage.setItem(this.config.storageKey, urlToken);
      this.stripTokenFromUrl();
    }

    // 2. Load from sessionStorage
    const stored = sessionStorage.getItem(this.config.storageKey);
    if (stored) {
      this.applyToken(stored);
    }

    // 3. Schedule refresh
    this.scheduleRefresh();
  }

  // ── Auth state ──────────────────────────────────────────────────────────────

  /** Returns `true` if the client holds a valid, non-expired platform token. */
  isAuthenticated(): boolean {
    if (!this.claims) return false;
    return this.claims.exp * 1000 > Date.now();
  }

  /**
   * Returns the current user derived from the platform token claims.
   * Returns `null` if the user is not authenticated.
   */
  getUser(): SsoUser | null {
    if (!this.isAuthenticated() || !this.claims) return null;
    return {
      userId:    this.claims.sub,
      email:     this.claims.email,
      roles:     this.claims.roles,
      tenantId:  this.claims.tenantId,
      sessionId: this.claims.sessionId,
    };
  }

  /** Returns the raw token claims or `null` if unauthenticated. */
  getClaims(): PlatformTokenClaims | null {
    return this.isAuthenticated() ? this.claims : null;
  }

  /** Returns the raw JWT string or `null` if unauthenticated. */
  getRawToken(): string | null {
    return this.isAuthenticated() ? this.rawToken : null;
  }

  // ── Actions ─────────────────────────────────────────────────────────────────

  /**
   * Redirects the browser to the auth-service OIDC login initiation endpoint.
   *
   * @param redirectUrl Override the post-login redirect URL for this call.
   */
  login(redirectUrl?: string): void {
    const redirect = redirectUrl
        ?? this.config.postLoginRedirectUrl
        ?? window.location.origin;

    const loginUrl = new URL(`${this.config.authServiceBaseUrl}/auth/login`);
    loginUrl.searchParams.set('redirect_uri', redirect);
    loginUrl.searchParams.set('product_id',  this.config.productId);
    window.location.assign(loginUrl.toString());
  }

  /**
   * Logs the user out.
   *
   * Calls `POST /auth/logout` on the auth-service, clears local state,
   * and optionally redirects to `postLogoutUrl` (defaults to login page).
   *
   * @param postLogoutUrl URL to redirect to after logout (default: same origin).
   */
  async logout(postLogoutUrl?: string): Promise<void> {
    if (this.rawToken) {
      try {
        await fetch(`${this.config.authServiceBaseUrl}/auth/logout`, {
          method:      'POST',
          credentials: 'include',    // sends the ghatana_session cookie
          headers:     { 'Content-Type': 'application/json' },
          body:        JSON.stringify({ token: this.rawToken }),
        });
      } catch {
        // Best-effort; proceed with local cleanup regardless
      }
    }
    this.clearLocalState();
    const target = postLogoutUrl ?? window.location.origin;
    window.location.assign(target);
  }

  /**
   * Exchanges a product-scoped JWT for a short-lived (15-minute) platform token
   * by calling `POST /auth/exchange` on the auth-gateway.
   *
   * This is useful when a product needs to call another product's API on behalf
   * of the user; the calling product passes its own JWT and receives a platform
   * token that the target product will accept.
   *
   * @param productJwt The product-scoped JWT to exchange.
   * @returns The platform token string, or `null` if the exchange failed.
   */
  async exchangeForPlatformToken(productJwt: string): Promise<string | null> {
    try {
      const response = await fetch(`${this.config.authGatewayBaseUrl}/auth/exchange`, {
        method:  'POST',
        headers: {
          'Content-Type':  'application/json',
          'Authorization': `Bearer ${productJwt}`,
        },
      });
      if (!response.ok) return null;
      const data = await response.json() as { platformToken?: string };
      return data.platformToken ?? null;
    } catch {
      return null;
    }
  }

  /**
   * Fetches the current user from `GET /auth/me` on the auth-service.
   * Useful to validate server-side session state or refresh user metadata.
   *
   * @returns Raw JSON response from auth-service or `null` on error.
   */
  async fetchMe(): Promise<Record<string, unknown> | null> {
    const token = this.getRawToken();
    if (!token) return null;
    try {
      const response = await fetch(`${this.config.authServiceBaseUrl}/auth/me`, {
        credentials: 'include',
        headers:     token ? { 'Authorization': `Bearer ${token}` } : {},
      });
      if (!response.ok) return null;
      return await response.json() as Record<string, unknown>;
    } catch {
      return null;
    }
  }

  // ── Private helpers ─────────────────────────────────────────────────────────

  private extractTokenFromUrl(): string | null {
    try {
      const params = new URLSearchParams(window.location.search);
      return params.get(this.config.platformTokenParam);
    } catch {
      return null;
    }
  }

  private stripTokenFromUrl(): void {
    try {
      const url = new URL(window.location.href);
      url.searchParams.delete(this.config.platformTokenParam);
      window.history.replaceState({}, '', url.toString());
    } catch {
      // Non-browser environment or replaceState not available — no-op
    }
  }

  private applyToken(raw: string): void {
    const decoded = decodeJwtPayload(raw);
    if (!decoded) return;
    this.rawToken = raw;
    this.claims   = decoded;
  }

  private scheduleRefresh(): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
    if (!this.claims) return;

    const expiresAtMs   = this.claims.exp * 1000;
    const refreshAtMs   = expiresAtMs - this.config.refreshThresholdMs;
    const delayMs       = Math.max(0, refreshAtMs - Date.now());

    this.refreshTimer = setTimeout(async () => {
      // If the product has a product-scoped token, exchange it for a new platform
      // token. Otherwise, logout (the session has expired).
      const stored = sessionStorage.getItem(this.config.storageKey);
      if (stored) {
        const newToken = await this.exchangeForPlatformToken(stored);
        if (newToken) {
          sessionStorage.setItem(this.config.storageKey, newToken);
          this.applyToken(newToken);
          this.scheduleRefresh();
          return;
        }
      }
      // Could not refresh — clear state (user must re-login)
      this.clearLocalState();
    }, delayMs);
  }

  private clearLocalState(): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
    sessionStorage.removeItem(this.config.storageKey);
    this.rawToken = null;
    this.claims   = null;
  }
}

// ─── JWT helpers ──────────────────────────────────────────────────────────────

/**
 * Decodes the payload section of a JWT without verifying the signature.
 *
 * Signature verification is the responsibility of the server. On the client
 * side we only need the claims for display and expiry scheduling.
 *
 * @param token Raw JWT string.
 * @returns Decoded payload claims or `null` if the token is malformed.
 */
export function decodeJwtPayload(token: string): PlatformTokenClaims | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    // Base64-URL decode: replace URL-safe chars, pad to multiple of 4
    const payload = parts[1]!
        .replace(/-/g, '+')
        .replace(/_/g, '/');
    const padded  = payload + '=='.slice(0, (4 - (payload.length % 4)) % 4);
    const decoded = atob(padded);
    return JSON.parse(decoded) as PlatformTokenClaims;
  } catch {
    return null;
  }
}

/**
 * Returns `true` if the given JWT appears to be a Ghatana platform token
 * (contains `tokenType: "PLATFORM"` in its payload).
 */
export function isPlatformToken(token: string): boolean {
  const claims = decodeJwtPayload(token);
  return claims?.tokenType === 'PLATFORM';
}

/**
 * Returns the number of seconds until the token expires, or 0 if already
 * expired or invalid.
 */
export function tokenTtlSeconds(token: string): number {
  const claims = decodeJwtPayload(token);
  if (!claims) return 0;
  return Math.max(0, claims.exp - Math.floor(Date.now() / 1000));
}
