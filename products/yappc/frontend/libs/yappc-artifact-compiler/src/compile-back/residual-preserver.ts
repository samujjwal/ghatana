/**
 * @fileoverview ResidualPreserver — ensures residual islands survive round-trips.
 *
 * During compile-back, any source region tagged as a ResidualIsland must be
 * re-emitted verbatim (or stubbed/flagged per its RegenerationStrategy).
 * This module applies residual preservation rules to a source file being patched.
 */

import type { ResidualIsland, RegenerationStrategy } from '../residual/types';

// ============================================================================
// Preservation result
// ============================================================================

export interface PreservationResult {
  readonly preserved: boolean;
  readonly blocked: boolean;
  readonly reviewRequired: boolean;
  readonly strategy: RegenerationStrategy;
  readonly content: string;
  readonly warning?: string;
}

// ============================================================================
// ResidualPreserver
// ============================================================================

/**
 * Applies the RegenerationStrategy of a ResidualIsland to produce the content
 * to emit in place of the residual region.
 *
 * This is called by emitters when they encounter a file region that overlaps
 * with a known residual island.
 */
export function preserveResidual(
  island: ResidualIsland,
  _patchedFileContent: string,
): PreservationResult {
  if ((island.regenerationStrategy as string) === 'placeholder-stub') {
    return {
      preserved: false,
      blocked: true,
      reviewRequired: true,
      strategy: 'require-manual-impl',
      content: island.originalSource,
      warning: `Residual island "${island.normalizedSummary}" requires manual review; placeholder stub generation is blocked in production mode.`,
    };
  }

  switch (island.regenerationStrategy) {
    case 'verbatim-preserve':
      return {
        preserved: true,
        blocked: false,
        reviewRequired: island.reviewRequired,
        strategy: 'verbatim-preserve',
        content: island.originalSource,
      };

    case 'best-effort-approximate':
      return {
        preserved: false,
        blocked: true,
        reviewRequired: true,
        strategy: 'best-effort-approximate',
        content: island.originalSource,
        warning: `Residual island "${island.normalizedSummary}" requires manual review; non-verbatim regeneration is blocked in production mode.`,
      };

    case 'emit-warning':
      return {
        preserved: false,
        blocked: true,
        reviewRequired: true,
        strategy: 'emit-warning',
        content: island.originalSource,
        warning: `Residual island "${island.normalizedSummary}" requires manual review; warning-comment regeneration is blocked in production mode.`,
      };

    case 'require-manual-impl':
      return {
        preserved: false,
        blocked: true,
        reviewRequired: true,
        strategy: 'require-manual-impl',
        content: island.originalSource,
        warning: `Residual island "${island.normalizedSummary}" requires manual review; synthetic implementation stubs are blocked in production mode.`,
      };

    default: {
      // Exhaustive check — TypeScript will error if a new strategy is added without handling it
      const exhaustive: never = island.regenerationStrategy;
      return {
        preserved: true,
        blocked: false,
        reviewRequired: true,
        strategy: 'verbatim-preserve',
        content: island.originalSource,
        warning: `Unknown regeneration strategy: ${String(exhaustive)}. Defaulting to verbatim-preserve.`,
      };
    }
  }
}

/**
 * Build a ReadonlyMap of element ID → ResidualIsland for fast lookup during patching.
 */
export function buildResidualIndex(
  islands: readonly ResidualIsland[],
): ReadonlyMap<string, ResidualIsland> {
  return new Map(islands.flatMap(island =>
    island.linkedModelElementIds.length > 0
      ? island.linkedModelElementIds.map(id => [id, island] as const)
      : [[island.id, island] as const],
  ));
}
