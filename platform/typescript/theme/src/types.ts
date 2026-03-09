/**
 * Theme Types for Ghatana Platform
 *
 * @package @ghatana/theme
 */

import type { Theme, ThemeLayer } from './theme';

/**
 * Theme mode
 */
export type ThemeMode = 'light' | 'dark' | 'system';

/**
 * Resolved theme (system is resolved to light or dark)
 */
export type ResolvedTheme = 'light' | 'dark';

/**
 * Theme configuration
 */
export interface ThemeConfig {
  /**
   * Default theme mode
   * @default 'system'
   */
  defaultTheme?: ThemeMode;

  /**
   * Storage key for persisting theme preference
   * @default 'ghatana-theme'
   */
  storageKey?: string;

  /**
   * Attribute to set on document element
   * @default 'class'
   */
  attribute?: 'class' | 'data-theme';

  /**
   * Enable storage of theme preference
   * @default true
   */
  enableStorage?: boolean;

  /**
   * Enable system theme detection
   * @default true
   */
  enableSystem?: boolean;

  /**
   * Disable theme transition on change
   * @default false
   */
  disableTransition?: boolean;
}

/**
 * Theme context value
 */
export interface ThemeContextValue {
  /**
   * Current theme mode (including 'system')
   */
  theme: ThemeMode;

  /**
   * Resolved theme (light or dark)
   */
  resolvedTheme: ResolvedTheme;

  /**
   * Set theme mode
   */
  setTheme: (theme: ThemeMode) => void;

  /**
   * Toggle between light and dark
   */
  toggleTheme: () => void;

  /**
   * System preference (light or dark)
   */
  systemTheme: ResolvedTheme;

  /**
   * Active theme definition (tokens + layers)
   */
  themeDefinition: Theme;

  /**
    * Update theme layers at runtime
    */
  setThemeLayers: (layers: ThemeLayer[] | ((previous: ThemeLayer[]) => ThemeLayer[])) => void;
}

/**
 * Theme provider props
 */
export interface ThemeProviderProps extends ThemeConfig {
  /**
   * Children to render
   */
  children: React.ReactNode;

  /**
   * Initial theme layers to merge with the base theme.
   */
  initialLayers?: ThemeLayer[];

  /**
   * Provide a fully computed theme definition (controlled mode).
   */
  themeDefinition?: Theme;

  /**
   * Callback fired when the internal theme changes.
   */
  onThemeDefinitionChange?: (theme: Theme) => void;
}
