/**
 * Shared utilities for file-backed lifecycle providers.
 *
 * @doc.type module
 * @doc.purpose Common utilities for file-backed bootstrap providers
 * @doc.layer kernel-providers
 * @doc.pattern Utility
 */

import * as fs from "node:fs/promises";
import * as path from "node:path";
import type { LifecycleProviderResult, LifecycleProviderWriteOptions } from "@ghatana/kernel-product-contracts";

/**
 * Atomic write: writes to temp file then renames for atomicity.
 */
export async function atomicWrite(filePath: string, content: string): Promise<void> {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
  await fs.writeFile(tempPath, content, "utf-8");
  await fs.rename(tempPath, filePath);
}

/**
 * Safe JSON read with error handling and default on ENOENT.
 */
export async function safeJsonRead<T>(filePath: string, defaultValue: T): Promise<T> {
  try {
    const content = await fs.readFile(filePath, "utf-8");
    return JSON.parse(content) as T;
  } catch (error) {
    if (isFileNotFound(error)) {
      return defaultValue;
    }
    throw error;
  }
}

/**
 * Check if error is file not found (ENOENT).
 */
export function isFileNotFound(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    (error as { readonly code?: unknown }).code === "ENOENT"
  );
}

/**
 * Create a failure result with appropriate message based on required flag.
 */
export function fail(message: string, required: boolean): LifecycleProviderResult {
  return {
    success: false,
    error: required ? message : `optional write skipped: ${message}`,
  };
}

/**
 * Create a success result with ref.
 */
export function succeed(ref: string): LifecycleProviderResult {
  return { success: true, ref };
}

/**
 * Encode a path segment safely for file paths.
 */
export function encodePathSegment(segment: string): string {
  return encodeURIComponent(segment.trim()).replace(/\./g, "%2E");
}

/**
 * Check if a string is a valid ISO timestamp.
 */
export function isIsoTimestamp(value: string): boolean {
  return !Number.isNaN(Date.parse(value));
}

/**
 * Enrich a record with privacy and retention metadata from write options.
 */
export function enrichWithMetadata<T extends Record<string, unknown>>(
  record: T,
  options: LifecycleProviderWriteOptions
): T {
  const enriched: Record<string, unknown> = { ...record };

  // Add correlationId if not present
  if ("correlationId" in enriched && typeof enriched.correlationId === "string" && enriched.correlationId === "") {
    enriched.correlationId = options.correlationId;
  }

  // Add privacy classification
  if (options.privacyClassification !== undefined) {
    if ("privacyClassification" in enriched && enriched.privacyClassification === undefined) {
      enriched.privacyClassification = options.privacyClassification;
    }
  }

  // Add retention metadata
  if (options.retention !== undefined) {
    if ("retention" in enriched && enriched.retention === undefined) {
      enriched.retention = options.retention;
    }
  }

  return enriched as T;
}

/**
 * Validate required string fields.
 */
export function validateRequiredStringFields(
  record: Record<string, unknown>,
  fieldNames: readonly string[]
): string[] {
  const errors: string[] = [];
  for (const fieldName of fieldNames) {
    const value = record[fieldName];
    if (typeof value !== "string" || value.trim().length === 0) {
      errors.push(`record requires ${fieldName}`);
    }
  }
  return errors;
}

/**
 * Validate non-empty string array fields.
 */
export function validateNonEmptyStringArray(
  record: Record<string, unknown>,
  fieldName: string
): string[] {
  const value = record[fieldName];
  if (!Array.isArray(value)) {
    return [`${fieldName} must be an array`];
  }
  if (value.some((item) => typeof item !== "string" || item.trim().length === 0)) {
    return [`${fieldName} must contain non-empty strings`];
  }
  return [];
}

/**
 * Validate retention metadata.
 */
export function validateRetentionMetadata(retention: unknown): string[] {
  const errors: string[] = [];
  if (typeof retention !== "object" || retention === null) {
    return [];
  }

  const ret = retention as Record<string, unknown>;
  if ("policyId" in ret) {
    if (typeof ret.policyId !== "string" || ret.policyId.trim().length === 0) {
      errors.push("retention requires policyId");
    }
  }
  if ("retentionDays" in ret) {
    if (!Number.isInteger(ret.retentionDays) || (ret.retentionDays as number) < 0) {
      errors.push("retentionDays must be non-negative integer");
    }
  }
  if ("expiresAt" in ret && ret.expiresAt !== undefined) {
    if (typeof ret.expiresAt !== "string" || !isIsoTimestamp(ret.expiresAt)) {
      errors.push("retention expiresAt must be ISO timestamp");
    }
  }
  return errors;
}

/**
 * Cleanup retained files based on retention policy.
 * Removes files older than retentionDays if specified.
 */
export async function cleanupRetainedFiles(
  directory: string,
  retentionDays?: number
): Promise<void> {
  try {
    const entries = await fs.readdir(directory, { withFileTypes: true });
    const now = Date.now();
    const retentionMs = retentionDays !== undefined ? retentionDays * 24 * 60 * 60 * 1000 : Infinity;

    for (const entry of entries) {
      if (!entry.isFile()) {
        continue;
      }

      const filePath = path.join(directory, entry.name);
      const stats = await fs.stat(filePath);
      const ageMs = now - stats.mtimeMs;

      if (ageMs > retentionMs) {
        await fs.unlink(filePath);
      }
    }
  } catch (error) {
    if (!isFileNotFound(error)) {
      throw error;
    }
  }
}

/**
 * Parse cursor string to number, defaulting to 0.
 */
export function parseCursor(cursor: string | undefined): number {
  if (cursor === undefined) {
    return 0;
  }
  const parsed = Number.parseInt(cursor, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
}
