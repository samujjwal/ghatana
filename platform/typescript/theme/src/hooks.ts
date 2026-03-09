/**
 * Theme Hooks for Ghatana Platform
 *
 * @package @ghatana/theme
 */

import { useTheme } from './provider';
import type { ThemeMode, ResolvedTheme } from './types';
import type { Theme, ThemeLayer, ThemeComputed } from './theme';

/**
 * Use theme mode
 *
 * Returns current theme mode and setter
 *
 * @example
 * ```tsx
 * const [theme, setTheme] = useThemeMode();
 * ```
 */
export function useThemeMode(): [ThemeMode, (theme: ThemeMode) => void] {
  const { theme, setTheme } = useTheme();
  return [theme, setTheme];
}

/**
 * Use resolved theme
 *
 * Returns resolved theme (light or dark, never system)
 *
 * @example
 * ```tsx
 * const resolvedTheme = useResolvedTheme();
 * // Returns 'light' or 'dark'
 * ```
 */
export function useResolvedTheme(): ResolvedTheme {
  const { resolvedTheme } = useTheme();
  return resolvedTheme;
}

/**
 * Use theme toggle
 *
 * Returns function to toggle between light and dark
 *
 * @example
 * ```tsx
 * const toggleTheme = useThemeToggle();
 *
 * <button onClick={toggleTheme}>Toggle Theme</button>
 * ```
 */
export function useThemeToggle(): () => void {
  const { toggleTheme } = useTheme();
  return toggleTheme;
}

/**
 * Use system theme
 *
 * Returns system theme preference
 *
 * @example
 * ```tsx
 * const systemTheme = useSystemTheme();
 * // Returns 'light' or 'dark' based on OS preference
 * ```
 */
export function useSystemTheme(): ResolvedTheme {
  const { systemTheme } = useTheme();
  return systemTheme;
}

/**
 * Use is dark mode
 *
 * Returns true if current resolved theme is dark
 *
 * @example
 * ```tsx
 * const isDark = useIsDarkMode();
 *
 * return <div className={isDark ? 'dark-bg' : 'light-bg'}>...</div>
 * ```
 */
export function useIsDarkMode(): boolean {
  const { resolvedTheme } = useTheme();
  return resolvedTheme === 'dark';
}

/**
 * Use is light mode
 *
 * Returns true if current resolved theme is light
 *
 * @example
 * ```tsx
 * const isLight = useIsLightMode();
 * ```
 */
export function useIsLightMode(): boolean {
  const { resolvedTheme } = useTheme();
  return resolvedTheme === 'light';
}

/**
 * Access the full theme definition.
 */
export function useThemeDefinition(): Theme {
  const { themeDefinition } = useTheme();
  return themeDefinition;
}

/**
 * Access computed theme tokens.
 */
export function useThemeTokens(): ThemeComputed {
  const { themeDefinition } = useTheme();
  return themeDefinition.computed;
}

/**
 * Access and update the theme layers.
 */
export function useThemeLayers(): [ThemeLayer[], (layers: ThemeLayer[] | ((prev: ThemeLayer[]) => ThemeLayer[])) => void] {
  const { themeDefinition, setThemeLayers } = useTheme();
  return [themeDefinition.layers, setThemeLayers];
}

// Re-export useTheme for convenience
export { useTheme } from './provider';
