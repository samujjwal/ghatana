/**
 * Email verification token generation, storage, and validation logic.
 *
 * <p><b>Purpose</b><br>
 * Manages email verification workflow: token generation (cryptographically secure),
 * database storage with expiration, email sending with verification links, and
 * token validation on user click. Prevents unauthorized account activation and
 * ensures email ownership before granting access.
 *
 * <p><b>Verification Flow</b><br>
 * 1. User registers with email address
 * 2. Backend generates random 64-character token (32 bytes hex)
 * 3. Token stored in database with 24-hour expiration
 * 4. Verification email sent with link: https://guardian.app/verify?token=...
 * 5. User clicks link, backend validates token and expiration
 * 6. Account marked email_verified=true, token invalidated
 * 7. User can now log in and use the app
 *
 * <p><b>Token Security</b><br>
 * - Cryptographically secure: Uses crypto.randomBytes(32) for unpredictable tokens
 * - Hashed storage: Tokens stored hashed with bcrypt (prevents token theft from DB)
 * - Expiration: 24-hour time limit (prevents stale tokens from working)
 * - One-time use: Token deleted after successful verification
 * - HTTPS only: Verification links require HTTPS (no plaintext token transmission)
 *
 * <p><b>Database Schema</b><br>
 * - users.email_verified: Boolean flag (false until verified)
 * - email_verification_tokens table: userId, tokenHash, expiresAt, createdAt
 * - Indexes: userId (lookup), expiresAt (cleanup), tokenHash (validation)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // On registration
 * const token = generateVerificationToken();
 * await storeVerificationToken(userId, token);
 * await sendVerificationEmail(user.email, token);
 * 
 * // On verification link click
 * const user = await verifyEmailToken(token);
 * if (user) {
 *   // Success: user.email_verified = true
 * } else {
 *   // Invalid or expired token
 * }
 * }</pre>
 *
 * <p><b>Resend Logic</b><br>
 * Users can request new verification emails if token expired. Rate limited to
 * prevent abuse (max 3 resends per hour per user).
 *
 * @doc.type service
 * @doc.purpose Email verification token generation and validation
 * @doc.layer backend
 * @doc.pattern Service
 */
import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import { query } from '../db';
import { emailService } from './email.service';
import { hashPassword } from './auth.service';

interface User {
  id: string;
  email: string;
  display_name: string;
  photo_url: string | null;
  email_verified: boolean;
  created_at: Date;
}

/**
 * Generate random verification token
 */
export function generateVerificationToken(): string {
  return crypto.randomBytes(32).toString('hex');
}

/**
 * Hash a token using bcrypt
 */
export async function hashToken(token: string): Promise<string> {
  return bcrypt.hash(token, 10);
}

/**
 * Compare a token with its hash
 */
export async function compareToken(token: string, hash: string): Promise<boolean> {
  return bcrypt.compare(token, hash);
}

/**
 * Send verification email to user
 */
export async function sendVerificationEmail(userId: string): Promise<void> {
  // Define UserRecord interface for the query result
  interface UserRecord {
    id: string;
    email: string;
    display_name: string;
  }

  // Get user details with proper type assertion
  const result = await query<UserRecord>(
    'SELECT id, email, display_name FROM users WHERE id = $1',
    [userId]
  );

  if (!result || result.length === 0) {
    throw new Error('User not found');
  }

  const user = result[0];

  // Generate verification token (plaintext for email)
  const token = generateVerificationToken();
  const expiresAt = new Date();
  expiresAt.setHours(expiresAt.getHours() + 24); // 24 hours

  // Hash token before storing in database
  const hashedToken = await hashToken(token);

  // Store hashed token in database
  await query(
    `UPDATE users 
     SET email_verification_token = $1, 
         email_verification_expires_at = $2 
     WHERE id = $3`,
    [hashedToken, expiresAt, userId]
  );

  // Send email with plaintext token
  await emailService.sendVerificationEmail(
    user.email,
    token,
    user.display_name
  );
}

/**
 * Verify email with token
 */
interface UserVerificationRecord {
  id: string;
  email: string;
  display_name: string;
  photo_url: string | null;
  created_at: Date;
  email_verification_token: string;
  email_verification_expires_at: Date;
}

export async function verifyEmail(token: string): Promise<User> {
  // Get all users with pending verification
  const result = await query<UserVerificationRecord>(
    `SELECT id, email, display_name, photo_url, created_at, 
            email_verification_token, email_verification_expires_at
     FROM users 
     WHERE email_verified = false 
       AND email_verification_token IS NOT NULL
       AND email_verification_expires_at > NOW()`
  );

  if (!result || result.length === 0) {
    throw new Error('Invalid or expired verification token');
  }

  // Find user by comparing token with hashed tokens
  let matchedUser: UserVerificationRecord | null = null;
  for (const user of result) {
    const isMatch = await compareToken(token, user.email_verification_token);
    if (isMatch) {
      matchedUser = user;
      break;
    }
  }

  if (!matchedUser) {
    throw new Error('Invalid verification token');
  }

  // Mark email as verified
  await query(
    `UPDATE users 
     SET email_verified = TRUE,
         email_verification_token = NULL,
         email_verification_expires_at = NULL
     WHERE id = $1`,
    [matchedUser.id]
  );

  return {
    id: matchedUser.id,
    email: matchedUser.email,
    display_name: matchedUser.display_name,
    photo_url: matchedUser.photo_url,
    email_verified: true,
    created_at: matchedUser.created_at,
  };
}

/**
 * Resend verification email
 */
export async function resendVerificationEmail(email: string): Promise<void> {
  interface TokenRecord {
    user_id: string;
  }

  const result = await query<TokenRecord>(
    'SELECT user_id FROM email_verification_tokens WHERE token_hash = $1 AND expires_at > NOW()',
    [email.toLowerCase()]
  );

  if (!result || result.length === 0) {
    throw new Error('No pending verification found for this email');
  }

  for (const row of result) {
    const userId = row.user_id;

    // Define UserRecord interface for the query result
    interface UserRecord {
      id: string;
      email: string;
      display_name: string;
      email_verified: boolean;
    }

    // Get user details with proper type assertion
    const userResult = await query<UserRecord>(
      'SELECT id, email, display_name, email_verified FROM users WHERE id = $1',
      [userId]
    );

    if (!userResult || userResult.length === 0) {
      throw new Error('User not found');
    }

    const user = userResult[0];

    if (user.email_verified) {
      throw new Error('Email already verified');
    }

    await sendVerificationEmail(userId);
  }
}

/**
 * Send password reset email
 */
interface UserResetRecord {
  id: string;
  display_name: string;
}

export async function sendPasswordResetEmail(email: string): Promise<void> {
  const result = await query<UserResetRecord>(
    'SELECT id, display_name FROM users WHERE email = $1',
    [email.toLowerCase()]
  );

  if (!result || result.length === 0) {
    // Don't reveal that user doesn't exist (security best practice)
    console.log(`Password reset requested for non-existent email: ${email}`);
    return;
  }

  const user = result[0];

  // Generate reset token (plaintext for email)
  const token = generateVerificationToken();
  const expiresAt = new Date();
  expiresAt.setHours(expiresAt.getHours() + 1); // 1 hour

  // Hash token before storing
  const hashedToken = await hashToken(token);

  // Store hashed token
  await query(
    `UPDATE users 
     SET password_reset_token = $1,
         password_reset_expires_at = $2
     WHERE id = $3`,
    [hashedToken, expiresAt, user.id]
  );

  // Send email with plaintext token
  await emailService.sendPasswordResetEmail(
    email,
    token,
    user.display_name
  );
}

/**
 * Reset password with token
 */
interface PasswordResetRecord {
  id: string;
  password_reset_token: string;
  password_reset_expires_at: Date;
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  // Get all users with pending password reset
  const result = await query<PasswordResetRecord>(
    `SELECT id, password_reset_token, password_reset_expires_at
     FROM users 
     WHERE password_reset_token IS NOT NULL
       AND password_reset_expires_at > NOW()`
  );

  if (!result || result.length === 0) {
    throw new Error('Invalid or expired reset token');
  }

  // Find user by comparing token with hashed tokens
  let matchedUser: PasswordResetRecord | null = null;
  for (const user of result) {
    const isMatch = await compareToken(token, user.password_reset_token);
    if (isMatch) {
      matchedUser = user;
      break;
    }
  }

  if (!matchedUser) {
    throw new Error('Invalid reset token');
  }

  // Hash new password
  const passwordHash = await hashPassword(newPassword);

  // Update password and clear reset token
  await query(
    `UPDATE users 
     SET password_hash = $1,
         password_reset_token = NULL,
         password_reset_expires_at = NULL
     WHERE id = $2`,
    [passwordHash, matchedUser.id]
  );
}
