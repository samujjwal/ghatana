import { CssBaseline } from '@mui/material';
import {
  ThemeProvider as MuiThemeProvider,
  useMediaQuery,
} from '@mui/material';
import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

import { lightTheme, darkTheme } from './theme';

export type ThemeMode = 'light' | 'dark' | 'system';

export interface ThemeContextType {
  mode: ThemeMode;
  theme: typeof lightTheme;
  setMode: (mode: ThemeMode) => void;
  toggleMode: () => void;
}

export const ThemeContext = createContext<ThemeContextType | undefined>(
  undefined
);

const THEME_STORAGE_KEY = 'app-theme-mode';

export interface ThemeProviderProps {
  children: React.ReactNode;
  defaultMode?: ThemeMode;
  storageKey?: string;
  disableSystemPreference?: boolean;
}

export function ThemeProvider({
  children,
  defaultMode = 'system',
  storageKey = THEME_STORAGE_KEY,
  disableSystemPreference = false,
}: ThemeProviderProps) {
  const prefersDarkMode = useMediaQuery('(prefers-color-scheme: dark)');
  const [mode, setMode] = useState<ThemeMode>(() => {
    if (typeof window !== 'undefined') {
      const savedMode = localStorage.getItem(storageKey) as ThemeMode | null;
      if (savedMode) return savedMode;
    }
    return defaultMode;
  });

  useEffect(() => {
    if (typeof window !== 'undefined') {
      localStorage.setItem(storageKey, mode);
    }
  }, [mode, storageKey]);

  const handleSystemPreferenceChange = useCallback(() => {
    if (mode === 'system') {
      setMode('system');
    }
  }, [mode]);

  useEffect(() => {
    if (disableSystemPreference || typeof window === 'undefined') {
      return;
    }

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    mediaQuery.addEventListener('change', handleSystemPreferenceChange);

    return () => {
      mediaQuery.removeEventListener('change', handleSystemPreferenceChange);
    };
  }, [disableSystemPreference, handleSystemPreferenceChange]);

  const theme = useMemo(() => {
    if (mode === 'system') {
      return prefersDarkMode ? darkTheme : lightTheme;
    }
    return mode === 'dark' ? darkTheme : lightTheme;
  }, [mode, prefersDarkMode]);

  const toggleMode = useCallback(() => {
    setMode((previousMode) => {
      if (previousMode === 'light') return 'dark';
      if (previousMode === 'dark') return 'system';
      return 'light';
    });
  }, []);

  const contextValue = useMemo(
    () => ({
      mode,
      theme,
      setMode,
      toggleMode,
    }),
    [mode, theme, toggleMode]
  );

  return (
    <ThemeContext.Provider value={contextValue}>
      <MuiThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </MuiThemeProvider>
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextType {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return context;
}

export default ThemeProvider;
