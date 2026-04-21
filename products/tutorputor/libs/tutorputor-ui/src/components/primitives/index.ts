/**
 * @ghatana/tutorputor-ui Primitives
 *
 * Shared UI primitive components for the Tutorputor product suite.
 * These components are shared by all Tutorputor applications to prevent code drift.
 *
 * Usage boundaries:
 * - All new UI components should be added here instead of app-specific folders
 * - Existing app-specific components should be migrated here when shared
 * - Do not duplicate these components in app-specific folders
 *
 * @module @ghatana/tutorputor-ui/primitives
 */

export { Button } from './Button';
export type { ButtonProps } from './Button';

export { Input } from './Input';
export type { InputProps } from './Input';

export { Badge } from './Badge';
export type { BadgeProps } from './Badge';

export { Spinner } from './Spinner';
export type { SpinnerProps } from './Spinner';
