/**
 * Re-exports shared utilities from the canonical shared package.
 *
 * Keeping this re-export module preserves all existing '\@/lib/utils' import
 * paths while the actual implementation lives in one authoritative location.
 *
 * @see @tutorputor/ui
 */
export { cn } from '@tutorputor/ui';
