/**
 * @ghatana/tutorputor-ui Components
 *
 * Shared UI components and utilities for the Tutorputor product suite.
 * These exports are shared by all Tutorputor applications to prevent code drift.
 *
 * Usage boundaries:
 * - Use primitives from ./primitives for basic UI components
 * - Do not duplicate these components in app-specific folders
 * - Add new shared components to ./primitives
 *
 * @module @ghatana/tutorputor-ui/components
 */

export { cn } from './utils';
export { MinimalThemeProvider } from './MinimalThemeProvider';
export type { MinimalThemeProviderProps } from './MinimalThemeProvider';

// Export primitive components
export * from './primitives';
