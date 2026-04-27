/**
 * @tutorputor/auth-client — Token lifecycle utilities
 *
 * Browser-safe JWT decode, expiry checks, and token claim extraction.
 * Does NOT depend on any backend-only libraries (no `jsonwebtoken`).
 *
 * @doc.type module
 * @doc.purpose Client-side JWT token decode and lifecycle management
 * @doc.layer product
 * @doc.pattern ValueObject
 */

import { z } from "zod";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * Canonical token pair held by any TutorPutor client.
 */
export interface AuthTokenPair {
  readonly accessToken: string;
  readonly refreshToken: string;
}

/**
 * Decoded claims from a TutorPutor access token.
 */
export interface TutorPutorTokenClaims {
  readonly sub: string;
  readonly email: string;
  readonly displayName: string;
  readonly role: string;
  readonly tenantId: string;
  readonly iat: number;
  readonly exp: number;
}

const TokenClaimsSchema = z.object({
  sub: z.string().min(1),
  email: z.string().email(),
  displayName: z.string().default(""),
  role: z.string().min(1),
  tenantId: z.string().min(1),
  iat: z.number(),
  exp: z.number(),
});

// ---------------------------------------------------------------------------
// JWT decode (browser-safe, no crypto)
// ---------------------------------------------------------------------------

/**
 * Decodes the payload of a JWT without verifying the signature.
 * Signature verification is always performed server-side.
 */
export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;

    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");

    const decoded: unknown = JSON.parse(atob(padded));
    if (typeof decoded !== "object" || decoded === null) return null;
    return decoded as Record<string, unknown>;
  } catch {
    return null;
  }
}

/**
 * Extracts and validates TutorPutor-specific claims from an access token.
 * Returns `null` if the token is malformed or missing required claims.
 */
export function extractTokenClaims(token: string): TutorPutorTokenClaims | null {
  const payload = decodeJwtPayload(token);
  if (!payload) return null;

  const result = TokenClaimsSchema.safeParse(payload);
  if (!result.success) return null;

  return result.data as TutorPutorTokenClaims;
}

// ---------------------------------------------------------------------------
// Expiry checks
// ---------------------------------------------------------------------------

/**
 * Returns the number of seconds until the token expires.
 * Negative means already expired.
 */
export function secondsUntilExpiry(token: string): number {
  const claims = extractTokenClaims(token);
  if (!claims) return -1;

  const nowSeconds = Math.floor(Date.now() / 1000);
  return claims.exp - nowSeconds;
}

/**
 * Returns true when the token is expired or within `bufferSeconds` of expiry.
 */
export function isTokenExpired(token: string, bufferSeconds = 30): boolean {
  return secondsUntilExpiry(token) <= bufferSeconds;
}

/**
 * Returns true when the access token is still valid (not expired, not malformed).
 */
export function hasValidAccessToken(tokenPair: Partial<AuthTokenPair>): boolean {
  if (!tokenPair.accessToken) return false;
  return !isTokenExpired(tokenPair.accessToken);
}
