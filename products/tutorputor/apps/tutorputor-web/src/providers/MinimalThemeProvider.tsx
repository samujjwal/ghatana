/**
 * Re-exports MinimalThemeProvider from the canonical shared package.
 *
 * Keeping this re-export module preserves all existing local import paths
 * (`'./providers/MinimalThemeProvider'`) while the actual implementation
 * lives in a single authoritative location.
 *
 * @see @ghatana/tutorputor-ui-shared
 */
export { MinimalThemeProvider } from '@ghatana/tutorputor-ui-shared';
export type { MinimalThemeProviderProps } from '@ghatana/tutorputor-ui-shared';

