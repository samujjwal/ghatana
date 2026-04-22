import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { CssBaseline } from '@mui/material';
import { ThemeProvider as MuiThemeProvider, useMediaQuery, } from '@mui/material';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, } from 'react';
import { lightTheme, darkTheme } from './theme';
export const ThemeContext = createContext(undefined);
const THEME_STORAGE_KEY = 'app-theme-mode';
export function ThemeProvider({ children, defaultMode = 'system', storageKey = THEME_STORAGE_KEY, disableSystemPreference = false, }) {
    const prefersDarkMode = useMediaQuery('(prefers-color-scheme: dark)');
    const [mode, setMode] = useState(() => {
        if (typeof window !== 'undefined') {
            const savedMode = localStorage.getItem(storageKey);
            if (savedMode)
                return savedMode;
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
            if (previousMode === 'light')
                return 'dark';
            if (previousMode === 'dark')
                return 'system';
            return 'light';
        });
    }, []);
    const contextValue = useMemo(() => ({
        mode,
        theme,
        setMode,
        toggleMode,
    }), [mode, theme, toggleMode]);
    return (_jsx(ThemeContext.Provider, { value: contextValue, children: _jsxs(MuiThemeProvider, { theme: theme, children: [_jsx(CssBaseline, {}), children] }) }));
}
export function useTheme() {
    const context = useContext(ThemeContext);
    if (!context) {
        throw new Error('useTheme must be used within ThemeProvider');
    }
    return context;
}
export default ThemeProvider;
