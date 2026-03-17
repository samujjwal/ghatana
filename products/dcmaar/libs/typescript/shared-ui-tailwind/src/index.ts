/**
 * @ghatana/dcmaar-shared-ui-tailwind
 *
 * Thin adapter layer over @ghatana/design-system.
 * Components are re-exported from the canonical platform design system.
 * DCMAAR-specific additions (StatusBadge, Toggle alias) are implemented here.
 *
 * @doc.type module
 * @doc.purpose Re-export canonical UI components for DCMAAR apps
 * @doc.layer product
 * @doc.pattern Adapter
 */

// ── Re-exports from canonical design system ─────────────────────────────────
export {
  Button,
  type ButtonProps,
  Input,
  type InputProps,
  Select,
  type SelectProps,
  Badge,
  type BadgeProps,
  Skeleton,
  type SkeletonProps,
  Card,
  type CardProps,
  type CardHeaderProps,
  type CardBodyProps,
  type CardFooterProps,
  type CardVariant,
  Switch,        // exported as Switch; consumer can alias as Toggle
  type SwitchProps,
} from '@ghatana/design-system';

// ── Toggle alias ─────────────────────────────────────────────────────────────
export { Switch as Toggle, type SwitchProps as ToggleProps } from '@ghatana/design-system';

// ── StatusBadge — convenience wrapper with semantic status variants ──────────
import React from 'react';
import { Badge, type BadgeProps } from '@ghatana/design-system';

export type StatusVariant = 'online' | 'offline' | 'idle' | 'error' | 'warning';

const STATUS_COLOR_MAP: Record<StatusVariant, string> = {
  online:  'bg-green-500 text-white',
  offline: 'bg-gray-400 text-white',
  idle:    'bg-yellow-400 text-gray-900',
  error:   'bg-red-500 text-white',
  warning: 'bg-orange-400 text-white',
};

export interface StatusBadgeProps extends Omit<BadgeProps, 'children'> {
  status: StatusVariant;
  label?: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, label, className = '', ...rest }) => {
  const colorClass = STATUS_COLOR_MAP[status] ?? 'bg-gray-300 text-gray-800';
  return (
    <Badge className={`${colorClass} ${className}`} {...rest}>
      {label ?? status}
    </Badge>
  );
};

