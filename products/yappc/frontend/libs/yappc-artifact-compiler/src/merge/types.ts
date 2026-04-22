/**
 * @fileoverview Merge engine type definitions.
 *
 * The merge engine compares existing repo artifacts, extracted semantic model,
 * and generated output to classify differences and drive round-trip workflows.
 */

import { z } from 'zod';
import type { SemanticModelElement } from '../model/types';
import type { ResidualIsland } from '../residual/types';

// ============================================================================
// Difference Classification
// ============================================================================

export const DifferenceKindSchema = z.enum([
  'safe-normalization',
  'semantic-equivalence',
  'manual-review-required',
  'unsupported-divergence',
]);

export type DifferenceKind = z.infer<typeof DifferenceKindSchema>;

// ============================================================================
// Model Difference
// ============================================================================

export interface ModelDifference {
  readonly elementId: string;
  readonly elementKind: string;
  readonly kind: DifferenceKind;
  readonly fieldPath: string;
  readonly sourceValue: unknown;
  readonly generatedValue: unknown;
  readonly confidence: number;
  readonly reason: string;
  readonly requiresAction: boolean;
}

// ============================================================================
// Merge Result
// ============================================================================

export interface MergeResult {
  readonly acceptedElements: readonly SemanticModelElement[];
  readonly rejectedDifferences: readonly ModelDifference[];
  readonly reviewQueue: readonly ModelDifference[];
  readonly residualIslands: readonly ResidualIsland[];
  readonly mergeCoverageRatio: number;
}

// ============================================================================
// Merge Engine Interface
// ============================================================================

export interface MergeEngine {
  readonly id: string;
  readonly version: string;
  compare(existingSource: string, modelElement: SemanticModelElement, generatedOutput: string): readonly ModelDifference[];
  merge(differences: readonly ModelDifference[]): MergeResult;
}
