/**
 * Auth Test Fixtures
 *
 * Provides realistic test data and JWT generation utilities for authentication tests.
 *
 * <p><b>Purpose</b><br>
 * Utilities for generating valid, expired, and invalid JWT tokens for testing
 * authentication flows. Includes credential fixtures for login/registration testing
 * and token manipulation helpers for edge case testing.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { generateTokenPair, generateExpiredToken, credentialFixtures } from './auth.fixtures';
 *
 * const tokens = generateTokenPair({ userId: '123', email: 'user@example.com', role: 'parent' });
 * const expiredToken = generateExpiredToken(payload);
 * const { valid, invalid } = credentialFixtures;
 * }</pre>
 *
 * <p><b>Test Coverage</b><br>
 * Used by:
 * - AuthService tests (token generation, verification, refresh)
 * - Auth middleware tests (token validation, expiration)
 * - AuthRoute tests (login, logout, registration endpoints)
 * - Protected route tests (authorization checks)
 * - Session management tests
 *
 * <p><b>Token Generation</b><br>
 * - Access tokens: 15 minute expiration (JWT_SECRET)
 * - Refresh tokens: 7 day expiration (JWT_REFRESH_SECRET)
 * - Expired tokens: Signed with past expiration for testing
 * - Invalid tokens: Signed with wrong secret to test verification
 *
 * @doc.type fixtures
 * @doc.purpose Test utilities for JWT generation and authentication data
 * @doc.layer backend
 * @doc.pattern Test Factory
 */

import jwt from "jsonwebtoken";

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export interface JWTPayload {
  userId: string;
  email: string;
  role: "parent" | "child" | "admin";
  iat?: number;
  exp?: number;
}

/**
 * Generate a valid JWT access token
 */
export function generateAccessToken(payload: JWTPayload): string {
  return jwt.sign({ ...payload, type: 'access' }, process.env.JWT_SECRET!, { expiresIn: "15m" });
}

/**
 * Generate a valid JWT refresh token
 */
export function generateRefreshToken(payload: JWTPayload): string {
  return jwt.sign({ ...payload, type: 'refresh' }, process.env.JWT_REFRESH_SECRET!, {
    expiresIn: "7d",
  });
}

/**
 * Generate both access and refresh tokens
 */
export function generateTokenPair(payload: JWTPayload): TokenPair {
  return {
    accessToken: generateAccessToken(payload),
    refreshToken: generateRefreshToken(payload),
  };
}

/**
 * Generate an expired token (for testing expiration)
 */
export function generateExpiredToken(payload: JWTPayload): string {
  return jwt.sign(
    payload,
    process.env.JWT_SECRET!,
    { expiresIn: "-1h" } // Already expired
  );
}

/**
 * Generate an invalid token (wrong secret)
 */
export function generateInvalidToken(payload: JWTPayload): string {
  return jwt.sign(payload, "wrong-secret-key", { expiresIn: "15m" });
}

/**
 * Decode token without verification (for testing)
 */
export function decodeToken(token: string): JWTPayload {
  return jwt.decode(token) as JWTPayload;
}

export const authFixtures = {
  generateAccessToken,
  generateRefreshToken,
  generateTokenPair,
  generateExpiredToken,
  generateInvalidToken,
  decodeToken,
};

/**
 * Sample login credentials
 */
export const validCredentials = {
  email: "parent@example.com",
  password: "ParentPassword123!",
};

export const invalidCredentials = {
  email: "nonexistent@example.com",
  password: "WrongPassword123!",
};

export const weakPassword = {
  email: "test@example.com",
  password: "weak",
};

export const credentialFixtures = {
  valid: validCredentials,
  invalid: invalidCredentials,
  weakPassword,
};
