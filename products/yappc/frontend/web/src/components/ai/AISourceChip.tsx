/**
 * AISourceChip — YAPPC Web.
 *
 * Canonical component that labels every AI-produced value with its provenance:
 * - **RULE**: result from the deterministic rule engine (100% deterministic)
 * - **MODEL**: result from a probabilistic LLM call (confidence shown when known)
 *
 * This component implements the AI-Y1 audit requirement: every AI-surface in the
 * YAPPC UI must carry visible provenance so users can calibrate trust appropriately.
 *
 * Matches the `AISource` enum in `yappc-api.openapi.yaml`.
 *
 * ## Usage
 * ```tsx
 * // Rule-based result — no confidence shown
 * <AISourceChip source="RULE" />
 *
 * // Model-backed result with confidence
 * <AISourceChip source="MODEL" confidence={0.87} />
 *
 * // With tooltip explanation
 * <AISourceChip source="MODEL" confidence={0.73} rationale="Based on semantic similarity" />
 * ```
 *
 * @doc.type component
 * @doc.purpose Provenance labelling for AI-produced values
 * @doc.layer product
 * @doc.pattern Atom
 */

import React from 'react';

// ─────────────────────────────────────────────────────────────────────────────
// Types — aligned with OpenAPI AISource enum
// ─────────────────────────────────────────────────────────────────────────────

/** Canonical AI source values — matches OpenAPI `AISource` schema enum. */
export type AISource = 'RULE' | 'MODEL';

export interface AISourceChipProps {
  /** Source provenance of the AI-produced value. */
  source: AISource;
  /**
   * Model confidence in [0, 1]. Shown as a percentage.
   * Only meaningful when `source === 'MODEL'`; ignored for RULE.
   */
  confidence?: number;
  /** Optional tooltip explaining the rationale for this result. */
  rationale?: string;
  /** Visual size. Defaults to 'sm'. */
  size?: 'sm' | 'md';
  /** Additional CSS class names for layout control. */
  className?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const SOURCE_CONFIG = {
  RULE: {
    label: 'Rule',
    title: 'Deterministic rule — this result is 100% reproducible.',
    bgClass: 'bg-slate-100 text-slate-700',
    icon: '⚙',
  },
  MODEL: {
    label: 'Model',
    title: 'Probabilistic AI model — confidence score shown when available.',
    bgClass: 'bg-violet-100 text-violet-700',
    icon: '✦',
  },
} as const satisfies Record<AISource, { label: string; title: string; bgClass: string; icon: string }>;

// ─────────────────────────────────────────────────────────────────────────────
// Component
// ─────────────────────────────────────────────────────────────────────────────

export const AISourceChip = React.memo<AISourceChipProps>(function AISourceChip({
  source,
  confidence,
  rationale,
  size = 'sm',
  className,
}) {
  const config = SOURCE_CONFIG[source];
  const showConfidence = source === 'MODEL' && confidence !== undefined;
  const tooltipText = rationale ?? config.title;
  const sizeClass = size === 'sm' ? 'text-xs px-1.5 py-0.5 gap-1' : 'text-sm px-2 py-1 gap-1.5';

  return (
    <span
      className={[
        'inline-flex items-center rounded font-medium select-none',
        sizeClass,
        config.bgClass,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      title={tooltipText}
      aria-label={`AI source: ${config.label}${showConfidence ? `, confidence ${Math.round(confidence! * 100)}%` : ''}`}
      role="status"
    >
      <span aria-hidden="true">{config.icon}</span>
      <span>{config.label}</span>
      {showConfidence && (
        <span className="opacity-75">{Math.round(confidence! * 100)}%</span>
      )}
    </span>
  );
});
