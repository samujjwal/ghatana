/**
 * @fileoverview AILabel - Marks content or region as AI-generated.
 *
 * @doc.type component
 * @doc.purpose Visual indicator that content was generated or suggested by AI.
 * @doc.category atom
 * @doc.tags ai, visibility, label
 */

import * as React from 'react';

export interface AILabelProps {
  /** Variant of the AI label */
  readonly variant?: 'badge' | 'tag' | 'inline' | 'overlay';
  /** Size of the label */
  readonly size?: 'sm' | 'md' | 'lg';
  /** Custom label text (defaults to "AI-generated") */
  readonly label?: string;
  /** Whether this is a suggestion (not yet applied) */
  readonly isSuggestion?: boolean;
  /** Additional CSS classes */
  readonly className?: string;
  /** Whether to show the sparkle icon */
  readonly showIcon?: boolean;
}

/**
 * Sparkle icon for AI indication.
 */
const SparkleIcon: React.FC<{ className?: string }> = ({ className = '' }) => (
  <svg
    className={className}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M12 3L14.5 8.5L20 11L14.5 13.5L12 19L9.5 13.5L4 11L9.5 8.5L12 3Z" />
  </svg>
);

const variantConfig = {
  badge: {
    container: 'rounded-full px-2.5 py-0.5 font-medium',
    icon: 'h-3 w-3',
  },
  tag: {
    container: 'rounded px-2 py-0.5 font-medium',
    icon: 'h-3 w-3',
  },
  inline: {
    container: 'inline-flex items-center gap-1 font-normal',
    icon: 'h-3.5 w-3.5',
  },
  overlay: {
    container: 'rounded px-2 py-1 font-medium shadow-sm border',
    icon: 'h-3 w-3',
  },
};

const sizeConfig = {
  sm: 'text-xs',
  md: 'text-sm',
  lg: 'text-base',
};

/**
 * AILabel component - marks content as AI-generated.
 */
export const AILabel: React.FC<AILabelProps> = React.memo(({
  variant = 'badge',
  size = 'md',
  label,
  isSuggestion = false,
  className = '',
  showIcon = true,
}) => {
  const variantClasses = variantConfig[variant];
  const sizeClass = sizeConfig[size];
  const displayLabel = label ?? (isSuggestion ? 'AI Suggested' : 'AI-generated');

  // Suggestions use purple, applied AI uses blue
  const colorClasses = isSuggestion
    ? 'bg-purple-50 text-purple-700 border-purple-200'
    : 'bg-blue-50 text-blue-700 border-blue-200';

  return (
    <span
      className={`inline-flex items-center gap-1 ${variantClasses.container} ${sizeClass} ${colorClasses} ${className}`}
      aria-label={displayLabel}
    >
      {showIcon && <SparkleIcon className={variantClasses.icon} />}
      <span>{displayLabel}</span>
    </span>
  );
});

AILabel.displayName = 'AILabel';

/**
 * AILabelOverlay - positions an AI label as an overlay on content.
 */
export interface AILabelOverlayProps extends AILabelProps {
  /** Position of the overlay */
  readonly position?: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';
  /** Children content to overlay on */
  readonly children: React.ReactNode;
}

const positionConfig = {
  'top-left': 'top-2 left-2',
  'top-right': 'top-2 right-2',
  'bottom-left': 'bottom-2 left-2',
  'bottom-right': 'bottom-2 right-2',
};

export const AILabelOverlay: React.FC<AILabelOverlayProps> = React.memo(({
  position = 'top-right',
  children,
  ...labelProps
}) => {
  return (
    <div className="relative inline-block">
      {children}
      <div className={`absolute ${positionConfig[position]} z-10`}>
        <AILabel variant="overlay" {...labelProps} />
      </div>
    </div>
  );
});

AILabelOverlay.displayName = 'AILabelOverlay';
