/**
 * Authentication utilities for JWT and password hashing
 */

import bcrypt from "bcrypt";
import jwt from "jsonwebtoken";
import { FastifyRequest } from "fastify";

const SALT_ROUNDS = 10;
const JWT_EXPIRATION = process.env.JWT_EXPIRATION || "7d";

function getJwtSecret(): string {
  const secret = process.env.JWT_SECRET;
  if (!secret) {
    throw new Error('JWT_SECRET environment variable is required');
  }
  return secret;
}

/**
 * Hash a password using bcrypt
 */
export const hashPassword = async (password: string): Promise<string> => {
  return bcrypt.hash(password, SALT_ROUNDS);
};

/**
 * Compare a plain-text password with a hashed password
 */
export const comparePassword = async (
  password: string,
  hash: string
): Promise<boolean> => {
  return bcrypt.compare(password, hash);
};

/**
 * Extract user ID from JWT token in request
 */
export const getUserIdFromRequest = (request: FastifyRequest): string => {
  const user = request.user as { userId: string } | undefined;
  if (!user?.userId) {
    throw new Error("User not authenticated");
  }
  return user.userId;
};

/**
 * Simple auth guard for Fastify routes.
 * Verifies JWT token and populates request.user with { userId, email }.
 */
export const requireAuth = async (request: FastifyRequest) => {
  try {
    // Verify JWT token from Authorization header
    await (request as any).jwtVerify();
    
    const user = request.user as { userId?: string } | undefined;
    if (!user?.userId) {
      throw new Error("User not authenticated");
    }
  } catch (error) {
    throw new Error("User not authenticated");
  }
};

/**
 * JWT Payload structure
 */
export interface JwtPayload {
  userId: string;
  email: string;
  iat?: number;
  exp?: number;
}

/**
 * Generate a JWT token for a user
 */
export const generateToken = (payload: JwtPayload): string => {
  return jwt.sign(
    { userId: payload.userId, email: payload.email },
    getJwtSecret(),
    { expiresIn: JWT_EXPIRATION }
  );
};

/**
 * Verify and decode a JWT token
 */
export const verifyToken = (token: string): JwtPayload | null => {
  if (!token) return null;
  
  try {
    const decoded = jwt.verify(token, getJwtSecret()) as JwtPayload;
    return decoded;
  } catch {
    return null;
  }
};
