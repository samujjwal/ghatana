import React, { createContext, useCallback, useEffect, useMemo, useState } from 'react';

import { lightTheme, darkTheme } from './theme';

/**
 *
 */
type ThemeMode = 'light' | 'dark' | 'system';

/**
 *
 */
export interface ThemeContextType {
  mode: ThemeMode;
  theme: typeof lightTheme;
  setMode: (mode: ThemeMode) => void;
  toggleMode: () => void;
}

export const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

// Constants
const THEME_STORAGE_KEY = 'app-theme-mode';

/**
 *
 */
export interface ThemeProviderProps {
  children: React.ReactNode;
  defaultMode?: ThemeMode;
  storageKey?: string;
  disableSystemPreference?: boolean;
}

/**
 * ThemeProvider component that handles theme switching and persistence
 */
function ThemeProvider({
  children,
  defaultMode = 'system',
  storageKey = THEME_STORAGE_KEY,
  disableSystemPreference = false,
}: ThemeProviderProps) {
  // Get system preference
  const prefersDarkMode = useMediaQuery('(prefers-color-scheme: dark)');
  
  // State for theme mode
  const [mode, setMode] = useState<ThemeMode>(() => {
    // Try to get theme from localStorage
    if (typeof window !== 'undefined') {
      const savedMode = localStorage.getItem(storageKey) as ThemeMode | null;
      if (savedMode) return savedMode;
    }
    return defaultMode;
  });

  // Save theme preference to localStorage
  useEffect(() => {
    if (typeof window !== 'undefined') {
      localStorage.setItem(storageKey, mode);
    }
  }, [mode, storageKey]);

  // Handle system preference changes
  const handleSystemPreferenceChange = useCallback((e: MediaQueryListEvent) => {
    if (mode === 'system') {
      setMode('system');
    }
  }, [mode]);

  // Listen for system preference changes
  useEffect(() => {
    if (disableSystemPreference) return;
    
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    mediaQuery.addEventListener('change', handleSystemPreferenceChange);
    
    return () => {
      mediaQuery.removeEventListener('change', handleSystemPreferenceChange);
    };
  }, [handleSystemPreferenceChange, disableSystemPreference]);

  // Determine which theme to use based on mode and system preference
  const theme = useMemo(() => {
    if (mode === 'system') {
      return prefersDarkMode ? darkTheme : lightTheme;
    }
    return mode === 'dark' ? darkTheme : lightTheme;
  }, [mode, prefersDarkMode]);

  // Toggle between light/dark theme
  const toggleMode = useCallback(() => {
    setMode((prevMode) => {
      if (prevMode === 'light') return 'dark';
      if (prevMode === 'dark') return 'system';
      return 'light';
    });
  }, []);

  // Context value
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

export { ThemeProvider };
export default ThemeProvider;
