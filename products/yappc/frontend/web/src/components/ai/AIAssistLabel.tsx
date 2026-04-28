/**
 * AIAssistLabel — C-Y3 — Rule vs model provenance disclosure
 *
 * Every AI-assist surface must label whether a suggestion was produced by:
 * - A deterministic **rule** (e.g., policy check, schema validation, pattern match)
 * - A **model** (e.g., LLM inference, embedding-based retrieval)
 * - A **hybrid** of both
 *
 * This component renders a small, accessible badge/chip that makes the
 * provenance explicit to the user.
 *
 * ## Usage
 * ```tsx
 * <AIAssistLabel source="rule" label="STRIDE threat check" />
 * <AIAssistLabel source="model" label="GPT-4o" />
 * <AIAssistLabel source="hybrid" label="Policy + LLM" />
 * ```
 *
 * @doc.type component
 * @doc.purpose Label AI-assist suggestions with rule vs model provenance
 * @doc.layer product
 * @doc.pattern AI Transparency
 */

import React from 'react';
import { Cpu, BookOpen, Layers } from 'lucide-react';

// ── Types ─────────────────────────────────────────────────────────────────────

/** Source of an AI-assist suggestion. */
export type AIAssistSource = 'rule' | 'model' | 'hybrid';

export interface AIAssistLabelProps {
  /** Whether the suggestion came from a rule, a model, or both. */
  source: AIAssistSource;
  /**
   * Optional descriptive label, e.g. "STRIDE policy" or "GPT-4o".
   * If omitted, a canonical default is shown.
   */
  label?: string;
  /** Additional CSS class names. */
  className?: string;
}

// ── Config ─────────────────────────────────────────────────────────────────────

const SOURCE_CONFIG: Record<
  AIAssistSource,
  {
    defaultLabel: string;
    icon: React.ReactNode;
    chipClass: string;
    tooltip: string;
  }
> = {
  rule: {
    defaultLabel: 'Rule-based',
    icon: <BookOpen className="h-3 w-3" aria-hidden="true" />,
    chipClass: 'bg-info-bg text-info-color border-info-border',
    tooltip: 'This suggestion was produced by a deterministic rule or policy — not an AI model.',
  },
  model: {
    defaultLabel: 'AI model',
    icon: <Cpu className="h-3 w-3" aria-hidden="true" />,
    chipClass: 'bg-primary-bg text-primary border-primary-border',
    tooltip: 'This suggestion was produced by an AI language model. It may be imprecise.',
  },
  hybrid: {
    defaultLabel: 'Rule + AI model',
    icon: <Layers className="h-3 w-3" aria-hidden="true" />,
    chipClass: 'bg-warning-bg text-warning-color border-warning-border',
    tooltip: 'This suggestion combines deterministic rules with AI model inference.',
  },
};

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * Renders an inline provenance chip disclosing whether an AI-assist suggestion
 * came from a rule, a model, or both.
 */
export function AIAssistLabel({ source, label, className }: AIAssistLabelProps) {
  const config = SOURCE_CONFIG[source];
  const displayLabel = label ?? config.defaultLabel;

  return (
    <span
      role="img"
      aria-label={`AI assist source: ${displayLabel}`}
      title={config.tooltip}
      data-testid="ai-assist-label"
      data-source={source}
      className={[
        'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium',
        config.chipClass,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {config.icon}
      {displayLabel}
    </span>
  );
}
