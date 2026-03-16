/**
 * Auth Service — Platform-integrated authentication and user management.
 *
 * <p><b>Purpose</b><br>
 * Provides user profile operations and a backward-compatible local JWT verifier.
 * All primary authentication (login, registration, token issuance, password reset)
 * is delegated to the Ghatana auth-gateway. This service no longer manages
 * bcrypt password hashes or issues its own JWT tokens.
 *
 * <p><b>Token Strategy</b><br>
 * The auth middleware validates tokens in priority order:
 * 1. Platform auth-gateway (primary, cross-service SSO)
 * 2. {@link verifyAccessToken} local fallback for tokens previously issued
 *    by DCMAAR before the platform migration (read-only verification only;
 *    no new tokens are generated here).
 *
 * @doc.type service
 * @doc.purpose User profile management and legacy JWT verification
 * @doc.layer backend
 * @doc.pattern Service
 */
import jwt from "jsonwebtoken";
import { query } from "../db";

const JWT_SECRET = process.env.JWT_SECRET || "development-secret-key";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface User {
  id: string;
  email: string;
  display_name: string | null;
  photo_url: string | null;
  email_verified: boolean;
  created_at: Date;
}

// ---------------------------------------------------------------------------
// Token verification (legacy fallback — no new token generation)
// ---------------------------------------------------------------------------

/**
 * Verify and decode a locally-issued JWT access token.
 *
 * <p>Used by the auth middleware as a fallback when the auth-gateway is
 * unavailable or returns a negative result. No new tokens are issued here;
 * this only validates tokens that were created before the platform migration.
 *
 * @param token JWT access token from the Authorization header
 * @return Object with userId if valid, null if invalid/expired
 * @doc.type function
 * @doc.purpose Validate legacy local access token
 * @doc.layer backend
 * @doc.pattern Security
 */
export function verifyAccessToken(token: string): { userId: string } | null {
  try {
    const decoded = jwt.verify(token, JWT_SECRET) as {
      userId: string;
      type: string;
    };
    if (decoded.type !== "access") return null;
    return { userId: decoded.userId };
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// User profile
// ---------------------------------------------------------------------------

/**
 * Retrieve user record by ID.
 *
 * @param userId User ID (UUID from users.id)
 * @return Promise resolving to User object if found, null if not found
 * @doc.type function
 * @doc.purpose Fetch user profile by ID
 * @doc.layer backend
 * @doc.pattern Service
 */
export async function getUserById(userId: string): Promise<User | null> {
  const result = await query<User>(
    `SELECT id, email, display_name, photo_url, email_verified, created_at
     FROM users WHERE id = $1`,
    [userId]
  );

  return result.length > 0 ? result[0] : null;
}

/**
 * Update user profile information (display name, photo URL).
 *
 * @param userId  User ID to update
 * @param updates Object with optional displayName and/or photoUrl
 * @return Promise resolving to updated User object
 * @throws Error if no fields provided or user not found
 * @doc.type function
 * @doc.purpose Update user profile metadata
 * @doc.layer backend
 * @doc.pattern Service
 */
export async function updateProfile(
  userId: string,
  updates: { displayName?: string; photoUrl?: string }
): Promise<User> {
  const fields: string[] = [];
  const values: unknown[] = [];
  let paramIndex = 1;

  if (updates.displayName !== undefined) {
    fields.push(`display_name = $${paramIndex++}`);
    values.push(updates.displayName);
  }

  if (updates.photoUrl !== undefined) {
    fields.push(`photo_url = $${paramIndex++}`);
    values.push(updates.photoUrl);
  }

  if (fields.length === 0) {
    throw new Error("No fields to update");
  }

  values.push(userId);

  const result = await query<User>(
    `UPDATE users SET ${fields.join(", ")}
     WHERE id = $${paramIndex}
     RETURNING id, email, display_name, photo_url, email_verified, created_at`,
    values
  );

  if (result.length === 0) {
    throw new Error("User not found");
  }

  return result[0];
}
