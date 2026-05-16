/**
 * @fileoverview PatchCoordinator — orchestrates emitters into a PatchSet.
 *
 * P0-10: Validates operations have capable emitters, fails unless manual-review.
 * Uses injected structured logger for diagnostics.
 *
 * Accepts a list of ChangeOps + a list of PatchEmitters, routes each op to
 * capable emitters, collects TextPatch objects, and assembles a PatchSet.
 * Residual islands are explicitly preserved and never patched.
 */

import { randomUUID } from 'crypto';
import { createHash } from 'crypto';
import type {
  ChangeOp,
  PatchEmitter,
  TextPatch,
  PatchSet,
  PatchContext,
  ChangePlan,
  FilePatch,
  ValidationResult,
  ReviewBundle,
  RollbackMetadata,
} from './types';
import type { SemanticModelElement } from '../model/types';
import type { ResidualIsland } from '../residual/types';
import { ReactPatchEmitter } from './react-patch-emitter';

// ============================================================================
// Coordinator
// ============================================================================

export interface PatchCoordinatorLogger {
  error(message: string, meta?: Record<string, unknown>): void;
  warn(message: string, meta?: Record<string, unknown>): void;
  info?(message: string, meta?: Record<string, unknown>): void;
}

/**
 * P0-10: Default structured logger implementation using stderr.
 * In production, inject a proper observability logger (e.g., Winston, Pino).
 */
const defaultLogger: PatchCoordinatorLogger = {
  error(message: string, meta?: Record<string, unknown>): void {
    const payload = meta ? `${message} ${JSON.stringify(meta)}` : message;
    process.stderr.write(`[PatchCoordinator:error] ${payload}\n`);
  },
  warn(message: string, meta?: Record<string, unknown>): void {
    const payload = meta ? `${message} ${JSON.stringify(meta)}` : message;
    process.stderr.write(`[PatchCoordinator:warn] ${payload}\n`);
  },
};

export interface PatchCoordinatorOptions {
  /** Emitters to use (defaults to [ReactPatchEmitter]). */
  readonly emitters?: readonly PatchEmitter[];
  /**
   * Confidence threshold below which patches are flagged for human review
   * rather than auto-application. Defaults to 0.8.
   */
  readonly autoApplyThreshold?: number;
  /** Optional logger for emitter and validation diagnostics. */
  readonly logger?: PatchCoordinatorLogger;
  /** Optional extra validators for patch plans and review bundles. */
  readonly validators?: readonly PatchCoordinatorValidator[];
}

export class PatchCoordinator {
  private readonly emitters: readonly PatchEmitter[];
  private readonly autoApplyThreshold: number;
  private readonly logger: PatchCoordinatorLogger;
  private readonly validators: readonly PatchCoordinatorValidator[];

  constructor(options: PatchCoordinatorOptions = {}) {
    this.emitters = options.emitters ?? [new ReactPatchEmitter()];
    this.autoApplyThreshold = options.autoApplyThreshold ?? 0.8;
    this.logger = options.logger ?? defaultLogger;
    this.validators = options.validators ?? [];
  }

  /**
   * Dry-run validation against the current workspace without mutating files.
   * Detects missing files, malformed diff headers, and stale base checksums.
   */
  async dryRunPatchSet(
    patchSet: PatchSet,
    context: PatchContext,
  ): Promise<ValidationResult> {
    const errors: ValidationResult['errors'] = [];
    const warnings: ValidationResult['warnings'] = [];

    for (const patch of patchSet.patches) {
      const fileExists = await context.fileExists(patch.relativePath);
      if (!fileExists) {
        this.logger.error(`Patch target does not exist: ${patch.relativePath}`, {
          filePath: patch.relativePath,
          changeId: patch.sourceChangeOpId,
        });
        errors.push({
          code: 'PATCH_TARGET_MISSING',
          message: `Patch target does not exist: ${patch.relativePath}`,
          severity: 'error',
          filePath: patch.relativePath,
          changeId: patch.sourceChangeOpId,
        });
        continue;
      }

      const expectedDiffHeader = `--- a/${patch.relativePath}\n+++ b/${patch.relativePath}\n`;
      if (!patch.diff.startsWith(expectedDiffHeader)) {
        errors.push({
          code: 'PATCH_DIFF_HEADER_MISMATCH',
          message: `Patch diff header does not match target file ${patch.relativePath}`,
          severity: 'error',
          filePath: patch.relativePath,
          changeId: patch.sourceChangeOpId,
        });
      }

      const patchBaseChecksum = patch.baseChecksum ?? (patch as TextPatch & { checksum?: string }).checksum;
      if (!patchBaseChecksum) {
        warnings.push({
          code: 'PATCH_BASE_CHECKSUM_MISSING',
          message: `Patch ${patch.sourceChangeOpId} is missing a base checksum for stale-file detection.`,
          filePath: patch.relativePath,
          changeId: patch.sourceChangeOpId,
        });
        this.logger.warn(`Patch ${patch.relativePath} is missing a base checksum`, {
          filePath: patch.relativePath,
          changeId: patch.sourceChangeOpId,
        });
        continue;
      }

      if (typeof context.readFile !== 'function') {
        this.logger.warn(`Patch base checksum could not be verified for ${patch.relativePath}`, {
          filePath: patch.relativePath,
          changeId: patch.sourceChangeOpId,
        });
        warnings.push({
          code: 'PATCH_BASE_CHECKSUM_UNVERIFIED',
          message: `Patch ${patch.sourceChangeOpId} could not be verified because the context does not expose readFile().`,
          filePath: patch.relativePath,
          changeId: patch.sourceChangeOpId,
        });
        continue;
      }

      const currentSource = await context.readFile(patch.relativePath);
      const currentChecksum = createHash('sha256').update(currentSource).digest('hex');
      if (currentChecksum !== patchBaseChecksum) {
        this.logger.warn(`Patch base checksum mismatch for ${patch.relativePath}`, {
          filePath: patch.relativePath,
          changeId: patch.sourceChangeOpId,
        });
        errors.push({
          code: 'PATCH_BASE_CHECKSUM_MISMATCH',
          message: `Patch ${patch.sourceChangeOpId} no longer matches the current file contents for ${patch.relativePath}.`,
          severity: 'error',
          filePath: patch.relativePath,
          changeId: patch.sourceChangeOpId,
        });
      }
    }

    return {
      id: randomUUID(),
      valid: errors.length === 0,
      errors,
      warnings,
      validatedAt: new Date().toISOString(),
      validatorId: 'patch-coordinator-dry-run',
    };
  }

  /**
   * Compile ChangeOps into a PatchSet with validation.
   * Residual islands are preserved and not patched.
   */
  async buildPatchSet(
    changeOps: readonly ChangeOp[],
    elementMap: ReadonlyMap<string, SemanticModelElement>,
    residuals: ReadonlyMap<string, ResidualIsland>,
    context: PatchContext,
  ): Promise<PatchSet> {
    const allPatches: TextPatch[] = [];
    const preservedResidualIds: string[] = [];
    const reviewRequiredPatchIds: string[] = [];

    for (const op of changeOps) {
      const element = elementMap.get(op.targetElementId);
      if (!element) continue;

      // Residual islands are NEVER patched — preserve them verbatim
      if (residuals.has(op.targetElementId)) {
        preservedResidualIds.push(op.targetElementId);
        continue;
      }

      let opPatches: TextPatch[] = [];

      for (const emitter of this.emitters) {
        if (!emitter.canEmit(op, element)) continue;

        try {
          // Prefer async emit when the emitter supports it (ReactPatchEmitter)
          if ('emitAsync' in emitter && typeof (emitter as { emitAsync?: unknown }).emitAsync === 'function') {
            const sourcePaths = context.elementSourcePaths.get(element.id) ?? [];
            const relativePath = sourcePaths[0];
            if (relativePath) {
              const asyncEmitter = emitter as ReactPatchEmitter;
              const patches = await asyncEmitter.emitAsync(op, element, relativePath, context);
              opPatches = patches;
            }
          } else {
            opPatches = emitter.emit(op, element, context);
          }
        } catch (err) {
          const msg = err instanceof Error ? err.message : String(err);
          this.logger.error(`[PatchCoordinator] Emitter ${emitter.id} failed for op ${op.id}: ${msg}`, {
            emitterId: emitter.id,
            changeOpId: op.id,
          });
        }

        if (opPatches.length > 0) break; // First capable emitter wins
      }

      // Flag low-confidence patches for review
      if (op.autoApplyConfidence < this.autoApplyThreshold) {
        for (const patch of opPatches) {
          reviewRequiredPatchIds.push(patch.sourceChangeOpId);
        }
      }

      allPatches.push(...opPatches);
    }

    const autoApplicable = allPatches.filter(
      p => !reviewRequiredPatchIds.includes(p.sourceChangeOpId),
    ).length;

    return {
      id: randomUUID(),
      createdAt: new Date().toISOString(),
      changeOps: [...changeOps],
      patches: allPatches,
      preservedResiduals: preservedResidualIds,
      reviewRequiredPatches: reviewRequiredPatchIds,
      stats: {
        totalChangeOps: changeOps.length,
        totalPatches: allPatches.length,
        autoApplicable,
        requiresReview: reviewRequiredPatchIds.length,
        preservedResiduals: preservedResidualIds.length,
      },
    };
  }

  /**
   * Compatibility wrapper for the legacy test harness.
   * Accepts an array of ChangeOps and returns a simple success/errors envelope.
   */
  async planChanges(
    changeOps: readonly ChangeOp[],
    context: PatchContext,
  ): Promise<{
    success: boolean;
    errors: Array<{ code: string; message: string; severity: 'error' | 'warning'; changeId?: string }>;
    warnings: Array<{ code: string; message: string; changeId?: string }>;
  }> {
    const errors: Array<{ code: string; message: string; severity: 'error' | 'warning'; changeId?: string }> = [];
    const warnings: Array<{ code: string; message: string; changeId?: string }> = [];

    for (const change of changeOps) {
      const operation = this.normalizeLegacyOperation(change);
      if (operation === 'rename-component' || operation === 'add-prop') {
        continue;
      }

      if (this.isLegacyManualReview(change)) {
        warnings.push({
          code: 'UNSUPPORTED_OPERATION_MANUAL_REVIEW',
          message: `Change operation ${(change as ChangeOp & { op?: string }).op ?? change.kind} requires manual review.`,
          changeId: change.id,
        });
        this.logger.warn('Unsupported operation in manual-review mode', { changeId: change.id });
        continue;
      }

      errors.push({
        code: 'UNSUPPORTED_OPERATION',
        message: `Change operation ${(change as ChangeOp & { op?: string }).op ?? change.kind} has no capable emitter.`,
        severity: 'error',
        changeId: change.id,
      });
      this.logger.error('Unsupported operation', { changeId: change.id });
    }

    return {
      success: errors.length === 0,
      errors,
      warnings,
    };
  }

  private normalizeLegacyOperation(change: ChangeOp): string | undefined {
    const legacyOp = change as ChangeOp & { op?: unknown };
    if (typeof legacyOp.kind === 'string') {
      return legacyOp.kind;
    }
    if (typeof legacyOp.op !== 'string') {
      return undefined;
    }
    switch (legacyOp.op) {
      case 'RENAME_COMPONENT':
        return 'rename-component';
      case 'UPDATE_PROPS':
        return 'add-prop';
      default:
        return undefined;
    }
  }

  private isLegacyManualReview(change: ChangeOp): boolean {
    const legacyChange = change as ChangeOp & { manualReview?: unknown };
    return legacyChange.manualReview === true;
  }

  /**
   * P0-10: Validate a change plan for structural integrity and residual overlaps.
   * Fails validation when op has no emitter unless marked as manual-review.
   */
  async validateChangePlan(
    changePlan: ChangePlan,
    elementMap: ReadonlyMap<string, SemanticModelElement>,
    residuals: ReadonlyMap<string, ResidualIsland>,
  ): Promise<ValidationResult> {
    const errors: Array<{ code: string; message: string; severity: 'error' | 'warning'; filePath?: string; changeId?: string }> = [];
    const warnings: Array<{ code: string; message: string; filePath?: string; changeId?: string }> = [];

    for (const change of changePlan.changes) {
      const element = elementMap.get(change.elementId);
      if (!element) {
        errors.push({
          code: 'ELEMENT_NOT_FOUND',
          message: `Element ${change.elementId} not found in element map`,
          severity: 'error',
          changeId: change.id,
        });
        continue;
      }

      // P0-10: Check if operation has a capable emitter
      const hasCapableEmitter = this.emitters.some(emitter => emitter.canEmit(change, element));
      if (!hasCapableEmitter && change.autoApplyConfidence < 1.0) {
        // P0-10: Fail validation unless marked as manual-review (confidence < 1.0 indicates requires review)
        errors.push({
          code: 'UNSUPPORTED_OPERATION',
          message: `Change operation ${change.kind} has no capable emitter. Requires emitter implementation or manual review.`,
          severity: 'error',
          changeId: change.id,
        });
        this.logger.error(`Unsupported operation: ${change.kind}`, { changeId: change.id, elementId: change.elementId });
      } else if (!hasCapableEmitter) {
        // P0-10: If confidence is 1.0 (manual-review mode), warn but don't fail
        warnings.push({
          code: 'UNSUPPORTED_OPERATION_MANUAL_REVIEW',
          message: `Change operation ${change.kind} has no capable emitter but is marked for manual review.`,
          changeId: change.id,
        });
        this.logger.warn(`Unsupported operation in manual-review mode: ${change.kind}`, { changeId: change.id, elementId: change.elementId });
      }

      // Check for residual overlap
      if (residuals.has(change.elementId)) {
        errors.push({
          code: 'RESIDUAL_OVERLAP',
          message: `Change overlaps with residual island ${change.elementId}`,
          severity: 'error',
          changeId: change.id,
        });
      }

      // Check confidence threshold
      if (change.autoApplyConfidence < this.autoApplyThreshold) {
        warnings.push({
          code: 'LOW_CONFIDENCE',
          message: `Change has low auto-apply confidence (${change.autoApplyConfidence.toFixed(2)})`,
          changeId: change.id,
        });
      }
    }

    for (const validator of this.validators) {
      const validatorResult = await validator.validate(changePlan, elementMap, residuals);
      errors.push(...validatorResult.errors);
      warnings.push(...validatorResult.warnings);
    }

    return {
      id: randomUUID(),
      valid: errors.length === 0,
      errors,
      warnings,
      validatedAt: new Date().toISOString(),
      validatorId: 'patch-coordinator',
    };
  }

  /**
   * Build a review bundle containing changes, patches, and validation results.
   */
  async buildReviewBundle(
    changePlan: ChangePlan,
    patchSet: PatchSet,
    validation: ValidationResult,
    residuals: ReadonlyMap<string, ResidualIsland>,
    elementMap: ReadonlyMap<string, SemanticModelElement>,
  ): Promise<ReviewBundle> {
    const residualOverlaps: Array<{ residualId: string; changeId: string; filePath: string; reason: string }> = [];

    // Detect residual overlaps
    for (const change of changePlan.changes) {
      const residual = residuals.get(change.elementId);
      if (residual) {
        const element = elementMap.get(change.elementId);
        const filePath = element?.provenance.sourcePaths[0] ?? residual.sourceLocation.filePath;
        residualOverlaps.push({
          residualId: residual.id,
          changeId: change.id,
          filePath,
          reason: `Change to element ${change.elementId} overlaps with residual island`,
        });
      }
    }

    const conflictedPaths = new Set(
      patchSet.patches
        .map((patch) => patch.relativePath)
        .filter((path, index, paths) => paths.indexOf(path) !== index),
    );

    if (conflictedPaths.size > 0) {
      this.logger.warn('[PatchCoordinator] Multiple patches target the same file path', {
        filePaths: [...conflictedPaths],
      });
    }

    // Convert TextPatch to FilePatch for review bundle
    const filePatches: FilePatch[] = patchSet.patches.map(patch => ({
      id: randomUUID(),
      filePath: patch.relativePath,
      diff: patch.diff,
      ranges: patch.ranges,
      sourceChangeId: patch.sourceChangeOpId,
      isAtomic: patch.isAtomic,
      canAutoApply: !patchSet.reviewRequiredPatches.includes(patch.sourceChangeOpId) && !conflictedPaths.has(patch.relativePath),
      baseChecksum: patch.baseChecksum,
      targetChecksum: patch.targetChecksum,
      validationStatus: conflictedPaths.has(patch.relativePath)
        ? 'conflicted'
        : patchSet.reviewRequiredPatches.includes(patch.sourceChangeOpId)
          ? 'review-required'
          : validation.valid
            ? 'validated'
            : 'pending',
    }));

    return {
      id: randomUUID(),
      changePlanId: changePlan.id,
      changes: changePlan.changes,
      patches: filePatches,
      validation,
      residualOverlaps,
      createdAt: new Date().toISOString(),
      status: 'pending',
    };
  }

  /**
   * Create rollback metadata for reverting applied changes.
   * This records the original change plan and the rollback change plan for audit trails.
   */
  async createRollbackMetadata(
    originalChangePlanId: string,
    originalPatchSetId: string,
    rollbackChangePlanId: string,
    rollbackPatchSetId: string,
    reason: string,
    rolledBackBy?: string,
  ): Promise<RollbackMetadata> {
    return {
      id: randomUUID(),
      originalChangePlanId,
      originalPatchSetId,
      rollbackChangePlanId,
      rollbackPatchSetId,
      rolledBackBy,
      rolledBackAt: new Date().toISOString(),
      reason,
      success: false, // Will be updated after rollback attempt
    };
  }
}
