/**
 * @fileoverview PatchCoordinator — orchestrates emitters into a PatchSet.
 *
 * Accepts a list of ChangeOps + a list of PatchEmitters, routes each op to
 * capable emitters, collects TextPatch objects, and assembles a PatchSet.
 * Residual islands are explicitly preserved and never patched.
 */

import { randomUUID } from 'crypto';
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

export interface PatchCoordinatorOptions {
  /** Emitters to use (defaults to [ReactPatchEmitter]). */
  readonly emitters?: readonly PatchEmitter[];
  /**
   * Confidence threshold below which patches are flagged for human review
   * rather than auto-application. Defaults to 0.8.
   */
  readonly autoApplyThreshold?: number;
}

export class PatchCoordinator {
  private readonly emitters: readonly PatchEmitter[];
  private readonly autoApplyThreshold: number;

  constructor(options: PatchCoordinatorOptions = {}) {
    this.emitters = options.emitters ?? [new ReactPatchEmitter()];
    this.autoApplyThreshold = options.autoApplyThreshold ?? 0.8;
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
          console.error(`[PatchCoordinator] Emitter ${emitter.id} failed for op ${op.id}: ${msg}`);
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
   * Validate a change plan for structural integrity and residual overlaps.
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

    // Convert TextPatch to FilePatch for review bundle
    const filePatches: FilePatch[] = patchSet.patches.map(patch => ({
      id: randomUUID(),
      filePath: patch.relativePath,
      diff: patch.diff,
      sourceChangeId: patch.sourceChangeOpId,
      isAtomic: patch.isAtomic,
      canAutoApply: !patchSet.reviewRequiredPatches.includes(patch.sourceChangeOpId),
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
