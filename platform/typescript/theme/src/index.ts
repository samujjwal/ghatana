/**
 * @ghatana/theme
 *
 * Unified theme system for Ghatana platform
 * Light/dark mode support with system preference detection
 *
 * @package @ghatana/theme
 * @version 0.1.0
 */

// Export provider
export { ThemeProvider, useTheme } from './provider';

// Export hooks
export {
  useThemeMode,
  useResolvedTheme,
  useThemeToggle,
  useSystemTheme,
  useIsDarkMode,
  useIsLightMode,
  useThemeDefinition,
  useThemeTokens,
  useThemeLayers,
} from './hooks';

// Export types
export type {
  ThemeMode,
  ResolvedTheme,
  ThemeConfig,
  ThemeContextValue,
  ThemeProviderProps,
} from './types';

// Theme helpers
export {
  baseThemeTokens,
  createTheme,
  applyThemeLayers,
  resolveThemeColors,
  themeToCSSVariables,
} from './theme';

export type { Theme, ThemeLayer, ThemeLayerType, ThemeTokens, ThemeComputed, DeepPartial } from './theme';

// Material-UI theme with accessibility enhancements (P0 Critical - Touch Targets)
export { createAccessibleMuiTheme, accessibleMuiTheme } from './muiTheme';

// Schema + validation utilities
export * from './schema';

// Brand preset support
export { defaultBrandPresets, brandPresetSchema, themeLayerSchema, themeOverridesSchema } from './brandPresets';
export type { BrandPreset, BrandPresetInput } from './brandPresets';

export {
  registerBrandPreset,
  registerBrandPresets,
  getBrandPreset,
  getBrandPresets,
  setBrandPresetLoader,
  loadBrandPresets,
  applyBrandPreset,
  loadAndApplyBrandPreset,
} from './themeManager';
export type { BrandPresetLoader, BrandPresetSource, ApplyBrandPresetOptions } from './themeManager';

/**
 * Migration notes for products:
 *
 * DCMAAR products should update imports:
 *   - @ghatana/dcmaar-agent-core-ui/theme/ThemeProvider → @ghatana/theme
 *
 * YAPPC products should adopt:
 *   - Use @ghatana/theme for consistent theming
 */
