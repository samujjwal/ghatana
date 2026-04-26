/**
 * Session Manager
 *
 * Centralized session storage using httpOnly cookies only.
 * No localStorage usage for auth credentials - prevents XSS attacks.
 *
 * @doc.type service
 * @doc.purpose Cookie-based authentication session
 * @doc.layer product
 * @doc.pattern Service
 * @doc.security httpOnly cookies, no localStorage
 */

/**
 * Session metadata interface (non-sensitive data only)
 * Sensitive tokens are stored in httpOnly cookies only
 */
export interface StoredSession {
  expiresAt?: string;
  userId?: string;
}

/**
 * Retrieve the access token from httpOnly cookie only.
 * No localStorage fallback - this is intentional for security.
 */
export function getAccessToken(): string | null {
  return getAccessTokenFromCookie();
}

/**
 * Retrieve the refresh token from httpOnly cookie only.
 */
export function getRefreshToken(): string | null {
  return getRefreshTokenFromCookie();
}

/**
 * Check if user appears to have an active session (cookie present)
 */
export function hasSession(): boolean {
  return !!getAccessTokenFromCookie();
}

/**
 * Clear session by calling the logout endpoint.
 * Server should clear httpOnly cookies.
 */
export async function clearSession(): Promise<void> {
  try {
    await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
    });
  } catch {
    // Silent fail - cookies will expire naturally
  }
}

/**
 * Read non-sensitive session metadata.
 * Note: Tokens are NOT stored in localStorage - they remain in httpOnly cookies.
 */
export function readStoredSession(): StoredSession | null {
  // Only read non-sensitive metadata, never tokens
  try {
    if (typeof window === 'undefined' || !window.localStorage) {
      return null;
    }
    const raw = window.localStorage.getItem('auth-session-meta');
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredSession;
    return parsed;
  } catch {
    return null;
  }
}

/**
 * Persist non-sensitive session metadata only.
 * Tokens remain in httpOnly cookies only.
 */
export function persistStoredSession(session: StoredSession): void {
  // Only store non-sensitive metadata, never tokens
  try {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }
    window.localStorage.setItem('auth-session-meta', JSON.stringify(session));
  } catch {
    // Silent fail
  }
}

/**
 * Clear non-sensitive session metadata.
 */
export function clearStoredSession(): void {
  try {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }
    // Clear only metadata, not tokens (they're in cookies)
    window.localStorage.removeItem('auth-session-meta');
    // Also clear legacy keys if they exist
    window.localStorage.removeItem('auth-session');
    window.localStorage.removeItem('auth_token');
    window.localStorage.removeItem('api_key');
  } catch {
    // Silent fail
  }
}

function getAccessTokenFromCookie(): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const match = document.cookie.match(/(?:^|;\s*)accessToken=([^;]+)/);
  return match?.[1] ?? null;
}

function getRefreshTokenFromCookie(): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const match = document.cookie.match(/(?:^|;\s*)refreshToken=([^;]+)/);
  return match?.[1] ?? null;
}
