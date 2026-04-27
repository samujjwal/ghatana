/**
 * @tutorputor/validation — Common schemas
 *
 * Reusable Zod primitives and branded types shared by all domain schemas.
 *
 * @doc.type module
 * @doc.purpose Shared Zod primitives
 * @doc.layer product
 * @doc.pattern ValueObject
 */

import { z } from "zod";

// ---------------------------------------------------------------------------
// Branded ID types
// ---------------------------------------------------------------------------

export const TenantIdSchema = z.string().min(1, "tenantId is required").brand<"TenantId">();
export type TenantId = z.infer<typeof TenantIdSchema>;

export const UserIdSchema = z.string().uuid("userId must be a valid UUID").brand<"UserId">();
export type UserId = z.infer<typeof UserIdSchema>;

export const ModuleIdSchema = z.string().uuid("moduleId must be a valid UUID").brand<"ModuleId">();
export type ModuleId = z.infer<typeof ModuleIdSchema>;

export const EnrollmentIdSchema = z
  .string()
  .uuid("enrollmentId must be a valid UUID")
  .brand<"EnrollmentId">();
export type EnrollmentId = z.infer<typeof EnrollmentIdSchema>;

// ---------------------------------------------------------------------------
// Pagination
// ---------------------------------------------------------------------------

export const PaginationQuerySchema = z.object({
  cursor: z.string().optional(),
  limit: z.coerce.number().int().min(1).max(100).default(20),
});
export type PaginationQuery = z.infer<typeof PaginationQuerySchema>;

// ---------------------------------------------------------------------------
// Shared enums
// ---------------------------------------------------------------------------

export const DifficultySchema = z.enum(["INTRO", "INTERMEDIATE", "ADVANCED"]);
export type Difficulty = z.infer<typeof DifficultySchema>;

export const ModuleStatusSchema = z.enum(["DRAFT", "PUBLISHED", "ARCHIVED"]);
export type ModuleStatus = z.infer<typeof ModuleStatusSchema>;

export const UserRoleSchema = z.enum(["student", "teacher", "admin", "superadmin"]);
export type UserRole = z.infer<typeof UserRoleSchema>;

export const DomainSchema = z.enum([
  "MATH",
  "PHYSICS",
  "CHEMISTRY",
  "BIOLOGY",
  "ECONOMICS",
  "CS",
  "SCIENCE",
  "TECH",
]);
export type Domain = z.infer<typeof DomainSchema>;

// ---------------------------------------------------------------------------
// Reusable field schemas
// ---------------------------------------------------------------------------

/**
 * ISO 8601 date-time string.
 */
export const ISODateTimeSchema = z.string().datetime({ offset: true });

/**
 * A non-empty string that is trimmed before storage.
 */
export const NonEmptyStringSchema = z.string().trim().min(1);

/**
 * URL string — must be a valid HTTP/HTTPS URL.
 */
export const HttpUrlSchema = z.string().url().startsWith("http");

/**
 * A slug: lowercase alphanumeric with hyphens, no leading/trailing hyphens.
 */
export const SlugSchema = z
  .string()
  .regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/, "slug must be lowercase alphanumeric with hyphens");
