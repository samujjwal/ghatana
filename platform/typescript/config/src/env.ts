/**
 * @ghatana/config — environment variable loading and validation.
 *
 * Validates `process.env` against a Zod schema at startup.
 * Fails fast on missing/invalid environment variables rather than propagating
 * undefined values into runtime behaviour.
 *
 * @example
 * const envSchema = z.object({
 *   NODE_ENV: z.enum(["development", "production", "test"]),
 *   API_BASE_URL: z.string().url(),
 *   PORT: z.coerce.number().int().positive().default(3000),
 * });
 * export const env = loadEnv(envSchema);
 */

import { z, ZodError, ZodObject, ZodRawShape } from "zod";
import { ConfigValidationError } from "./schema.js";

// ─────────────────────────────────────────
// loadEnv
// ─────────────────────────────────────────

/**
 * Parse and validate environment variables against a Zod schema.
 *
 * Only properties declared in the schema are included in the result —
 * unknown environment variables are stripped for security.
 *
 * Throws `ConfigValidationError` if validation fails.
 */
export function loadEnv<S extends ZodObject<ZodRawShape>>(
  schema: S,
  env: Record<string, string | undefined> = process.env
): z.infer<S> {
  try {
    return schema.parse(env) as z.infer<S>;
  } catch (err) {
    if (err instanceof ZodError) {
      const lines = err.issues.map(
        (issue) =>
          `  ${issue.path.join(".") || "(root)"}: ${issue.message}`
      );
      throw new ConfigValidationError(
        `Environment validation failed. Check the following variables:\n${lines.join("\n")}`,
        err
      );
    }
    throw err;
  }
}

// ─────────────────────────────────────────
// Common environment schemas
// ─────────────────────────────────────────

/**
 * Canonical NODE_ENV values for the Ghatana platform.
 */
export const NodeEnvSchema = z.enum(["development", "production", "test"]);
export type NodeEnv = z.infer<typeof NodeEnvSchema>;

/**
 * Shared base environment schema applicable to all Ghatana services.
 *
 * Extend this with service-specific variables:
 * @example
 * const MyServiceEnv = BaseEnvSchema.extend({
 *   DATABASE_URL: z.string().url(),
 * });
 * export const env = loadEnv(MyServiceEnv);
 */
export const BaseEnvSchema = z.object({
  NODE_ENV: NodeEnvSchema.default("development"),
  LOG_LEVEL: z.enum(["error", "warn", "info", "debug"]).default("info"),
});

export type BaseEnv = z.infer<typeof BaseEnvSchema>;
