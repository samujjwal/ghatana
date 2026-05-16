/**
 * @fileoverview PatchCoordinator — orchestrates emitters into a PatchSet.
 *
 * Accepts a list of ChangeOps + a list of PatchEmitters, routes each op to
 * capable emitters, collects TextPatch objects, and assembles a PatchSet.
 * Residual islands are explicitly preserved and never patched.
 */

import { randomUUID } from 'crypto';
import type { ChangeOp, PatchEmitter, TextPatch, PatchSet, PatchContext } from './types';
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
   * Compile ChangeOps into a PatchSet.
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
}
