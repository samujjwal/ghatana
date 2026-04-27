/**
 * @tutorputor/auth-client — Auth request headers
 *
 * Canonical factory for HTTP request headers that include the Bearer token.
 * Eliminates per-app `authHeaders()` duplicates across web, admin, and mobile.
 *
 * @doc.type module
 * @doc.purpose Canonical auth request header builder
 * @doc.layer product
 * @doc.pattern ValueObject
 */

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type AuthHeaders = Record<string, string>;

// ---------------------------------------------------------------------------
// Header builder
// ---------------------------------------------------------------------------

/**
 * Builds the canonical set of HTTP headers for authenticated TutorPutor API requests.
 *
 * @param accessToken - The Bearer access token, or null for unauthenticated requests.
 * @param extra - Optional additional headers to merge (caller wins on conflicts).
 */
export function buildAuthHeaders(
  accessToken: string | null,
  extra?: Record<string, string>,
): AuthHeaders {
  const headers: AuthHeaders = {
    "Content-Type": "application/json",
  };

  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }

  if (extra) {
    Object.assign(headers, extra);
  }

  return headers;
}

/**
 * Builds headers for multipart/form-data requests (no Content-Type override —
 * the browser sets the boundary automatically).
 */
export function buildMultipartAuthHeaders(accessToken: string | null): AuthHeaders {
  const headers: AuthHeaders = {};
  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }
  return headers;
}

/**
 * Extracts the Bearer token string from an Authorization header value.
 * Returns null if the header is missing or malformed.
 *
 * Useful on the client when re-reading headers from a proxy response.
 */
export function extractBearerToken(authorizationHeader: string | undefined | null): string | null {
  if (!authorizationHeader) return null;
  const match = /^Bearer (.+)$/i.exec(authorizationHeader.trim());
  return match?.[1] ?? null;
}
