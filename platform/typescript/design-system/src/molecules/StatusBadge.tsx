/**
 * StatusBadge Component
 *
 * <p>A reusable status badge component that maps domain-specific status values
 * to visual variants. Provides a consistent way to display status indicators
 * across products while allowing custom status mappings.</p>
 *
 * @doc.type component
 * @doc.purpose Reusable status badge with domain status mapping support
 * @doc.layer design-system
 */
import * as React from 'react';
import { Badge, BadgeProps } from '../atoms/Badge';

export interface StatusMapping {
  variant?: BadgeProps['variant'];
  tone?: BadgeProps['tone'];
  label?: string;
}

export interface StatusBadgeProps extends Omit<BadgeProps, 'variant' | 'tone'> {
  /** The status value to display and map */
  status: string;
  /** Optional custom mapping from status values to visual variants */
  statusMappings?: Record<string, StatusMapping>;
  /** Optional custom mapping from status values to display labels */
  labelMappings?: Record<string, string>;
  /** Fallback variant when status is not in mappings */
  fallbackVariant?: BadgeProps['variant'];
  /** Fallback tone when status is not in mappings */
  fallbackTone?: BadgeProps['tone'];
  /** Whether to show the status value as label if no mapping is found */
  showStatusAsFallback?: boolean;
}

/**
 * Default status mappings for common status patterns
 */
const DEFAULT_STATUS_MAPPINGS: Record<string, StatusMapping> = {
  // Success states
  active: { variant: 'success', tone: 'success', label: 'Active' },
  completed: { variant: 'success', tone: 'success', label: 'Completed' },
  success: { variant: 'success', tone: 'success', label: 'Success' },
  approved: { variant: 'success', tone: 'success', label: 'Approved' },
  launched: { variant: 'success', tone: 'success', label: 'Launched' },
  granted: { variant: 'success', tone: 'success', label: 'Granted' },

  // Warning states
  pending: { variant: 'soft', tone: 'warning', label: 'Pending' },
  pending_approval: { variant: 'soft', tone: 'warning', label: 'Pending Approval' },
  paused: { variant: 'soft', tone: 'warning', label: 'Paused' },
  warning: { variant: 'soft', tone: 'warning', label: 'Warning' },
  expiring: { variant: 'soft', tone: 'warning', label: 'Expiring' },

  // Error/danger states
  failed: { variant: 'destructive', tone: 'danger', label: 'Failed' },
  error: { variant: 'destructive', tone: 'danger', label: 'Error' },
  rejected: { variant: 'destructive', tone: 'danger', label: 'Rejected' },
  blocked: { variant: 'destructive', tone: 'danger', label: 'Blocked' },
  revoked: { variant: 'destructive', tone: 'danger', label: 'Revoked' },
  rolled_back: { variant: 'destructive', tone: 'danger', label: 'Rolled Back' },

  // Neutral/default states
  draft: { variant: 'default', tone: 'neutral', label: 'Draft' },
  archived: { variant: 'default', tone: 'neutral', label: 'Archived' },
  inactive: { variant: 'default', tone: 'neutral', label: 'Inactive' },
  unknown: { variant: 'default', tone: 'neutral', label: 'Unknown' },
};

/**
 * StatusBadge component – reusable status indicator with domain status mapping.
 */
export const StatusBadge = React.forwardRef<HTMLSpanElement, StatusBadgeProps>(
  (
    {
      status,
      statusMappings = {},
      labelMappings = {},
      fallbackVariant = 'default',
      fallbackTone = 'neutral',
      showStatusAsFallback = true,
      children,
      ...badgeProps
    },
    ref
  ) => {
    // Normalize status key for case-insensitive matching
    const normalizedStatus = status.toLowerCase().replace(/[-_\s]/g, '_');

    // Get mapping from custom mappings first, then defaults
    const customMapping = statusMappings[normalizedStatus] || statusMappings[status];
    const defaultMapping = DEFAULT_STATUS_MAPPINGS[normalizedStatus] || DEFAULT_STATUS_MAPPINGS[status];
    const mapping = customMapping || defaultMapping;

    // Determine variant and tone
    const variant = mapping?.variant || fallbackVariant;
    const tone = mapping?.tone || fallbackTone;

    // Determine label - prioritize labelMappings over statusMappings label
    const labelMapping = labelMappings[normalizedStatus] || labelMappings[status];
    const label = labelMapping || mapping?.label || (showStatusAsFallback ? status : children);

    // If no label found and showStatusAsFallback is false, don't render anything
    if (!label) {
      return null;
    }

    return (
      <Badge ref={ref} variant={variant} tone={tone} {...badgeProps}>
        {label}
      </Badge>
    );
  }
);

StatusBadge.displayName = 'StatusBadge';
