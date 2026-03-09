import { useMemo, useCallback, useEffect } from 'react';
import { useMediaQuery } from '@mui/material';
import useLocalStorage from './useLocalStorage';
import { createTheme, ThemeProvider as MuiThemeProvider } from '@mui/material/styles';

type ThemeMode = 'light' | 'dark' | 'system';

const prefersDarkMode = (): boolean => {
  if (typeof window === 'undefined') return false;
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
};

const getSystemTheme = (): ThemeMode => {
  return prefersDarkMode() ? 'dark' : 'light';
};

interface ThemeOptions {
  mode: ThemeMode;
  toggleColorMode: () => void;
  setThemeMode: (mode: ThemeMode) => void;
  isDarkMode: boolean;
  isLightMode: boolean;
}

/**
 * Custom hook to manage theme and color mode
 */
export function useTheme(): ThemeOptions {
  const prefersDark = useMediaQuery('(prefers-color-scheme: dark)');
  const [mode, setMode] = useLocalStorage<ThemeMode>('themeMode', getSystemTheme());
  
  // Update theme mode when system preference changes
  useEffect(() => {
    const systemMode = getSystemTheme();
    if (mode === 'system') {
      setMode(systemMode);
    }
  }, [prefersDark, mode, setMode]);

  // Toggle between light and dark mode
  const toggleColorMode = useCallback(() => {
    setMode((prevMode) => (prevMode === 'light' ? 'dark' : 'light'));
  }, [setMode]);

  // Set a specific theme mode
  const setThemeMode = useCallback((newMode: ThemeMode) => {
    setMode(newMode);
  }, [setMode]);

  // Determine the actual theme mode to use
  const actualMode = mode === 'system' ? getSystemTheme() : mode;
  
  const isDarkMode = actualMode === 'dark';
  const isLightMode = actualMode === 'light';

  return {
    mode: actualMode,
    toggleColorMode,
    setThemeMode,
    isDarkMode,
    isLightMode,
  };
}

/**
 * Theme provider component
 */
interface ThemeProviderProps {
  children: React.ReactNode;
}

export function ThemeProvider({ children }: ThemeProviderProps) {
  const { isDarkMode } = useTheme();
  
  // Create theme with light/dark mode
  const theme = useMemo(
    () =>
      createTheme({
        palette: {
          mode: isDarkMode ? 'dark' : 'light',
          primary: {
            main: isDarkMode ? '#90caf9' : '#1976d2',
          },
          secondary: {
            main: isDarkMode ? '#f48fb1' : '#d81b60',
          },
          background: {
            default: isDarkMode ? '#121212' : '#f5f5f5',
            paper: isDarkMode ? '#1e1e1e' : '#ffffff',
          },
        },
        typography: {
          fontFamily: [
            '-apple-system',
            'BlinkMacSystemFont',
            '"Segoe UI"',
            'Roboto',
            '"Helvetica Neue"',
            'Arial',
            'sans-serif',
            '"Apple Color Emoji"',
            '"Segoe UI Emoji"',
            '"Segoe UI Symbol"',
          ].join(','),
        },
        components: {
          MuiButton: {
            styleOverrides: {
              root: {
                textTransform: 'none',
                borderRadius: 8,
              },
            },
          },
          MuiCard: {
            styleOverrides: {
              root: {
                borderRadius: 12,
                boxShadow: '0 4px 20px 0 rgba(0,0,0,0.05)',
                transition: 'transform 0.2s, box-shadow 0.2s',
                '&:hover': {
                  transform: 'translateY(-2px)',
                  boxShadow: '0 6px 24px 0 rgba(0,0,0,0.1)',
                },
              },
            },
          },
        },
      }),
    [isDarkMode]
  );

  return <MuiThemeProvider theme={theme}>{children}</MuiThemeProvider>;
}

export default useTheme;
