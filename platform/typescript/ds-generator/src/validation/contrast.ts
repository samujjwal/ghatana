/**
 * @fileoverview Design-system–level WCAG contrast validation.
 *
 * Applies WCAG 2.1 contrast ratio requirements to the color tokens contained
 * in a {@link DesignSystemDocument} (or a plain color record).  The raw
 * contrast math lives in `../tokens/contrast.ts`; this module provides the
 * higher-level "validate a whole design system" surface that the Studio DS
 * workflow uses.
 *
 * @doc.type module
 * @doc.purpose DS-document contrast validation (WCAG AA / AAA)
 * @doc.layer ds-generator
 * @doc.pattern Validation
 */

export {
  hexToRgb,
  calculateRelativeLuminance,
  calculateContrastRatio,
  validateContrast,
  validateContrastBatch,
  passesAA,
  passesAAA,
  passesAALarge,
  passesComponent,
  suggestContrastImprovements,
} from '../tokens/contrast.js';

export type {
  ContrastRequirements,
  ContrastValidationResult,
} from '../tokens/contrast.js';

import {
  calculateContrastRatio,
} from '../tokens/contrast.js';

// ============================================================================
// DS-document level contrast audit
// ============================================================================

/** A single pair in a contrast audit. */
export interface ContrastPair {
  /** Human-readable label for this pair (e.g. "text on background"). */
  readonly label: string;
  readonly foreground: string;
  readonly background: string;
}

/** Outcome for one pair in a contrast audit. */
export interface ContrastAuditEntry {
  readonly label: string;
  readonly foreground: string;
  readonly background: string;
  readonly ratio: number;
  readonly passesAA: boolean;
  readonly passesAAA: boolean;
  /** True when the ratio is below the minimum AA threshold (4.5:1). */
  readonly isFailing: boolean;
}

/** Summary result of a full contrast audit. */
export interface ContrastAuditResult {
  readonly entries: readonly ContrastAuditEntry[];
  readonly failingCount: number;
  readonly passingCount: number;
  readonly isFullyCompliant: boolean;
}

/**
 * Audit an array of foreground/background pairs for WCAG 2.1 compliance.
 *
 * Hex strings must be in `#RRGGBB` format. Values that fail to parse are
 * skipped (a warning is logged in development).
 */
export function auditContrastPairs(
  pairs: readonly ContrastPair[],
): ContrastAuditResult {
  const entries: ContrastAuditEntry[] = [];

  for (const pair of pairs) {
    try {
      const ratio = calculateContrastRatio(pair.foreground, pair.background);
      const aaPass = ratio >= 4.5;
      const aaaPass = ratio >= 7.0;
      entries.push({
        label: pair.label,
        foreground: pair.foreground,
        background: pair.background,
        ratio,
        passesAA: aaPass,
        passesAAA: aaaPass,
        isFailing: !aaPass,
      });
    } catch {
      // Skip invalid hex values — the raw validation utilities already throw
      // descriptive errors; here we just continue so the rest of the audit
      // succeeds.
      const _gProc = (globalThis as { process?: { env?: Record<string, string | undefined> } }).process;
      if (_gProc?.env?.['NODE_ENV'] !== 'production') {
        console.warn(
          `[ds-generator/validation] Skipping invalid color pair: "${pair.foreground}" / "${pair.background}"`,
        );
      }
    }
  }

  const failingCount = entries.filter((e) => e.isFailing).length;

  return {
    entries,
    failingCount,
    passingCount: entries.length - failingCount,
    isFullyCompliant: failingCount === 0,
  };
}

/**
 * Derive canonical foreground/background pairs from a flat color map.
 *
 * The convention used here mirrors common design-system naming:
 * - Keys ending in `-foreground` or `-text` pair with their sibling base
 *   key (same prefix, no suffix).
 * - A `background` key pairs against all keys starting with `text-`.
 *
 * This heuristic covers the most common naming patterns; supply custom
 * {@link ContrastPair} arrays for more precise auditing.
 */
export function deriveColorPairs(
  colors: Record<string, string>,
): ContrastPair[] {
  const pairs: ContrastPair[] = [];
  const keys = Object.keys(colors);

  // Pattern 1: text-X pairs with background-X (or background)
  const textKeys = keys.filter((k) => k.startsWith('text-') || k === 'text');
  const bgKey = colors['background'] ?? colors['surface'] ?? null;

  if (bgKey !== null) {
    for (const tk of textKeys) {
      const fg = colors[tk];
      if (fg !== undefined) {
        pairs.push({
          label: `${tk} on background`,
          foreground: fg,
          background: bgKey,
        });
      }
    }
  }

  // Pattern 2: *-foreground vs *-background siblings
  for (const key of keys) {
    if (key.endsWith('-foreground')) {
      const prefix = key.slice(0, -'-foreground'.length);
      const bgValue = colors[`${prefix}-background`] ?? colors[`${prefix}`];
      if (bgValue !== undefined) {
        const fgValue = colors[key];
        if (fgValue !== undefined) {
          pairs.push({
            label: `${prefix} foreground/background`,
            foreground: fgValue,
            background: bgValue,
          });
        }
      }
    }
  }

  return pairs;
}
