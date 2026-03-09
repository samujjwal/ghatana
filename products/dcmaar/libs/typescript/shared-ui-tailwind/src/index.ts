/**
 * @ghatana/dcmaar-shared-ui-tailwind
 *
 * Shared Tailwind CSS components for DCMAAR platform
 * Used by Extension and Agent apps
 */

// Export all components
export { Button } from './components/Button';
export type { ButtonProps } from './components/Button';

export { Card } from './components/Card';
export type { CardProps } from './components/Card';

export { Input } from './components/Input';
export type { InputProps } from './components/Input';

export { Select } from './components/Select';
export type { SelectProps, SelectOption } from './components/Select';

export { Toggle } from './components/Toggle';
export type { ToggleProps } from './components/Toggle';

export { Badge } from './components/Badge';
export type { BadgeProps } from './components/Badge';

export { Spinner } from './components/Spinner';
export type { SpinnerProps } from './components/Spinner';

export { Skeleton } from './components/Skeleton';
export type { SkeletonProps } from './components/Skeleton';

export { StatusBadge } from './components/StatusBadge';

// Re-export types from shared-ui-core for convenience
export type {
  ButtonVariant,
  ComponentSize,
  CardVariant,
  BadgeVariant,
  StatusVariant,
  StatusBadgeProps,
} from '@ghatana/dcmaar-shared-ui-core';
