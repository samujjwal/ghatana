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
  switch (island.regenerationStrategy) {
    case 'verbatim-preserve':
      return {
        preserved: true,
        strategy: 'verbatim-preserve',
        content: island.originalSource,
      };

    case 'best-effort-approximate':
      return {
        preserved: true,
        strategy: 'best-effort-approximate',
        content: island.originalSource,
        warning: `[YAPPC] Best-effort preservation of residual "${island.normalizedSummary}". Manual review recommended.`,
      };

    case 'emit-warning':
      return {
        preserved: false,
        strategy: 'emit-warning',
        content: `/* [YAPPC-WARNING] Residual island "${island.normalizedSummary}" was not modeled and cannot be regenerated. Original source was:\n${island.originalSource}\n*/`,
        warning: `Residual island "${island.normalizedSummary}" emitted as warning comment.`,
      };

    case 'require-manual-impl':
      return {
        preserved: false,
        strategy: 'require-manual-impl',
        content: `// [YAPPC-TODO] Residual island requires manual implementation:\n// ${island.normalizedSummary}\n// Reason: ${island.reasonUnmodeled}\nthrow new Error("Not implemented: ${island.normalizedSummary.replace(/"/g, '\\"')}");`,
        warning: `Residual island "${island.normalizedSummary}" requires manual implementation.`,
      };

    case 'placeholder-stub':
      return {
        preserved: false,
        strategy: 'placeholder-stub',
        content: `// [YAPPC-STUB] ${island.normalizedSummary}\n// TODO: Implement this section (was residual island ${island.id})\n`,
        warning: `Residual island "${island.normalizedSummary}" emitted as placeholder stub.`,
      };

    default: {
      // Exhaustive check — TypeScript will error if a new strategy is added without handling it
      const exhaustive: never = island.regenerationStrategy;
      return {
        preserved: true,
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
