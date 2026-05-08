/**
 * AI Label Overlay
 *
 * Marks AI-generated content with a consistent label/badge to set user expectations.
 * Can be used as an inline pill, a section header label, or a hover tooltip trigger.
 *
 * @doc.type component
 * @doc.purpose Mark AI-generated content and surfaces
 * @doc.layer product
 * @doc.pattern Generic Component
 */

import React from 'react';
import { Sparkles } from 'lucide-react';
import { Button } from '../ui/Button';

export type AILabelSize = 'sm' | 'md' | 'lg';
export type AILabelVariant = 'subtle' | 'emphasis' | 'border';

export interface AILabelOverlayProps {
  /** Optional tooltip text explaining the AI involvement */
  tooltip?: string;
  /** Size variant */
  size?: AILabelSize;
  /** Visual variant */
  variant?: AILabelVariant;
  /** Show sparkle icon */
  showIcon?: boolean;
  /** Additional CSS classes */
  className?: string;
  /** Click handler for "learn more" behavior */
  onClick?: () => void;
}

const SIZE_STYLES: Record<AILabelSize, string> = {
  sm: 'px-1.5 py-0.5 text-[10px] gap-0.5',
  md: 'px-2 py-1 text-xs gap-1',
  lg: 'px-3 py-1.5 text-sm gap-1.5',
};

const VARIANT_STYLES: Record<AILabelVariant, string> = {
  subtle: 'bg-info-bg/50 text-info-color',
  emphasis: 'bg-info-bg text-info-color',
  border: 'bg-white border border-info-border text-info-color',
};

export const AILabelOverlay: React.FC<AILabelOverlayProps> = ({
  tooltip,
  size = 'sm',
  variant = 'subtle',
  showIcon = true,
  className = '',
  onClick,
}) => {
  const baseClasses = [
    'inline-flex items-center rounded-full font-medium uppercase tracking-wider',
    size === 'sm' ? 'tracking-wide' : '',
    size === 'md' ? 'tracking-wider' : '',
    size === 'lg' ? 'tracking-widest' : '',
    onClick ? 'cursor-pointer hover:opacity-80' : '',
    SIZE_STYLES[size],
    VARIANT_STYLES[variant],
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const content = (
    <span className={baseClasses} title={tooltip} data-testid="ai-label">
      {showIcon && <Sparkles className={size === 'sm' ? 'h-2.5 w-2.5' : size === 'md' ? 'h-3 w-3' : 'h-4 w-4'} />}
      AI
    </span>
  );

  if (onClick) {
    return (
      <Button type="button" onClick={onClick} className="inline-flex p-0 min-h-0" variant="ghost" size="sm">
        {content}
      </Button>
    );
  }

  return content;
};

/**
 * Section header variant that spans the full width with a divider line.
 */
export const AISectionHeader: React.FC<{
  title: string;
  subtitle?: string;
  showLabel?: boolean;
  children?: React.ReactNode;
}> = ({ title, subtitle, showLabel = true, children }) => {
  return (
    <div className="flex items-center justify-between gap-3">
      <div className="flex items-center gap-2">
        <h3 className="text-sm font-semibold text-text-primary">{title}</h3>
        {showLabel && <AILabelOverlay size="sm" variant="subtle" />}
      </div>
      {subtitle && <p className="text-xs text-text-secondary">{subtitle}</p>}
      {children}
    </div>
  );
};
