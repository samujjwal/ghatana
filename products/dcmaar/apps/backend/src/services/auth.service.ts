import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { randomBytes } from "crypto";
import type { SignOptions } from "jsonwebtoken";
import { query, transaction } from "../db";

const JWT_SECRET = process.env.JWT_SECRET || "development-secret-key";
const JWT_REFRESH_SECRET =
  process.env.JWT_REFRESH_SECRET || "development-refresh-key";
const JWT_EXPIRES_IN = process.env.JWT_EXPIRES_IN || "15m";
const JWT_REFRESH_EXPIRES_IN = process.env.JWT_REFRESH_EXPIRES_IN || "7d";
const BCRYPT_ROUNDS = parseInt(process.env.BCRYPT_ROUNDS || "12");

export interface User {
  id: string;
  email: string;
  display_name: string | null;
  photo_url: string | null;
  email_verified: boolean;
  created_at: Date;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface RegisterData {
  email: string;
  password: string;
  displayName?: string;
}

export interface LoginData {
  email: string;
  password: string;
}

/**
 * Hash password using bcrypt with configurable rounds.
 *
 * <p><b>Purpose</b><br>
 * Provides secure password hashing for Guardian authentication system.
 * Uses bcrypt algorithm with salt rounds configured via environment variable.
 *
 * <p><b>Security</b><br>
 * - Default: 12 rounds (OWASP recommendation for 2025)
 * - Configurable via BCRYPT_ROUNDS env var
 * - Automatically salted by bcrypt library
 * - Future-proof: Can increase rounds as hardware improves
 *
 * @param password Plain-text password to hash (never stored)
 * @return Promise resolving to bcrypt hash string (60 characters, $2a$ prefix)
 * @throws Error if bcrypt hashing fails
 * @see comparePassword
 * @doc.type function
 * @doc.purpose Secure password hashing for authentication
 * @doc.layer product
 * @doc.pattern Security
 */
export async function hashPassword(password: string): Promise<string> {
  return await bcrypt.hash(password, BCRYPT_ROUNDS);
}

/**
 * Compare plain-text password with bcrypt hash using constant-time comparison.
 *
 * <p><b>Purpose</b><br>
 * Verifies user-provided password against stored hash during login.
 * Uses bcrypt's built-in constant-time comparison to prevent timing attacks.
 *
 * <p><b>Security</b><br>
 * - Constant-time comparison (immune to timing attacks)
 * - Handles salt extraction automatically
 * - Works with any bcrypt cost factor
 *
 * @param password Plain-text password provided by user
 * @param hash Bcrypt hash from database (60 characters)
 * @return Promise resolving to true if password matches, false otherwise
 * @throws Error if comparison fails (invalid hash format)
 * @see hashPassword
 * @doc.type function
 * @doc.purpose Verify password against stored hash
 * @doc.layer product
 * @doc.pattern Security
 */
export async function comparePassword(
  password: string,
  hash: string
): Promise<boolean> {
  return await bcrypt.compare(password, hash);
}

/**
 * Generate short-lived JWT access token for API authentication.
 *
 * <p><b>Purpose</b><br>
 * Creates JWT token for authenticated API requests. Short expiry (15min default)
 * minimizes risk if token is compromised. Must be refreshed using refresh token.
 *
 * <p><b>Token Structure</b><br>
 * - Payload: { userId, type: "access" }
 * - Expiry: JWT_EXPIRES_IN env var (default: 15m)
 * - Signature: JWT_SECRET (symmetric HMAC)
 *
 * @param userId User ID to embed in token (from users.id)
 * @return Signed JWT string (header.payload.signature format)
 * @see generateRefreshToken
 * @see verifyAccessToken
 * @doc.type function
 * @doc.purpose Generate short-lived authentication token
 * @doc.layer product
 * @doc.pattern Security
 */
export function generateAccessToken(userId: string): string {
  return jwt.sign({ userId, type: "access" }, JWT_SECRET, {
    expiresIn: JWT_EXPIRES_IN,
  } as SignOptions);
}

/**
 * Generate long-lived JWT refresh token with unique identifier.
 *
 * <p><b>Purpose</b><br>
 * Creates refresh token for obtaining new access tokens without re-authentication.
 * Includes random JTI (JWT ID) to ensure uniqueness even when tokens generated
 * in rapid succession (prevents database unique constraint violations).
 *
 * <p><b>Token Structure</b><br>
 * - Payload: { userId, type: "refresh", jti: <random-hex> }
 * - Expiry: JWT_REFRESH_EXPIRES_IN env var (default: 7d)
 * - Signature: JWT_REFRESH_SECRET (separate from access token secret)
 * - JTI: 32-character hex string (16 random bytes)
 *
 * <p><b>Storage</b><br>
 * Refresh tokens are stored in refresh_tokens table for revocation support.
 *
 * @param userId User ID to embed in token (from users.id)
 * @return Signed JWT string with embedded JTI for uniqueness
 * @see generateAccessToken
 * @see verifyRefreshToken
 * @doc.type function
 * @doc.purpose Generate long-lived token for access token refresh
 * @doc.layer product
 * @doc.pattern Security
 */
export function generateRefreshToken(userId: string): string {
  // include a random jti to ensure each refresh token is unique even when
  // generated in rapid succession (prevents DB unique constraint collisions)
  const jti = randomBytes(16).toString("hex");
  return jwt.sign({ userId, type: "refresh", jti }, JWT_REFRESH_SECRET, {
    expiresIn: JWT_REFRESH_EXPIRES_IN,
  } as SignOptions);
}

/**
 * Verify and decode JWT access token.
 *
 * <p><b>Purpose</b><br>
 * Validates access token signature and expiry, extracts user ID.
 * Used by authentication middleware to protect API endpoints.
 *
 * <p><b>Validation</b><br>
 * - Signature verification using JWT_SECRET
 * - Expiry check (automatic via jwt.verify)
 * - Type validation (must be "access", not "refresh")
 *
 * @param token JWT access token string from Authorization header
 * @return Object with userId if valid, null if invalid/expired/wrong type
 * @see generateAccessToken
 * @doc.type function
 * @doc.purpose Validate and decode access token
 * @doc.layer product
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
  } catch (_error) {
    return null;
  }
}

/**
 * Verify and decode JWT refresh token.
 *
 * <p><b>Purpose</b><br>
 * Validates refresh token signature and expiry, extracts user ID.
 * Used during token refresh flow to verify user identity before issuing new access token.
 *
 * <p><b>Validation</b><br>
 * - Signature verification using JWT_REFRESH_SECRET (separate from access token)
 * - Expiry check (automatic via jwt.verify)
 * - Type validation (must be "refresh", not "access")
 *
 * <p><b>Note</b><br>
 * This only validates JWT signature/expiry. Database check in refreshAccessToken()
 * ensures token hasn't been revoked.
 *
 * @param token JWT refresh token string from client
 * @return Object with userId if valid, null if invalid/expired/wrong type
 * @see generateRefreshToken
 * @see refreshAccessToken
 * @doc.type function
 * @doc.purpose Validate and decode refresh token
 * @doc.layer product
 * @doc.pattern Security
 */
export function verifyRefreshToken(token: string): { userId: string } | null {
  try {
    const decoded = jwt.verify(token, JWT_REFRESH_SECRET) as {
      userId: string;
      type: string;
    };
    if (decoded.type !== "refresh") return null;
    return { userId: decoded.userId };
  } catch (_error) {
    return null;
  }
}

/**
 * Register new user account with email and password.
 *
 * <p><b>Purpose</b><br>
 * Creates new user account in Guardian system. Handles duplicate email detection,
 * password hashing, and automatic token generation for immediate authentication.
 *
 * <p><b>Process</b><br>
 * 1. Check email uniqueness (case-insensitive)
 * 2. Hash password with bcrypt (12 rounds)
 * 3. Create user record (email_verified = false initially)
 * 4. Generate access + refresh tokens
 * 5. Store refresh token in database
 * 6. Return tokens + user data
 *
 * <p><b>Transaction Safety</b><br>
 * All operations run in database transaction to ensure atomicity.
 * If any step fails, entire registration is rolled back.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { accessToken, refreshToken, user } = await register({
 *   email: 'parent@example.com',
 *   password: 'SecurePass123!',
 *   displayName: 'Jane Doe' // optional
 * });
 * }</pre>
 *
 * @param data Registration data (email, password, optional displayName)
 * @return Promise resolving to tokens + user object (password_hash excluded)
 * @throws Error if email already exists or database operation fails
 * @see login
 * @see hashPassword
 * @doc.type function
 * @doc.purpose Create new user account with authentication tokens
 * @doc.layer product
 * @doc.pattern Service
 */
export async function register(data: RegisterData): Promise<AuthTokens> {
  const { email, password, displayName } = data;

  return await transaction(async (client) => {
    // Check if user already exists
    const existingUsers = await client.query(
      "SELECT id FROM users WHERE email = $1",
      [email.toLowerCase()]
    );

    if (existingUsers.rows.length > 0) {
      throw new Error("User with this email already exists");
    }

    // Hash password
    const passwordHash = await hashPassword(password);

    // Create user
    const userResult = await client.query(
      `INSERT INTO users (email, password_hash, display_name, email_verified)
       VALUES ($1, $2, $3, $4)
       RETURNING id, email, display_name, photo_url, email_verified, created_at`,
      [email.toLowerCase(), passwordHash, displayName || null, false]
    );

    const user: User = {
      id: userResult.rows[0].id,
      email: userResult.rows[0].email,
      display_name: userResult.rows[0].display_name,
      photo_url: userResult.rows[0].photo_url,
      email_verified: userResult.rows[0].email_verified,
      created_at: userResult.rows[0].created_at,
    };

    // Generate tokens
    const accessToken = generateAccessToken(user.id);
    const refreshToken = generateRefreshToken(user.id);

    // Store refresh token
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + 7); // 7 days

    await client.query(
      "INSERT INTO refresh_tokens (user_id, token, expires_at) VALUES ($1, $2, $3)",
      [user.id, refreshToken, expiresAt]
    );

    return { accessToken, refreshToken, user };
  });
}

/**
 * Authenticate user with email and password.
 *
 * <p><b>Purpose</b><br>
 * Validates user credentials and generates new authentication tokens.
 * Updates last_login timestamp for security monitoring.
 *
 * <p><b>Process</b><br>
 * 1. Lookup user by email (case-insensitive)
 * 2. Verify password using bcrypt constant-time comparison
 * 3. Update last_login timestamp (NOW())
 * 4. Generate fresh access + refresh tokens
 * 5. Store refresh token in database
 * 6. Return tokens + user data
 *
 * <p><b>Security</b><br>
 * - Returns generic error for both invalid email AND invalid password
 *   (prevents user enumeration attacks)
 * - Constant-time password comparison (prevents timing attacks)
 * - Refresh token stored with 7-day expiry
 *
 * <p><b>Transaction Safety</b><br>
 * All operations run in database transaction for atomicity.
 *
 * @param data Login credentials (email, password)
 * @return Promise resolving to tokens + user object (password_hash excluded)
 * @throws Error with message "Invalid email or password" if auth fails
 * @see register
 * @see comparePassword
 * @doc.type function
 * @doc.purpose Authenticate user and generate session tokens
 * @doc.layer product
 * @doc.pattern Service
 */
export async function login(data: LoginData): Promise<AuthTokens> {
  const { email, password } = data;

  return await transaction(async (client) => {
    // Get user
    const userResult = await client.query(
      `SELECT id, email, password_hash, display_name, photo_url, email_verified, created_at
       FROM users WHERE email = $1`,
      [email.toLowerCase()]
    );

    if (userResult.rows.length === 0) {
      throw new Error("Invalid email or password");
    }

    const userRow = userResult.rows[0];

    // Verify password
    const isValidPassword = await comparePassword(
      password,
      userRow.password_hash
    );

    if (!isValidPassword) {
      throw new Error("Invalid email or password");
    }

    const user: User = {
      id: userRow.id,
      email: userRow.email,
      display_name: userRow.display_name,
      photo_url: userRow.photo_url,
      email_verified: userRow.email_verified,
      created_at: userRow.created_at,
    };

    // Update last login
    await client.query("UPDATE users SET last_login = NOW() WHERE id = $1", [
      user.id,
    ]);

    // Generate tokens
    const accessToken = generateAccessToken(user.id);
    const refreshToken = generateRefreshToken(user.id);

    // Store refresh token
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + 7);

    await client.query(
      "INSERT INTO refresh_tokens (user_id, token, expires_at) VALUES ($1, $2, $3)",
      [user.id, refreshToken, expiresAt]
    );

    return { accessToken, refreshToken, user };
  });
}

/**
 * Generate new access token using valid refresh token.
 *
 * <p><b>Purpose</b><br>
 * Allows clients to obtain new access token without re-authentication.
 * Access tokens expire after 15 minutes, refresh tokens last 7 days.
 *
 * <p><b>Process</b><br>
 * 1. Verify refresh token JWT signature + expiry
 * 2. Check token exists in database (not revoked)
 * 3. Check database expiry (additional layer beyond JWT expiry)
 * 4. Generate new access token
 * 5. Return new access token (refresh token unchanged)
 *
 * <p><b>Security</b><br>
 * - Double expiry check: JWT expiry + database expiry
 * - Database lookup ensures token hasn't been revoked (logout)
 * - Does NOT generate new refresh token (client keeps existing)
 *
 * @param refreshToken JWT refresh token from client
 * @return Promise resolving to new access token
 * @throws Error if refresh token invalid, expired, revoked, or not found
 * @see generateRefreshToken
 * @see logout
 * @doc.type function
 * @doc.purpose Refresh access token without re-authentication
 * @doc.layer product
 * @doc.pattern Service
 */
export async function refreshAccessToken(
  refreshToken: string
): Promise<{ accessToken: string }> {
  const decoded = verifyRefreshToken(refreshToken);

  if (!decoded) {
    throw new Error("Invalid refresh token");
  }

  // Check if refresh token exists and is valid
  const tokenResult = await query<{ user_id: string; expires_at: Date }>(
    "SELECT user_id, expires_at FROM refresh_tokens WHERE token = $1",
    [refreshToken]
  );

  if (tokenResult.length === 0) {
    throw new Error("Refresh token not found");
  }

  const tokenData = tokenResult[0];

  if (new Date(tokenData.expires_at) < new Date()) {
    throw new Error("Refresh token expired");
  }

  // Generate new access token
  const accessToken = generateAccessToken(tokenData.user_id);

  return { accessToken };
}

/**
 * Logout user by revoking refresh token.
 *
 * <p><b>Purpose</b><br>
 * Immediately invalidates refresh token, preventing future access token generation.
 * Access tokens remain valid until expiry (15 minutes default).
 *
 * <p><b>Process</b><br>
 * - Delete refresh token from database
 * - Access token continues working until natural expiry
 * - Client should discard both tokens immediately
 *
 * <p><b>Security Note</b><br>
 * For immediate revocation (e.g., account compromise), implement token blacklist
 * or reduce access token expiry time. Current design optimizes for performance
 * (no DB lookup on every API request).
 *
 * @param refreshToken JWT refresh token to revoke
 * @return Promise resolving when token deleted (void)
 * @see login
 * @see refreshAccessToken
 * @doc.type function
 * @doc.purpose Revoke refresh token to end session
 * @doc.layer product
 * @doc.pattern Service
 */
export async function logout(refreshToken: string): Promise<void> {
  await query("DELETE FROM refresh_tokens WHERE token = $1", [refreshToken]);
}

/**
 * Retrieve user record by ID.
 *
 * <p><b>Purpose</b><br>
 * Fetches complete user profile data excluding sensitive fields.
 * Used by authentication middleware after token verification.
 *
 * <p><b>Data Returned</b><br>
 * - id, email, display_name, photo_url
 * - email_verified, created_at
 * - password_hash EXCLUDED for security
 *
 * @param userId User ID (UUID from users.id)
 * @return Promise resolving to User object if found, null if not found
 * @see verifyAccessToken
 * @doc.type function
 * @doc.purpose Fetch user profile by ID
 * @doc.layer product
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
 * <p><b>Purpose</b><br>
 * Allows users to update their profile metadata. Supports partial updates
 * (only provided fields are changed).
 *
 * <p><b>Updatable Fields</b><br>
 * - displayName: User's display name (nullable)
 * - photoUrl: Profile picture URL (nullable)
 *
 * <p><b>Dynamic Query</b><br>
 * Builds SQL UPDATE dynamically based on provided fields.
 * If no fields provided, throws error (prevents no-op updates).
 *
 * @param userId User ID to update (from users.id)
 * @param updates Object with optional displayName and/or photoUrl
 * @return Promise resolving to updated User object
 * @throws Error if no fields provided or user not found
 * @doc.type function
 * @doc.purpose Update user profile metadata
 * @doc.layer product
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

/**
 * Generate password reset token for user account.
 *
 * <p><b>Purpose</b><br>
 * Creates secure reset token and stores it in database with 1-hour expiry.
 * Token sent to user's email for password recovery flow.
 *
 * <p><b>Process</b><br>
 * 1. Lookup user by email
 * 2. Generate cryptographically secure random token (64 hex chars)
 * 3. Store token + expiry (NOW() + 1 hour) in database
 * 4. Return token to caller (typically sent via email)
 *
 * <p><b>Security</b><br>
 * - Token: 32 random bytes (256 bits of entropy)
 * - Expiry: Database-calculated using PostgreSQL NOW() + INTERVAL (timezone-safe)
 * - Single-use: Token consumed by resetPassword()
 *
 * <p><b>Timezone Safety</b><br>
 * Uses PostgreSQL `NOW() + INTERVAL '1 hour'` instead of JavaScript Date
 * to avoid timezone offset issues. See TIMESTAMP_FIX_SUMMARY.md.
 *
 * @param email User's email address (case-insensitive)
 * @return Promise resolving to reset token (64 hex characters)
 * @throws Error if user not found
 * @see resetPassword
 * @doc.type function
 * @doc.purpose Generate password reset token with expiry
 * @doc.layer product
 * @doc.pattern Service
 */
export async function requestPasswordReset(email: string): Promise<string> {
  const user = await query<{ id: string }>(
    "SELECT id FROM users WHERE email = $1",
    [email.toLowerCase()]
  );

  if (user.length === 0) {
    throw new Error("User not found");
  }

  // Generate reset token (UUID)
  const resetToken = require("crypto").randomBytes(32).toString("hex");

  // Set expiry time: NOW + 1 hour (using UTC)
  // Use PostgreSQL's interval arithmetic instead of JavaScript dates
  // to avoid timezone confusion
  await query(
    `UPDATE users 
     SET password_reset_token = $1, password_reset_expires_at = NOW() + INTERVAL '1 hour'
     WHERE id = $2`,
    [resetToken, user[0].id]
  );

  return resetToken;
}

/**
 * Reset user password using valid reset token.
 *
 * <p><b>Purpose</b><br>
 * Validates reset token and updates user password. Token consumed after use
 * (cleared from database).
 *
 * <p><b>Process</b><br>
 * 1. Validate token exists AND not expired (SQL: expires_at > NOW())
 * 2. Hash new password with bcrypt
 * 3. Update password + clear reset token fields
 * 4. All existing refresh tokens remain valid (user stays logged in on other devices)
 *
 * <p><b>Security</b><br>
 * - Token validation in SQL WHERE clause (timezone-safe)
 * - Single-use token (cleared after reset)
 * - Password hashed with bcrypt (12 rounds)
 *
 * <p><b>Timezone Safety</b><br>
 * Expiry check uses `WHERE expires_at > NOW()` in PostgreSQL for
 * timezone-safe comparison. See TIMESTAMP_FIX_SUMMARY.md.
 *
 * @param token Password reset token (from requestPasswordReset)
 * @param newPassword New password (will be hashed)
 * @return Promise resolving when password updated (void)
 * @throws Error if token invalid, expired, or user not found
 * @see requestPasswordReset
 * @see hashPassword
 * @doc.type function
 * @doc.purpose Reset password with validated token
 * @doc.layer product
 * @doc.pattern Service
 */
export async function resetPassword(
  token: string,
  newPassword: string
): Promise<void> {
  const user = await query<{ id: string }>(
    `SELECT id FROM users 
     WHERE password_reset_token = $1 
     AND password_reset_expires_at > NOW()`,
    [token]
  );

  if (user.length === 0) {
    throw new Error("Invalid or expired reset token");
  }

  const passwordHash = await hashPassword(newPassword);

  await query(
    `UPDATE users 
     SET password_hash = $1, password_reset_token = NULL, password_reset_expires_at = NULL
     WHERE id = $2`,
    [passwordHash, user[0].id]
  );
}
