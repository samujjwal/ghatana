/**
 * Plugin Policy - Safety and validation rules for plugin publishing.
 *
 * @doc.type module
 * @doc.purpose Define and enforce plugin security and compatibility policies
 * @doc.layer product
 * @doc.pattern Policy
 */

import { z } from "zod";
import type { PluginMetadata } from "@ghatana/tutorputor-sim-sdk";

// =============================================================================
// Constants
// =============================================================================

/** Maximum plugin bundle size in bytes (5MB) */
export const MAX_BUNDLE_SIZE = 5 * 1024 * 1024;

/** Maximum memory allocation per kernel (256MB) */
export const MAX_MEMORY_MB = 256;

/** Maximum execution time per step (10 seconds) */
export const MAX_EXECUTION_TIME_MS = 10_000;

/** Allowed plugin languages */
export const ALLOWED_LANGUAGES = [
  "typescript",
  "javascript",
  "wasm",
] as const;

/** Reserved plugin IDs that cannot be used */
export const RESERVED_IDS = [
  "core",
  "system",
  "builtin",
  "official",
  "tutorputor",
  "admin",
];

/** Prohibited dependencies that cannot be imported */
export const PROHIBITED_DEPENDENCIES = [
  "child_process",
  "cluster",
  "fs",
  "net",
  "dgram",
  "dns",
  "http2",
  "https",
  "http",
  "tls",
  "worker_threads",
];

// =============================================================================
// Validation Schemas
// =============================================================================

export const pluginIdSchema = z
  .string()
  .min(3, "Plugin ID must be at least 3 characters")
  .max(64, "Plugin ID must be at most 64 characters")
  .regex(
    /^[a-z0-9][a-z0-9-]*[a-z0-9]$/,
    "Plugin ID must start and end with alphanumeric characters and contain only lowercase letters, numbers, and hyphens"
  )
  .refine(
    (id) => !RESERVED_IDS.includes(id.toLowerCase()),
    "This plugin ID is reserved"
  );

export const semverSchema = z
  .string()
  .regex(
    /^\d+\.\d+\.\d+(-[a-zA-Z0-9]+(\.[a-zA-Z0-9]+)*)?(\+[a-zA-Z0-9]+(\.[a-zA-Z0-9]+)*)?$/,
    "Version must be a valid semver string"
  );

export const pluginMetadataSchema = z.object({
  id: pluginIdSchema,
  name: z.string().min(2).max(128),
  version: semverSchema,
  description: z.string().max(2048),
  author: z.string().min(1).max(256),
  license: z.string().optional(),
  repository: z.string().url().optional(),
  tags: z.array(z.string().max(32)).max(10).optional(),
});

export const resourceLimitsSchema = z.object({
  maxMemoryMB: z.number().int().min(16).max(MAX_MEMORY_MB).default(128),
  maxExecutionTimeMs: z.number().int().min(100).max(MAX_EXECUTION_TIME_MS).default(5000),
  maxBundleSizeBytes: z.number().int().min(1024).max(MAX_BUNDLE_SIZE).default(MAX_BUNDLE_SIZE),
});

export const pluginSubmissionSchema = z.object({
  metadata: pluginMetadataSchema,
  type: z.enum(["kernel", "promptPack", "visualizer"]),
  domain: z.string().min(1).max(64),
  language: z.enum(ALLOWED_LANGUAGES),
  bundleUrl: z.string().url().optional(),
  bundleBase64: z.string().optional(),
  sourceMapUrl: z.string().url().optional(),
  documentation: z.string().max(10_000).optional(),
  resourceLimits: resourceLimitsSchema.optional(),
  testManifestId: z.string().uuid().optional(),
});

// =============================================================================
// Types
// =============================================================================

export type PluginSubmission = z.infer<typeof pluginSubmissionSchema>;
export type ResourceLimits = z.infer<typeof resourceLimitsSchema>;
export type PluginType = "kernel" | "promptPack" | "visualizer";

export interface PolicyViolation {
  code: string;
  message: string;
  severity: "error" | "warning";
  location?: string;
}

export interface PolicyResult {
  passed: boolean;
  violations: PolicyViolation[];
  warnings: PolicyViolation[];
}

// =============================================================================
// Policy Checks
// =============================================================================

/**
 * Check if a plugin ID is valid.
 */
export function validatePluginId(id: string): PolicyResult {
  const violations: PolicyViolation[] = [];
  const warnings: PolicyViolation[] = [];

  const result = pluginIdSchema.safeParse(id);
  if (!result.success) {
    result.error.errors.forEach((err) => {
      violations.push({
        code: "INVALID_PLUGIN_ID",
        message: err.message,
        severity: "error",
      });
    });
  }

  return { passed: violations.length === 0, violations, warnings };
}

/**
 * Check plugin metadata for policy compliance.
 */
export function validateMetadata(metadata: PluginMetadata): PolicyResult {
  const violations: PolicyViolation[] = [];
  const warnings: PolicyViolation[] = [];

  const result = pluginMetadataSchema.safeParse(metadata);
  if (!result.success) {
    result.error.errors.forEach((err) => {
      violations.push({
        code: "INVALID_METADATA",
        message: `${err.path.join(".")}: ${err.message}`,
        severity: "error",
      });
    });
  }

  // Warnings for optional best practices
  if (!metadata.license) {
    warnings.push({
      code: "MISSING_LICENSE",
      message: "Consider adding a license to your plugin",
      severity: "warning",
    });
  }

  if (!metadata.repository) {
    warnings.push({
      code: "MISSING_REPOSITORY",
      message: "Consider linking to a source repository",
      severity: "warning",
    });
  }

  if (!metadata.tags || metadata.tags.length === 0) {
    warnings.push({
      code: "MISSING_TAGS",
      message: "Adding tags helps users discover your plugin",
      severity: "warning",
    });
  }

  return { passed: violations.length === 0, violations, warnings };
}

/**
 * Scan bundle code for prohibited dependencies.
 */
export function scanForProhibitedDependencies(
  code: string
): PolicyResult {
  const violations: PolicyViolation[] = [];
  const warnings: PolicyViolation[] = [];

  for (const dep of PROHIBITED_DEPENDENCIES) {
    // Check for require() and import statements
    const patterns = [
      new RegExp(`require\\s*\\(\\s*['"\`]${dep}['"\`]\\s*\\)`, "g"),
      new RegExp(`from\\s+['"\`]${dep}['"\`]`, "g"),
      new RegExp(`import\\s+['"\`]${dep}['"\`]`, "g"),
    ];

    for (const pattern of patterns) {
      const match = pattern.exec(code);
      if (match) {
        violations.push({
          code: "PROHIBITED_DEPENDENCY",
          message: `Use of prohibited dependency "${dep}" is not allowed`,
          severity: "error",
          location: `Character ${match.index}`,
        });
      }
    }
  }

  return { passed: violations.length === 0, violations, warnings };
}

/**
 * Validate resource limits.
 */
export function validateResourceLimits(
  limits: Partial<ResourceLimits>
): PolicyResult {
  const violations: PolicyViolation[] = [];
  const warnings: PolicyViolation[] = [];

  const result = resourceLimitsSchema.safeParse(limits);
  if (!result.success) {
    result.error.errors.forEach((err) => {
      violations.push({
        code: "INVALID_RESOURCE_LIMITS",
        message: `${err.path.join(".")}: ${err.message}`,
        severity: "error",
      });
    });
  }

  // Warnings for high resource usage
  if (limits.maxMemoryMB && limits.maxMemoryMB > 192) {
    warnings.push({
      code: "HIGH_MEMORY_USAGE",
      message: "High memory limit may affect performance on some devices",
      severity: "warning",
    });
  }

  if (limits.maxExecutionTimeMs && limits.maxExecutionTimeMs > 5000) {
    warnings.push({
      code: "HIGH_EXECUTION_TIME",
      message: "Long execution times may impact user experience",
      severity: "warning",
    });
  }

  return { passed: violations.length === 0, violations, warnings };
}

/**
 * Full policy check for plugin submission.
 */
export function validatePluginSubmission(
  submission: unknown
): PolicyResult {
  const allViolations: PolicyViolation[] = [];
  const allWarnings: PolicyViolation[] = [];

  // First validate the schema
  const schemaResult = pluginSubmissionSchema.safeParse(submission);
  if (!schemaResult.success) {
    schemaResult.error.errors.forEach((err) => {
      allViolations.push({
        code: "SCHEMA_VALIDATION_FAILED",
        message: `${err.path.join(".")}: ${err.message}`,
        severity: "error",
      });
    });
    return { passed: false, violations: allViolations, warnings: allWarnings };
  }

  const data = schemaResult.data;

  // Validate metadata
  const metadataResult = validateMetadata(data.metadata);
  allViolations.push(...metadataResult.violations);
  allWarnings.push(...metadataResult.warnings);

  // Validate resource limits if provided
  if (data.resourceLimits) {
    const limitsResult = validateResourceLimits(data.resourceLimits);
    allViolations.push(...limitsResult.violations);
    allWarnings.push(...limitsResult.warnings);
  }

  // Check bundle source
  if (!data.bundleUrl && !data.bundleBase64) {
    allViolations.push({
      code: "MISSING_BUNDLE",
      message: "Either bundleUrl or bundleBase64 must be provided",
      severity: "error",
    });
  }

  if (data.bundleUrl && data.bundleBase64) {
    allWarnings.push({
      code: "DUPLICATE_BUNDLE",
      message: "Both bundleUrl and bundleBase64 provided; bundleUrl will be used",
      severity: "warning",
    });
  }

  return {
    passed: allViolations.length === 0,
    violations: allViolations,
    warnings: allWarnings,
  };
}

/**
 * Check version compatibility.
 */
export function checkVersionCompatibility(
  currentVersion: string,
  newVersion: string
): PolicyResult {
  const violations: PolicyViolation[] = [];
  const warnings: PolicyViolation[] = [];

  // Import semver dynamically to avoid bundling issues
  const semver = require("semver");

  if (!semver.valid(currentVersion) || !semver.valid(newVersion)) {
    violations.push({
      code: "INVALID_VERSION",
      message: "Invalid semver version string",
      severity: "error",
    });
    return { passed: false, violations, warnings };
  }

  if (!semver.gt(newVersion, currentVersion)) {
    violations.push({
      code: "VERSION_NOT_INCREMENTED",
      message: `New version (${newVersion}) must be greater than current version (${currentVersion})`,
      severity: "error",
    });
  }

  // Warn about major version bumps
  if (semver.major(newVersion) > semver.major(currentVersion)) {
    warnings.push({
      code: "MAJOR_VERSION_BUMP",
      message: "Major version bump detected - this may break existing simulations",
      severity: "warning",
    });
  }

  return { passed: violations.length === 0, violations, warnings };
}

export default {
  validatePluginId,
  validateMetadata,
  validateResourceLimits,
  validatePluginSubmission,
  scanForProhibitedDependencies,
  checkVersionCompatibility,
  MAX_BUNDLE_SIZE,
  MAX_MEMORY_MB,
  MAX_EXECUTION_TIME_MS,
  ALLOWED_LANGUAGES,
  PROHIBITED_DEPENDENCIES,
};
