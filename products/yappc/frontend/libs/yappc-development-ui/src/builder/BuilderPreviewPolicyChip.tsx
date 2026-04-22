/**
 * BuilderPreviewPolicyChip
 *
 * Developer-facing chip that shows the active preview trust policy for the
 * YAPPC UI builder. Intended for development tool panels, not production UIs.
 *
 * Reads `builderPreviewModeAtom` from `@yappc/state`. Returns `null` when no
 * preview mode is active (builder not open).
 *
 * @doc.type component
 * @doc.purpose Developer diagnostic chip surfacing the active preview policy
 * @doc.layer product
 * @doc.pattern Diagnostic Component
 */

import React from 'react';
import { useAtomValue } from 'jotai';

import { builderPreviewModeAtom } from '@yappc/state';
import type { PreviewMode } from '@ghatana/ui-builder';

// ─── Constants ───────────────────────────────────────────────────────────────

const POLICY_META: Record<
  PreviewMode,
  { label: string; color: string; description: string }
> = {
  'trusted-local': {
    label: 'Trusted (local)',
    color: '#16a34a',        // green-600
    description: 'Full capability — local platform-trusted renderers only',
  },
  'semi-trusted': {
    label: 'Semi-trusted',
    color: '#d97706',        // amber-600
    description: 'Partial capability — verified partner components',
  },
  'untrusted-sandbox': {
    label: 'Sandboxed',
    color: '#dc2626',        // red-600
    description: 'Isolated sandbox — third-party or unverified renderers',
  },
};

// ─── Component ───────────────────────────────────────────────────────────────

export interface BuilderPreviewPolicyChipProps {
  /** Additional class name applied to the root element. */
  className?: string;
  /** Show the long-form description alongside the label. Defaults to false. */
  showDescription?: boolean;
}

/**
 * BuilderPreviewPolicyChip renders a small, color-coded chip showing the
 * currently active preview trust policy. When no builder session is active the
 * component renders nothing. Use this inside developer tool panels or the
 * editor debug overlay.
 */
export const BuilderPreviewPolicyChip: React.FC<BuilderPreviewPolicyChipProps> = ({
  className,
  showDescription = false,
}) => {
  const mode = useAtomValue(builderPreviewModeAtom);

  if (!mode) return null;

  const meta = POLICY_META[mode];

  return (
    <span
      data-testid="builder-preview-policy-chip"
      data-preview-mode={mode}
      title={meta.description}
      style={{ borderColor: meta.color, color: meta.color }}
      className={[
        'inline-flex items-center gap-1 rounded border px-2 py-0.5 text-xs font-mono font-medium',
        className ?? '',
      ].join(' ').trim()}
    >
      <span
        aria-hidden
        style={{ backgroundColor: meta.color }}
        className="h-1.5 w-1.5 rounded-full"
      />
      {meta.label}
      {showDescription && (
        <span className="ml-1 text-[10px] opacity-70">{meta.description}</span>
      )}
    </span>
  );
};

BuilderPreviewPolicyChip.displayName = 'BuilderPreviewPolicyChip';
