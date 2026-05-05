/**
 * Provenance Badge Component
 *
 * Displays provenance information for lifecycle items.
 * Every lifecycle item should show provenance:
 * - Backed: persisted API data
 * - Derived: computed from backed data
 * - Suggested: generated recommendation requiring review
 * - Preview: non-production / preview-only
 * - Unavailable: missing capability or integration
 *
 * @doc.type component
 * @doc.purpose Provenance badge display
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';

export type ProvenanceType = 'backed' | 'derived' | 'suggested' | 'preview' | 'unavailable';

export interface ProvenanceBadgeProps {
  /** Provenance type */
  type: ProvenanceType;
  /** Custom label (optional, defaults to type) */
  label?: string;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Show tooltip with description */
  showTooltip?: boolean;
  /** Custom className */
  className?: string;
}

/**
 * Provenance type configurations
 */
const PROVENANCE_CONFIG: Record<ProvenanceType, {
  label: string;
  description: string;
  icon: string;
  colors: string;
}> = {
  backed: {
    label: 'Backed',
    description: 'Persisted API data - safe to act on',
    icon: '✓',
    colors: 'bg-success-bg text-success-color dark:bg-success-bg/30 dark:text-success-color border-success-border dark:border-success-border',
  },
  derived: {
    label: 'Derived',
    description: 'Computed from backed data',
    icon: '→',
    colors: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color border-info-border dark:border-info-border',
  },
  suggested: {
    label: 'Suggested',
    description: 'Generated recommendation requiring review',
    icon: '💡',
    colors: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color border-info-border dark:border-info-border',
  },
  preview: {
    label: 'Preview',
    description: 'Non-production / preview-only',
    icon: '👁️',
    colors: 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color border-warning-border dark:border-warning-border',
  },
  unavailable: {
    label: 'Unavailable',
    description: 'Missing capability or integration',
    icon: '—',
    colors: 'bg-surface-muted text-fg dark:bg-surface/30 dark:text-fg-muted border-border dark:border-border',
  },
};

/**
 * Size classes
 */
const SIZE_CLASSES: Record<'sm' | 'md' | 'lg', string> = {
  sm: 'px-2 py-0.5 text-xs',
  md: 'px-3 py-1 text-sm',
  lg: 'px-4 py-1.5 text-base',
};

/**
 * Provenance Badge Component
 *
 * Displays provenance with:
 * - Type-based color coding and icons
 * - Optional custom label
 * - Size variants
 * - Tooltip with description
 */
export const ProvenanceBadge: React.FC<ProvenanceBadgeProps> = ({
  type,
  label,
  size = 'md',
  showTooltip = true,
  className = '',
}) => {
  const config = PROVENANCE_CONFIG[type];
  const sizeClass = SIZE_CLASSES[size];
  const displayLabel = label || config.label;

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border font-medium ${config.colors} ${sizeClass} ${className}`}
      title={showTooltip ? config.description : undefined}
      role="status"
      aria-label={`Provenance: ${displayLabel}`}
    >
      <span aria-hidden="true">{config.icon}</span>
      <span>{displayLabel}</span>
    </span>
  );
};

export default ProvenanceBadge;
