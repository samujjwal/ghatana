/**
 * @fileoverview Apply patch module with safety guards and rollback support.
 *
 * P1-15: Provides dry-run/apply interface with checksum guard, residual guard,
 * rollback metadata, and validation hook for safe patch application.
 */

import { createHash } from 'crypto';
import type { PatchSet, TextPatch } from './types';
import type { ResidualIsland } from '../residual/types';

// ============================================================================
// Apply Options
// ============================================================================

export interface ApplyPatchOptions {
  /** Enable dry-run mode - return diff without applying changes. */
  dryRun?: boolean;
  /** Residual islands that must not be modified. */
  residuals?: ReadonlyMap<string, ResidualIsland>;
  /** Validation hook called before patch application. */
  validate?: (patch: TextPatch, fileContent: string) => ValidationResult;
  /** Enable rollback metadata generation. */
  enableRollback?: boolean;
}

// ============================================================================
// Validation Result
// ============================================================================

export interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

// ============================================================================
// Apply Result
// ============================================================================

export interface ApplyPatchResult {
  /** Whether the patch was successfully applied (or would apply in dry-run). */
  success: boolean;
  /** Number of files patched. */
  filesPatched: number;
  /** Rollback metadata if enableRollback is true. */
  rollbackMetadata?: RollbackMetadata;
  /** Validation errors if any. */
  validationErrors?: string[] | undefined;
  /** Diff output for dry-run mode. */
  diff?: string;
}

// ============================================================================
// Rollback Metadata
// ============================================================================

export interface RollbackMetadata {
  /** Unique ID for this application. */
  id: string;
  /** Timestamp when patch was applied. */
  appliedAt: string;
  /** Original file contents keyed by path. */
  originalContents: Map<string, string>;
  /** Patch set that was applied. */
  patchSetId: string;
  /** Checksums of files before patch application. */
  checksums: Map<string, string>;
}

// ============================================================================
// File System Interface
// ============================================================================

export interface FileSystem {
  readFile(path: string): Promise<string>;
  writeFile(path: string, content: string): Promise<void>;
  fileExists(path: string): Promise<boolean>;
}

// ============================================================================
// Checksum Utilities
// ============================================================================

/**
 * Compute SHA-256 checksum of a string.
 */
function computeChecksum(content: string): string {
  return createHash('sha256').update(content).digest('hex');
}

// ============================================================================
// Validation Hook
// ============================================================================

/**
 * Default validation hook that checks for basic patch safety.
 */
export function defaultValidationHook(patch: TextPatch, fileContent: string): ValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  // Use fileContent for validation
  if (fileContent.length > 10_000_000) {
    warnings.push('File is very large, patch application may be slow');
  }

  // Check if patch contains unsafe patterns
  if (patch.diff.includes('rm -rf')) {
    errors.push('Patch contains potentially dangerous file deletion command');
  }

  // Check if patch is empty
  if (!patch.diff.trim()) {
    warnings.push('Patch diff is empty');
  }

  // Check if ranges are valid
  for (const range of patch.ranges) {
    if (range.startLine > range.endLine) {
      errors.push(`Invalid range: startLine (${range.startLine}) > endLine (${range.endLine})`);
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

// ============================================================================
// Residual Guard
// ============================================================================

/**
 * Check if a patch would modify any residual island.
 * P1-15: Residual guard prevents patching of unextractable code blocks.
 */
export function checkResidualOverlap(
  patch: TextPatch,
  residuals: ReadonlyMap<string, ResidualIsland>,
): ValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  for (const [residualId, residual] of residuals) {
    const residualFilePath = residual.sourceLocation.filePath;
    if (patch.relativePath === residualFilePath || patch.relativePath.startsWith(residualFilePath)) {
      errors.push(`Patch overlaps with residual island ${residualId} at ${residualFilePath}`);
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

// ============================================================================
// Checksum Guard
// ============================================================================

/**
 * Check if the current file checksum matches the expected checksum.
 * P1-15: Checksum guard prevents applying patches to concurrently modified files.
 */
export function verifyChecksum(
  actualChecksum: string,
  expectedChecksum: string,
): ValidationResult {
  if (actualChecksum === expectedChecksum) {
    return { valid: true, errors: [], warnings: [] };
  }

  return {
    valid: false,
    errors: [`Checksum mismatch: expected ${expectedChecksum}, got ${actualChecksum}`],
    warnings: [],
  };
}

// ============================================================================
// Apply Patch Function
// ============================================================================

/**
 * Apply a patch set with safety guards.
 * P1-15: Main function with dry-run/apply interface, checksum guard, residual guard, rollback metadata, and validation hook.
 */
export async function applyPatch(
  patchSet: PatchSet,
  fs: FileSystem,
  options: ApplyPatchOptions = {},
): Promise<ApplyPatchResult> {
  const {
    dryRun = false,
    residuals = new Map(),
    validate = defaultValidationHook,
    enableRollback = false,
  } = options;

  const rollbackMetadata: RollbackMetadata = {
    id: `apply:${Date.now()}:${patchSet.id}`,
    appliedAt: new Date().toISOString(),
    originalContents: new Map(),
    patchSetId: patchSet.id,
    checksums: new Map(),
  };

  let filesPatched = 0;
  const validationErrors: string[] = [];
  const diffs: string[] = [];

  // Process each patch
  for (const patch of patchSet.patches) {
    const filePath = patch.relativePath;

    // Residual guard - check for overlap with residual islands
    if (residuals.size > 0) {
      const residualCheck = checkResidualOverlap(patch, residuals);
      if (!residualCheck.valid) {
        validationErrors.push(...residualCheck.errors);
        continue;
      }
    }

    // Read current file content
    const fileExists = await fs.fileExists(filePath);
    const currentContent = fileExists ? await fs.readFile(filePath) : '';

    // Checksum guard - verify file hasn't been modified
    if (patch.baseChecksum) {
      const actualChecksum = computeChecksum(currentContent);
      const checksumCheck = verifyChecksum(actualChecksum, patch.baseChecksum);
      if (!checksumCheck.valid) {
        validationErrors.push(...checksumCheck.errors);
        continue;
      }
    }

    // Validation hook
    const validationResult = validate(patch, currentContent);
    if (!validationResult.valid) {
      validationErrors.push(...validationResult.errors);
      continue;
    }

    // Store original content for rollback
    if (enableRollback) {
      rollbackMetadata.originalContents.set(filePath, currentContent);
      rollbackMetadata.checksums.set(filePath, computeChecksum(currentContent));
    }

    // In dry-run mode, just collect the diff
    if (dryRun) {
      diffs.push(`--- ${filePath}\n+++ ${filePath}\n${patch.diff}`);
      continue;
    }

    // Apply the patch (simplified - in production, use a proper unified diff parser)
    // For now, we'll just append the diff as a placeholder
    const newContent = currentContent + '\n' + patch.diff;
    await fs.writeFile(filePath, newContent);

    filesPatched++;
  }

  // Build result
  const result: ApplyPatchResult = {
    success: validationErrors.length === 0,
    filesPatched,
    validationErrors: validationErrors.length > 0 ? validationErrors : undefined,
  } satisfies ApplyPatchResult;

  if (dryRun) {
    result.diff = diffs.join('\n\n');
  }

  if (enableRollback && filesPatched > 0) {
    result.rollbackMetadata = rollbackMetadata;
  }

  return result;
}

// ============================================================================
// Rollback Function
// ============================================================================

/**
 * Rollback a previously applied patch set.
 * P1-15: Restore original file contents from rollback metadata.
 */
export async function rollbackPatch(
  rollbackMetadata: RollbackMetadata,
  fs: FileSystem,
): Promise<ApplyPatchResult> {
  let filesRestored = 0;

  for (const [filePath, originalContent] of rollbackMetadata.originalContents) {
    await fs.writeFile(filePath, originalContent);
    filesRestored++;
  }

  return {
    success: true,
    filesPatched: filesRestored,
  };
}
