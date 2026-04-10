/**
 * Utility functions index
 *
 * Platform utilities (truncate, capitalize, formatDate, getCurrentTimestamp, etc.)
 * are re-exported from @ghatana/platform-utils — the canonical source of truth.
 *
 * Flashit-specific utilities (randomString, slugify, toTitleCase, formatRelativeTime,
 * isValidISODate) are kept here.
 */

// Platform canonical source — re-exported for convenience
export {
  truncate,
  capitalize,
  formatDate,
  getCurrentTimestamp,
  formatDistanceToNow as formatRelativeToNow,
} from '@ghatana/platform-utils';

// Flashit-specific string utilities not in platform
export { toTitleCase, randomString, slugify } from './string';

// Flashit-specific date utilities not in platform
export { formatRelativeTime, isValidISODate } from './date';
