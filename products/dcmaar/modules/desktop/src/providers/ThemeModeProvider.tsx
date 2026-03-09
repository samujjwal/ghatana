import React, {
  createContext,
  useCallback,
  useContext,
  useMemo,
} from 'react';
import CssBaseline from '@mui/material/CssBaseline';
import useMediaQuery from '@mui/material/useMediaQuery';
import { ThemeProvider } from '@mui/material/styles';
import { createAppTheme } from '../theme';
import type { AppPaletteMode } from '../theme';
import useLocalStorage from '../hooks/useLocalStorage';

export type ThemePreference = AppPaletteMode | 'system';

interface ThemeModeContextValue {
  /** The resolved palette mode after applying system preference */
  mode: AppPaletteMode;
  /** The stored preference (light, dark, or system) */
  preference: ThemePreference;
  setPreference: (value: ThemePreference) => void;
  toggle: () => void;
}

const ThemeModeContext = createContext<ThemeModeContextValue | undefined>(undefined);

const STORAGE_KEY = 'dcmaar-theme-preference';

export function ThemeModeProvider({ children }: { children: React.ReactNode }) {
  const prefersDark = useMediaQuery('(prefers-color-scheme: dark)');
  const [preference, setPreference] = useLocalStorage<ThemePreference>(STORAGE_KEY, 'system');

  const resolvedMode: AppPaletteMode =
    preference === 'system' ? (prefersDark ? 'dark' : 'light') : preference;

  const toggle = useCallback(() => {
    setPreference((prev: ThemePreference) => {
      if (prev === 'system') {
        return resolvedMode === 'dark' ? 'light' : 'dark';
      }
      return prev === 'dark' ? 'light' : 'dark';
    });
  }, [resolvedMode, setPreference]);

  const setPreferenceValue = useCallback(
    (value: ThemePreference) => setPreference(value),
    [setPreference],
  );

  const contextValue = useMemo<ThemeModeContextValue>(
    () => ({
      mode: resolvedMode,
      preference,
      setPreference: setPreferenceValue,
      toggle,
    }),
    [resolvedMode, preference, setPreferenceValue, toggle],
  );

  const theme = useMemo(() => createAppTheme(resolvedMode), [resolvedMode]);

  return (
    <ThemeModeContext.Provider value={contextValue}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeModeContext.Provider>
  );
}

export function useThemeMode() {
  const ctx = useContext(ThemeModeContext);
  if (!ctx) {
    throw new Error('useThemeMode must be used within a ThemeModeProvider');
  }
  return ctx;
}
