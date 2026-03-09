/**
 * Status Badge Component
 * 
 * Displays a status indicator badge with semantic colors.
 * Uses @ghatana/ui Badge component for consistency.
 * 
 * @doc.type component
 * @doc.purpose Display status with semantic badge
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React from 'react';
import { Badge } from '@ghatana/ui';

type StatusVariant = 'success' | 'danger' | 'warning' | 'info' | 'neutral';

interface StatusBadgeProps {
  /** Status text to display */
  status: string;
  /** Visual variant/tone */
  variant?: StatusVariant;
  /** Additional CSS classes */
  className?: string;
}

/**
 * Map local variants to @ghatana/ui Badge tones
 */
const variantToTone: Record<StatusVariant, 'success' | 'danger' | 'warning' | 'info' | 'neutral'> = {
  success: 'success',
  danger: 'danger',
  warning: 'warning',
  info: 'info',
  neutral: 'neutral',
};

/**
 * Status badge component using @ghatana/ui Badge.
 * 
 * @example
 * ```tsx
 * <StatusBadge status="Active" variant="success" />
 * <StatusBadge status="Failed" variant="danger" />
 * ```
 */
export const StatusBadge: React.FC<StatusBadgeProps> = ({
  status,
  variant = 'neutral',
  className = ''
}) => (
  <Badge
    tone={variantToTone[variant]}
    variant="soft"
    className={className}
  >
    {status}
  </Badge>
);

export default StatusBadge;
