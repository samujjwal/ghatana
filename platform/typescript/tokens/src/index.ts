/**
 * @ghatana/tokens
 *
 * Global design tokens for Ghatana platform
 * Framework-agnostic design tokens merged from DCMAAR and YAPPC design systems
 *
 * @package @ghatana/tokens
 * @version 0.1.0
 */

// Export all color tokens
export * from './colors';

// Export all spacing tokens
export * from './spacing';

// Export all typography tokens
export * from './typography';

// Export all shadow tokens
export * from './shadows';

// Export all border tokens
export * from './borders';

// Export all breakpoint tokens
export * from './breakpoints';

// Export all transition tokens
export * from './transitions';

// Export all z-index tokens
export * from './z-index';

// Aggregated registry + helpers
export { tokens, helpers } from './registry';

// CSS custom property generation
export { generateCssVariables, getCssVariables, createCssVariableMap } from './css';

// Validation utilities
export { validateTokens, assertTokensValid } from './validation';
// Note: tokenRegistrySchema export removed - it's not needed for normal usage
// and causes SSR issues. Use validateTokens() instead.

/**
 * Safe Area CSS Support (P0 Critical - Mobile Notch/Dynamic Island)
 * Import in root layout: import '@ghatana/tokens/safe-area.css';
 */

/**
 * Migration notes for products:
 *
 * DCMAAR products should update imports:
 *   - @ghatana/dcmaar-shared-ui-core/tokens → @ghatana/tokens
 *
 * YAPPC products should update imports:
 *   - @ghatana/yappc-ui/tokens → @ghatana/tokens
 *
 * Report Generator should adopt these tokens for consistency
 */
