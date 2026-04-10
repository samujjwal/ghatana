/**
 * @ghatana/config — configuration schema utilities.
 *
 * Provides typed wrappers for building configuration schemas with Zod,
 * with structured validation errors for clear runtime diagnostics.
 */

import { z, ZodError, ZodTypeAny } from "zod";

// ─────────────────────────────────────────
// Types
// ─────────────────────────────────────────

/**
 * A typed configuration object with get/set/validate semantics.
 */
export interface Config<T> {
  /** Return the full resolved configuration. */
  get(): T;
  /** Return a single top-level key. */
  getKey<K extends keyof T>(key: K): T[K];
  /** Validate the current configuration. Throws ConfigValidationError if invalid. */
  validate(): void;
}

/**
 * Error thrown when configuration validation fails.
 */
export class ConfigValidationError extends Error {
  public readonly issues: ZodError["issues"];

  constructor(message: string, cause: ZodError) {
    super(message);
    this.name = "ConfigValidationError";
    this.issues = cause.issues;
  }
}

// ─────────────────────────────────────────
// createConfig
// ─────────────────────────────────────────

/**
 * Create a typed configuration object from a Zod schema and raw input.
 *
 * Validates at construction time — throws `ConfigValidationError` if the
 * input does not satisfy the schema.
 *
 * @example
 * const AppConfig = z.object({ port: z.number().positive(), debug: z.boolean() });
 * const config = createConfig(AppConfig, { port: 3000, debug: false });
 * config.get().port; // 3000
 */
export function createConfig<S extends ZodTypeAny>(
  schema: S,
  input: unknown
): Config<z.output<S>> {
  let parsed: z.output<S>;
  try {
    parsed = schema.parse(input) as z.output<S>;
  } catch (err) {
    if (err instanceof ZodError) {
      throw new ConfigValidationError(
        `Configuration validation failed:\n${formatZodError(err)}`,
        err
      );
    }
    throw err;
  }

  return {
    get() {
      return parsed;
    },
    getKey<K extends keyof z.output<S>>(key: K): z.output<S>[K] {
      return parsed[key];
    },
    validate() {
      try {
        schema.parse(parsed);
      } catch (err) {
        if (err instanceof ZodError) {
          throw new ConfigValidationError(
            `Configuration validation failed:\n${formatZodError(err)}`,
            err
          );
        }
        throw err;
      }
    },
  };
}

// ─────────────────────────────────────────
// Common schema builders
// ─────────────────────────────────────────

/**
 * Zod schema for a non-empty string (trims whitespace).
 */
export const nonEmptyString = (): z.ZodString =>
  z.string().min(1, "Value must not be empty").trim();

/**
 * Zod schema for a URL string.
 */
export const urlString = (): z.ZodString => z.string().url();

/**
 * Zod schema for a positive integer.
 */
export const positiveInt = (): z.ZodNumber =>
  z.number().int().positive();

/**
 * Zod schema for a port number (1–65535).
 */
export const portNumber = (): z.ZodNumber =>
  z.number().int().min(1).max(65535);

// ─────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────

function formatZodError(err: ZodError): string {
  return err.issues
    .map((issue) => `  [${issue.path.join(".")}] ${issue.message}`)
    .join("\n");
}
