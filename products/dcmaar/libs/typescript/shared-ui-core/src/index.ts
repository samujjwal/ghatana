/**
 * @ghatana/dcmaar-shared-ui-core
 *
 * Framework-agnostic shared library for DCMAAR platform
 * Contains design tokens, types, hooks, and utilities used across all apps
 */

// Export all tokens
export * from './tokens';

// Export all types
export * from './types';

// Export all utilities
export * from './utils';

// Re-export for convenience
export { COLORS, SPACING, TYPOGRAPHY, BORDER_RADIUS, SHADOWS, ANIMATIONS, Z_INDEX } from './tokens';
export type {
  StatusVariant,
  StatusBadgeProps,
  ConnectionStatus,
  Activity,
  MetricsData,
  TimeRange,
  ButtonVariant,
  ComponentSize,
  CardVariant,
  BadgeVariant,
} from './types';
export {
  formatUptime,
  formatDistanceToNow,
  formatBytes,
  formatNumber,
  formatPercentage,
  formatLatency,
  formatTimestamp,
  formatDateISO,
  truncate,
} from './utils';
