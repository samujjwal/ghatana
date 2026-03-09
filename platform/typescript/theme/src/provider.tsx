/**
 * Theme Provider for Ghatana Platform
 *
 * Unified theme system with light/dark mode support
 *
 * @migrated-from @ghatana/dcmaar-agent-core-ui/theme/ThemeProvider
 *
 * @package @ghatana/theme
 */

import * as React from 'react';
import type {
  ThemeMode,
  ResolvedTheme,
  ThemeContextValue,
  ThemeProviderProps,
} from './types';
import { createTheme, themeToCSSVariables, type Theme, type ThemeLayer } from './theme';

const STORAGE_KEY = 'ghatana-theme';
const MEDIA_QUERY = '(prefers-color-scheme: dark)';

/**
 * Get system theme preference
 */
function getSystemTheme(): ResolvedTheme {
  if (typeof window === 'undefined') return 'light';
  if (!window.matchMedia) return 'light';

  return window.matchMedia(MEDIA_QUERY).matches ? 'dark' : 'light';
}

/**
 * Get stored theme from localStorage
 */
function getStoredTheme(storageKey: string): ThemeMode | null {
  if (typeof window === 'undefined') return null;

  try {
    const stored = localStorage.getItem(storageKey);
    if (stored === 'light' || stored === 'dark' || stored === 'system') {
      return stored;
    }
  } catch (e) {
    console.warn('Failed to read theme from storage:', e);
  }

  return null;
}

/**
 * Store theme in localStorage
 */
function storeTheme(storageKey: string, theme: ThemeMode): void {
  if (typeof window === 'undefined') return;

  try {
    localStorage.setItem(storageKey, theme);
  } catch (e) {
    console.warn('Failed to store theme:', e);
  }
}

/**
 * Apply theme to document
 */
function applyTheme(
  resolvedTheme: ResolvedTheme,
  attribute: 'class' | 'data-theme',
  disableTransition: boolean,
  themeDefinition: Theme
): void {
  if (typeof window === 'undefined') return;

  const root = document.documentElement;

  // Disable transitions during theme change
  if (disableTransition) {
    const css = document.createElement('style');
    css.textContent = '* { transition: none !important; }';
    document.head.appendChild(css);

    // Force reflow
    void root.offsetHeight;

    setTimeout(() => {
      document.head.removeChild(css);
    }, 1);
  }

  if (attribute === 'class') {
    root.classList.remove('light', 'dark');
    root.classList.add(resolvedTheme);
  } else {
    root.setAttribute('data-theme', resolvedTheme);
  }

  root.style.setProperty('color-scheme', resolvedTheme);
  root.style.backgroundColor = themeDefinition.computed.colors.background.default;
  root.style.color = themeDefinition.computed.colors.text.primary;

  const variables = themeToCSSVariables(themeDefinition);
  for (const [key, value] of Object.entries(variables)) {
    root.style.setProperty(key, value);
  }
}

/**
 * Resolve theme mode to actual theme
 */
function resolveTheme(theme: ThemeMode, systemTheme: ResolvedTheme): ResolvedTheme {
  return theme === 'system' ? systemTheme : theme;
}

// Create context
const ThemeContext = React.createContext<ThemeContextValue | undefined>(undefined);

/**
 * Theme Provider Component
 *
 * Provides theme context with light/dark mode support
 *
 * @example
 * ```tsx
 * <ThemeProvider defaultTheme="system">
 *   <App />
 * </ThemeProvider>
 * ```
 */
export function ThemeProvider({
  children,
  defaultTheme = 'system',
  storageKey = STORAGE_KEY,
  attribute = 'class',
  enableStorage = true,
  enableSystem = true,
  disableTransition = false,
  initialLayers,
  themeDefinition: controlledTheme,
  onThemeDefinitionChange,
}: ThemeProviderProps) {
  // System theme
  const [systemTheme, setSystemTheme] = React.useState<ResolvedTheme>(getSystemTheme);

  // Current theme mode
  const [theme, setThemeState] = React.useState<ThemeMode>(() => {
    if (enableStorage) {
      return getStoredTheme(storageKey) || defaultTheme;
    }
    return defaultTheme;
  });

  // Resolved theme (light or dark)
  const resolvedTheme = resolveTheme(theme, systemTheme);

  const [layersState, setLayersState] = React.useState<ThemeLayer[]>(
    () => controlledTheme?.layers ?? initialLayers ?? []
  );

  React.useEffect(() => {
    if (controlledTheme) {
      setLayersState(controlledTheme.layers);
      return;
    }
    if (initialLayers) {
      setLayersState(initialLayers);
    }
  }, [controlledTheme, initialLayers]);

  const themeDefinition = React.useMemo(() => {
    if (controlledTheme) {
      if (controlledTheme.mode !== resolvedTheme) {
        return createTheme(resolvedTheme, controlledTheme.layers);
      }
      return controlledTheme;
    }
    return createTheme(resolvedTheme, layersState);
  }, [controlledTheme, resolvedTheme, layersState]);

  const setThemeLayers = React.useCallback(
    (next: ThemeLayer[] | ((previous: ThemeLayer[]) => ThemeLayer[])) => {
      if (controlledTheme) {
        const currentLayers = controlledTheme.layers;
        const nextLayers =
          typeof next === 'function' ? next(currentLayers) : next;
        onThemeDefinitionChange?.(createTheme(resolvedTheme, nextLayers));
        return;
      }

      setLayersState((prev) =>
        typeof next === 'function' ? next(prev) : next
      );
    },
    [controlledTheme, onThemeDefinitionChange, resolvedTheme]
  );

  React.useEffect(() => {
    if (!controlledTheme) {
      onThemeDefinitionChange?.(themeDefinition);
    }
  }, [controlledTheme, themeDefinition, onThemeDefinitionChange]);

  // Listen to system theme changes
  React.useEffect(() => {
    if (!enableSystem) return;
    if (typeof window === 'undefined') return;
    if (!window.matchMedia) return;

    const mediaQuery = window.matchMedia(MEDIA_QUERY);

    const handleChange = (e: MediaQueryListEvent) => {
      setSystemTheme(e.matches ? 'dark' : 'light');
    };

    // Modern browsers
    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', handleChange);
      return () => mediaQuery.removeEventListener('change', handleChange);
    }
    // Legacy browsers
    else if (mediaQuery.addListener) {
      mediaQuery.addListener(handleChange);
      return () => mediaQuery.removeListener(handleChange);
    }
  }, [enableSystem]);

  // Apply theme on mount and when resolved theme changes
  React.useEffect(() => {
    applyTheme(resolvedTheme, attribute, disableTransition, themeDefinition);
  }, [resolvedTheme, attribute, disableTransition, themeDefinition]);

  // Set theme function
  const setTheme = React.useCallback(
    (newTheme: ThemeMode) => {
      setThemeState(newTheme);
      if (enableStorage) {
        storeTheme(storageKey, newTheme);
      }
    },
    [enableStorage, storageKey]
  );

  // Toggle between light and dark
  const toggleTheme = React.useCallback(() => {
    setTheme(resolvedTheme === 'light' ? 'dark' : 'light');
  }, [resolvedTheme, setTheme]);

  const value: ThemeContextValue = React.useMemo(
    () => ({
      theme,
      resolvedTheme,
      systemTheme,
      setTheme,
      toggleTheme,
      themeDefinition,
      setThemeLayers,
    }),
    [theme, resolvedTheme, systemTheme, setTheme, toggleTheme, themeDefinition, setThemeLayers]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

/**
 * Use Theme Hook
 *
 * Access theme context
 *
 * @throws Error if used outside ThemeProvider
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { theme, setTheme, toggleTheme } = useTheme();
 *
 *   return (
 *     <button onClick={toggleTheme}>
 *       Current theme: {theme}
 *     </button>
 *   );
 * }
 * ```
 */
export function useTheme(): ThemeContextValue {
  const context = React.useContext(ThemeContext);

  if (context === undefined) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }

  return context;
}

export default ThemeProvider;
