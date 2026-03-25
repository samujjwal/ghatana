/**
 * Re-exports MinimalThemeProvider from the canonical shared package.
 *
 * Keeping this re-export module preserves all existing local import paths
 * (`'./providers/MinimalThemeProvider'`) while the actual implementation
 * lives in a single authoritative location.
 *
 * @see @tutorputor/ui
 */
export { MinimalThemeProvider } from '@tutorputor/ui';
export type { MinimalThemeProviderProps } from '@tutorputor/ui';
