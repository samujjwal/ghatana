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
  /**
   * P0-10: Operation that has no capable emitter and cannot be applied automatically.
   * Requires manual review or emitter implementation.
   */
  'unsupported-operation',
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

export const PatchRangeSchema = z.object({
  startLine: z.number().int().nonnegative(),
  startColumn: z.number().int().nonnegative(),
  endLine: z.number().int().nonnegative(),
  endColumn: z.number().int().nonnegative(),
  nodeType: z.string().optional(),
});

export type PatchRange = z.infer<typeof PatchRangeSchema>;

export const PatchValidationStatusSchema = z.enum([
  'pending',
  'validated',
  'review-required',
  'conflicted',
]);

export type PatchValidationStatus = z.infer<typeof PatchValidationStatusSchema>;

// ============================================================================
// Text Patch
// ============================================================================

export const TextPatchSchema = z.object({
  /** Relative path of the file to patch. */
  relativePath: z.string().min(1),
  /** The patch content in unified diff format. */
  diff: z.string().min(1),
  /** AST/range metadata captured at emission time. */
  ranges: z.array(PatchRangeSchema).default([]),
  /** Whether this patch can be applied atomically without conflicts. */
  isAtomic: z.boolean().default(true),
  /** Source ChangeOp that generated this patch. */
  sourceChangeOpId: z.string().min(1),
  /** Emitter that produced this patch. */
  emitterId: z.string().min(1),
  /** Checksum of the original file content before patch emission. */
  baseChecksum: z.string().optional(),
  /** Checksum of the target file content after patch emission. */
  targetChecksum: z.string().optional(),
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

interface ComponentPropLike {
  readonly name: string;
  readonly type?: string;
}

function isComponentPropLike(value: unknown): value is ComponentPropLike {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.name === 'string';
}

function toComponentPropMap(props: readonly unknown[]): Map<string, ComponentPropLike> {
  return new Map(
    props.filter(isComponentPropLike).map((prop) => [prop.name, prop]),
  );
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
        const beforePropMap = toComponentPropMap(beforeProps);
        const afterPropMap = toComponentPropMap(afterProps);
        let emittedSpecificPropOp = false;

        for (const [propName, afterProp] of afterPropMap) {
          const beforeProp = beforePropMap.get(propName);
          if (!beforeProp) {
            ops.push({
              id: `op:add-prop:${id}:${propName}`,
              kind: 'add-prop',
              targetElementId: id,
              description: `Add prop "${propName}" to component "${afterElem.name}"`,
              after: afterProp,
              autoApplyConfidence: 0.8,
            });
            emittedSpecificPropOp = true;
            continue;
          }

          if (JSON.stringify(beforeProp) !== JSON.stringify(afterProp) && beforeProp.type !== afterProp.type) {
            ops.push({
              id: `op:update-prop-type:${id}:${propName}`,
              kind: 'update-prop-type',
              targetElementId: id,
              description: `Update prop type for "${propName}" on component "${afterElem.name}"`,
              before: beforeProp,
              after: afterProp,
              autoApplyConfidence: 0.75,
            });
            emittedSpecificPropOp = true;
          }
        }

        for (const [propName, beforeProp] of beforePropMap) {
          if (afterPropMap.has(propName)) continue;
          ops.push({
            id: `op:remove-prop:${id}:${propName}`,
            kind: 'remove-prop',
            targetElementId: id,
            description: `Remove prop "${propName}" from component "${beforeElem.name}"`,
            before: beforeProp,
            autoApplyConfidence: 0.8,
          });
          emittedSpecificPropOp = true;
        }

        if (!emittedSpecificPropOp) {
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
  }

  return ops;
}

// ============================================================================
// Full Patch Lifecycle Types
// ============================================================================

/**
 * Represents a single change to a model element with full audit trail.
 */
export const ModelChangeSchema = z.object({
  id: z.string().uuid(),
  elementId: z.string().uuid(),
  kind: ChangeOpKindSchema,
  description: z.string().min(1),
  before: z.unknown().optional(),
  after: z.unknown().optional(),
  changedBy: z.string().optional(),
  changedAt: z.string().datetime(),
  autoApplyConfidence: z.number().min(0).max(1),
  reviewRequired: z.boolean().default(false),
});

export type ModelChange = z.infer<typeof ModelChangeSchema>;

/**
 * A complete change plan containing all model changes and their relationships.
 */
export const ChangePlanSchema = z.object({
  id: z.string().uuid(),
  sourceModelId: z.string().uuid(),
  targetModelId: z.string().uuid(),
  changes: z.array(ModelChangeSchema),
  createdAt: z.string().datetime(),
  createdBy: z.string().optional(),
  description: z.string().optional(),
  estimatedImpact: z.object({
    addedElements: z.number().int().nonnegative(),
    removedElements: z.number().int().nonnegative(),
    modifiedElements: z.number().int().nonnegative(),
    affectedFiles: z.number().int().nonnegative(),
  }),
});

export type ChangePlan = z.infer<typeof ChangePlanSchema>;

/**
 * A file-level patch with AST/range-based metadata for precise application.
 */
export const FilePatchSchema = z.object({
  id: z.string().uuid(),
  filePath: z.string().min(1),
  /** Unified diff format for the patch. */
  diff: z.string().min(1),
  /** AST-based range information for precise patch application. */
  ranges: z.array(PatchRangeSchema).default([]),
  /** Source model change that generated this patch. */
  sourceChangeId: z.string().uuid(),
  isAtomic: z.boolean().default(true),
  canAutoApply: z.boolean().default(true),
  /** Checksum of the original file content before patch application. */
  baseChecksum: z.string().optional(),
  /** Checksum of the target file content after patch application. */
  targetChecksum: z.string().optional(),
  /** Validation state assigned by patch coordination and review flows. */
  validationStatus: PatchValidationStatusSchema.default('pending'),
  checksum: z.string().optional(), // Backward-compatible alias for older review bundles
});

export type FilePatch = z.infer<typeof FilePatchSchema>;

/**
 * Validation result for a change plan or patch set.
 */
export const ValidationResultSchema = z.object({
  id: z.string().uuid(),
  valid: z.boolean(),
  errors: z.array(z.object({
    code: z.string().min(1),
    message: z.string().min(1),
    severity: z.enum(['error', 'warning']),
    filePath: z.string().optional(),
    changeId: z.string().optional(),
  })),
  warnings: z.array(z.object({
    code: z.string().min(1),
    message: z.string().min(1),
    filePath: z.string().optional(),
    changeId: z.string().optional(),
  })),
  validatedAt: z.string().datetime(),
  validatorId: z.string().min(1),
});

export type ValidationResult = z.infer<typeof ValidationResultSchema>;

/**
 * Review bundle containing changes, patches, and validation results for human review.
 */
export const ReviewBundleSchema = z.object({
  id: z.string().uuid(),
  changePlanId: z.string().uuid(),
  changes: z.array(ModelChangeSchema),
  patches: z.array(FilePatchSchema),
  validation: ValidationResultSchema,
  /** Residual islands that overlap with changes and require manual review. */
  residualOverlaps: z.array(z.object({
    residualId: z.string().uuid(),
    changeId: z.string().uuid(),
    filePath: z.string().min(1),
    reason: z.string().min(1),
  })),
  createdAt: z.string().datetime(),
  expiresAt: z.string().datetime().optional(),
  status: z.enum(['pending', 'approved', 'rejected', 'expired']).default('pending'),
  reviewedBy: z.string().optional(),
  reviewedAt: z.string().datetime().optional(),
  reviewNotes: z.string().optional(),
});

export type ReviewBundle = z.infer<typeof ReviewBundleSchema>;

/**
 * Rollback metadata for reverting applied changes.
 */
export const RollbackMetadataSchema = z.object({
  id: z.string().uuid(),
  originalChangePlanId: z.string().uuid(),
  originalPatchSetId: z.string().uuid(),
  rollbackChangePlanId: z.string().uuid(),
  rollbackPatchSetId: z.string().uuid(),
  rolledBackBy: z.string().optional(),
  rolledBackAt: z.string().datetime(),
  reason: z.string().min(1),
  /** Whether rollback was successful. */
  success: z.boolean(),
  /** Error message if rollback failed. */
  error: z.string().optional(),
});

export type RollbackMetadata = z.infer<typeof RollbackMetadataSchema>;
