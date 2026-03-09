import { useState, useEffect, useCallback } from 'react';
import { DarkModeManager, DarkModeConfig, getSystemDarkModePreference } from '../theme/darkMode';

/**
 * Hook for managing dark mode with system preference detection
 */
export function useDarkMode(config: DarkModeConfig = {}) {
  const [isDark, setIsDarkState] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false;
    const stored = localStorage.getItem(config.storageKey ?? 'ghatana-dark-mode');
    if (stored !== null) return stored === 'true';
    if (config.defaultMode === 'dark') return true;
    if (config.defaultMode === 'auto') return getSystemDarkModePreference();
    return false;
  });

  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  const setDarkMode = useCallback(
    (value: boolean) => {
      setIsDarkState(value);
      const storageKey = config.storageKey ?? 'ghatana-dark-mode';
      localStorage.setItem(storageKey, String(value));

      if (typeof document !== 'undefined') {
        document.documentElement.setAttribute('data-theme', value ? 'dark' : 'light');
      }
    },
    [config.storageKey]
  );

  const toggleDarkMode = useCallback(() => {
    setDarkMode(!isDark);
  }, [isDark, setDarkMode]);

  const resetToSystemPreference = useCallback(() => {
    const storageKey = config.storageKey ?? 'ghatana-dark-mode';
    localStorage.removeItem(storageKey);
    const systemDark = getSystemDarkModePreference();
    setDarkMode(systemDark);
  }, [config.storageKey, setDarkMode]);

  return {
    isDark,
    setDarkMode,
    toggleDarkMode,
    resetToSystemPreference,
    mounted,
  };
}
