/**
 * @tutorputor/validation — Auth schemas
 *
 * Zod schemas for authentication request/response validation.
 * Used by the platform service (`/api/v1/auth` routes) and the API gateway.
 *
 * @doc.type module
 * @doc.purpose Auth request/response validation schemas
 * @doc.layer product
 * @doc.pattern ValueObject
 */

import { z } from "zod";
import { TenantIdSchema, UserRoleSchema } from "./common.js";

// ---------------------------------------------------------------------------
// Request schemas
// ---------------------------------------------------------------------------

export const LoginRequestSchema = z
  .object({
    email: z.string().email("Invalid email address"),
    password: z.string().min(8, "Password must be at least 8 characters"),
    tenantId: TenantIdSchema,
  })
  .strict();
export type LoginRequest = z.infer<typeof LoginRequestSchema>;

export const RefreshTokenRequestSchema = z
  .object({
    refreshToken: z.string().min(1, "refreshToken is required"),
  })
  .strict();
export type RefreshTokenRequest = z.infer<typeof RefreshTokenRequestSchema>;

export const ChangePasswordRequestSchema = z
  .object({
    currentPassword: z.string().min(1),
    newPassword: z
      .string()
      .min(12, "New password must be at least 12 characters")
      .regex(
        /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z0-9])/,
        "Password must include uppercase, lowercase, digit, and special character",
      ),
  })
  .strict();
export type ChangePasswordRequest = z.infer<typeof ChangePasswordRequestSchema>;

// ---------------------------------------------------------------------------
// Response schemas
// ---------------------------------------------------------------------------

export const LoginResponseSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string(),
  tokenType: z.literal("Bearer"),
  expiresIn: z.number().int().positive(),
});
export type LoginResponse = z.infer<typeof LoginResponseSchema>;

export const CurrentUserResponseSchema = z.object({
  id: z.string().uuid(),
  email: z.string().email(),
  displayName: z.string(),
  role: UserRoleSchema,
  tenantId: z.string(),
  isActive: z.boolean(),
  lastLoginAt: z.string().datetime({ offset: true }).optional(),
});
export type CurrentUserResponse = z.infer<typeof CurrentUserResponseSchema>;

// ---------------------------------------------------------------------------
// Validation helpers
// ---------------------------------------------------------------------------

/**
 * Validates a login request at the API boundary.
 * Throws `ZodError` on failure — callers should catch and return 422.
 */
export function parseLoginRequest(input: unknown): LoginRequest {
  return LoginRequestSchema.parse(input);
}

/**
 * Validates a refresh token request at the API boundary.
 */
export function parseRefreshTokenRequest(input: unknown): RefreshTokenRequest {
  return RefreshTokenRequestSchema.parse(input);
}
