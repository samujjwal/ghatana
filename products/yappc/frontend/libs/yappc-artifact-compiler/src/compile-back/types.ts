/**
 * @fileoverview Compile-back layer types.
 *
 * The compile-back layer safely converts YAPPC SemanticProductModel changes back
 * into source code patches. It operates in three steps:
 *
 *   1. Change Plan    — diff the before/after SemanticProductModel, produce typed ChangeOp[]
 *   2. Patch Set      — each ChangeOp is expanded into file-level TextPatch[] by emitters
 *   3. Application    — patches are applied to the working tree (or returned as dry-run diffs)
 *
 * Key invariants:
 *   - Residual islands are NEVER patched; they are preserved verbatim.
 *   - Every patch carries its source ChangeOp for auditability.
 *   - The emitter chain is open/closed: new languages add emitters, core types don't change.
 */

import { z } from 'zod';
import type { SemanticModelElement } from '../model/types';
import type { ResidualIsland } from '../residual/types';

// ============================================================================
// Change Operations
// ============================================================================

export const ChangeOpKindSchema = z.enum([
  'add-component',
  'remove-component',
  'update-component-props',
  'add-prop',
  'remove-prop',
  'update-prop-type',
  'add-event',
  'remove-event',
  'add-slot',
  'remove-slot',
  'rename-component',
  'update-accessibility',
  'add-variant',
  'remove-variant',
]);

export type ChangeOpKind = z.infer<typeof ChangeOpKindSchema>;

export const ChangeOpSchema = z.object({
  id: z.string().min(1),
  kind: ChangeOpKindSchema,
  targetElementId: z.string().min(1),
  /** Human-readable description of the change for audit logs. */
  description: z.string().min(1),
  /** Before state — undefined for 'add' operations. */
  before: z.unknown().optional(),
  /** After state — undefined for 'remove' operations. */
  after: z.unknown().optional(),
  /**
   * Confidence that the patch can be auto-applied safely (0–1).
   * Low-confidence patches are flagged for human review.
   */
  autoApplyConfidence: z.number().min(0).max(1),
});

export type ChangeOp = z.infer<typeof ChangeOpSchema>;

// ============================================================================
// Text Patch
// ============================================================================

export const TextPatchSchema = z.object({
  /** Relative path of the file to patch. */
  relativePath: z.string().min(1),
  /** The patch content in unified diff format. */
  diff: z.string().min(1),
  /** Whether this patch can be applied atomically without conflicts. */
  isAtomic: z.boolean().default(true),
  /** Source ChangeOp that generated this patch. */
  sourceChangeOpId: z.string().min(1),
  /** Emitter that produced this patch. */
  emitterId: z.string().min(1),
});

export type TextPatch = z.infer<typeof TextPatchSchema>;

// ============================================================================
// Patch Set
// ============================================================================

export const PatchSetSchema = z.object({
  id: z.string().min(1),
  createdAt: z.string().datetime(),
  changeOps: z.array(ChangeOpSchema),
  patches: z.array(TextPatchSchema),
  /** Residual islands that were NOT patched and must be preserved verbatim. */
  preservedResiduals: z.array(z.string()).default([]),
  /** Patches that require human review before application. */
  reviewRequiredPatches: z.array(z.string()).default([]),
  stats: z.object({
    totalChangeOps: z.number().int().nonnegative(),
    totalPatches: z.number().int().nonnegative(),
    autoApplicable: z.number().int().nonnegative(),
    requiresReview: z.number().int().nonnegative(),
    preservedResiduals: z.number().int().nonnegative(),
  }),
});

export type PatchSet = z.infer<typeof PatchSetSchema>;

// ============================================================================
// Patch Emitter Interface
// ============================================================================

/**
 * A PatchEmitter translates a ChangeOp into one or more TextPatch objects.
 * Each emitter handles a specific language/framework/kind combination.
 */
export interface PatchEmitter {
  readonly id: string;
  readonly version: string;
  /**
   * Returns true if this emitter can handle the given ChangeOp in the given
   * element's context (e.g. React component, Prisma model, workflow YAML).
   */
  canEmit(op: ChangeOp, element: SemanticModelElement): boolean;
  /**
   * Emit patches for the given ChangeOp.
   * Must not throw — return empty array if the op cannot be emitted.
   */
  emit(op: ChangeOp, element: SemanticModelElement, context: PatchContext): TextPatch[];
}

// ============================================================================
// Patch Context
// ============================================================================

export interface PatchContext {
  /** Read the current contents of a file by relative path. */
  readFile(relativePath: string): Promise<string>;
  /** Returns true if the file exists in the working tree. */
  fileExists(relativePath: string): Promise<boolean>;
  /**
   * Residual islands keyed by element ID.
   * Emitters must check this before patching — residuals are never touched.
   */
  readonly residuals: ReadonlyMap<string, ResidualIsland>;
  /** Source paths of the elements being changed, for locating files. */
  readonly elementSourcePaths: ReadonlyMap<string, readonly string[]>;
}

// ============================================================================
// Change Plan Builder
// ============================================================================

/**
 * Diff two SemanticModelElement arrays (before/after a canvas edit) and produce
 * a list of typed ChangeOps.
 */
export function buildChangePlan(
  before: readonly SemanticModelElement[],
  after: readonly SemanticModelElement[],
): ChangeOp[] {
  const ops: ChangeOp[] = [];
  const beforeMap = new Map(before.map(e => [e.id, e]));
  const afterMap = new Map(after.map(e => [e.id, e]));

  // Detect removals
  for (const [id, elem] of beforeMap) {
    if (!afterMap.has(id)) {
      ops.push({
        id: `op:remove:${id}`,
        kind: 'remove-component',
        targetElementId: id,
        description: `Remove component "${elem.name}"`,
        before: elem,
        autoApplyConfidence: 0.7,
      });
    }
  }

  // Detect additions
  for (const [id, elem] of afterMap) {
    if (!beforeMap.has(id)) {
      ops.push({
        id: `op:add:${id}`,
        kind: 'add-component',
        targetElementId: id,
        description: `Add component "${elem.name}"`,
        after: elem,
        autoApplyConfidence: 0.9,
      });
    }
  }

  // Detect updates
  for (const [id, afterElem] of afterMap) {
    const beforeElem = beforeMap.get(id);
    if (!beforeElem) continue;
    if (beforeElem === afterElem) continue;

    // Deep-equality check via JSON serialization (sufficient for model elements)
    if (JSON.stringify(beforeElem) === JSON.stringify(afterElem)) continue;

    if (beforeElem.name !== afterElem.name) {
      ops.push({
        id: `op:rename:${id}`,
        kind: 'rename-component',
        targetElementId: id,
        description: `Rename component "${beforeElem.name}" → "${afterElem.name}"`,
        before: beforeElem.name,
        after: afterElem.name,
        autoApplyConfidence: 0.85,
      });
    }

    // Kind-specific prop diffing for ComponentModel
    if (beforeElem.kind === 'component' && afterElem.kind === 'component') {
      const beforeProps = (beforeElem as { props?: unknown[] }).props ?? [];
      const afterProps = (afterElem as { props?: unknown[] }).props ?? [];
      if (JSON.stringify(beforeProps) !== JSON.stringify(afterProps)) {
        ops.push({
          id: `op:update-props:${id}`,
          kind: 'update-component-props',
          targetElementId: id,
          description: `Update props of component "${afterElem.name}"`,
          before: beforeProps,
          after: afterProps,
          autoApplyConfidence: 0.75,
        });
      }
    }
  }

  return ops;
}
