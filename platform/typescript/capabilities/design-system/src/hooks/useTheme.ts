import { useState, useEffect, useCallback } from 'react';

export type Theme = 'light' | 'dark' | 'auto';
export type ResolvedTheme = 'light' | 'dark';

export interface ThemeOptions {
  storageKey?: string;
  defaultTheme?: Theme;
}

/**
 * Hook for managing theme state with system preference detection
 * @param options - Theme configuration options
 * @returns Current theme, resolved theme, and setter function
 */
export function useTheme(options: ThemeOptions = {}) {
  const { storageKey = 'ghatana-theme', defaultTheme = 'auto' } = options;
  const [theme, setThemeState] = useState<Theme>(defaultTheme);
  const [resolvedTheme, setResolvedTheme] = useState<ResolvedTheme>('light');
  const [mounted, setMounted] = useState(false);

  // Detect system preference
  const getSystemTheme = useCallback((): ResolvedTheme => {
    if (typeof window === 'undefined') return 'light';
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }, []);

  // Resolve theme to actual value
  const resolveTheme = useCallback(
    (t: Theme): ResolvedTheme => {
      if (t === 'auto') {
        return getSystemTheme();
      }
      return t;
    },
    [getSystemTheme]
  );

  // Initialize theme from storage
  useEffect(() => {
    const stored = localStorage.getItem(storageKey) as Theme | null;
    const initial = stored || defaultTheme;
    setThemeState(initial);
    setResolvedTheme(resolveTheme(initial));
    setMounted(true);
  }, [storageKey, defaultTheme, resolveTheme]);

  // Listen for system theme changes
  useEffect(() => {
    if (theme !== 'auto') return;

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = () => {
      setResolvedTheme(getSystemTheme());
    };

    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, [theme, getSystemTheme]);

  // Update theme
  const setTheme = useCallback(
    (newTheme: Theme) => {
      setThemeState(newTheme);
      setResolvedTheme(resolveTheme(newTheme));
      localStorage.setItem(storageKey, newTheme);

      // Update document class
      if (typeof document !== 'undefined') {
        document.documentElement.classList.remove('light', 'dark');
        document.documentElement.classList.add(resolveTheme(newTheme));
      }
    },
    [storageKey, resolveTheme]
  );

  return {
    theme,
    resolvedTheme,
    setTheme,
    mounted,
  };
}
