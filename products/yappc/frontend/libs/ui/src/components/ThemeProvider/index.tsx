/**
 * Theme Provider Component
 * Provides theme context and manages light/dark mode
 * Moved from NL Reporting to shared UI library
 */

'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';
import { useAtom, WritableAtom } from 'jotai';
import { atom } from 'jotai';
import { StateManager } from '@ghatana/yappc-ui';

type Theme = 'light' | 'dark' | 'system';
type ThemeWithoutSystem = 'light' | 'dark';

const baseThemeAtomRef = StateManager.getAtom<ThemeWithoutSystem>('store:theme');

if (!baseThemeAtomRef) {
  throw new Error('[ThemeProvider] Expected "store:theme" atom to be registered in StateManager');
}

// Create a derived atom that handles the 'system' theme
export const themeAtom = atom(
  (get) => {
    const theme = get(baseThemeAtomRef as WritableAtom<ThemeWithoutSystem, [Theme], void>);
    return theme;
  },
  (get, set, update: Theme) => {
    // Only update if the theme is different
    const currentTheme = get(baseThemeAtomRef as WritableAtom<ThemeWithoutSystem, [Theme], void>);
    if (currentTheme !== update) {
      set(baseThemeAtomRef as WritableAtom<ThemeWithoutSystem, [Theme], void>, 
          update === 'system' 
            ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
            : update as ThemeWithoutSystem
      );
    }
  }
);

interface ThemeContextType {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  isDark: boolean;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

interface ThemeProviderProps {
  children: React.ReactNode;
  defaultTheme?: Theme;
  storageKey?: string;
}

export function ThemeProvider({
  children,
  defaultTheme = 'system',
  storageKey = 'yappc:theme',
}: ThemeProviderProps) {
  const [theme, setTheme] = useAtom(themeAtom);
  const [systemTheme, setSystemTheme] = useState<ThemeWithoutSystem>(
    window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  );
  const [currentTheme, setCurrentTheme] = useState<Theme>('system');

  const isDark = theme === 'dark' || (currentTheme === 'system' && systemTheme === 'dark');

  // Handle theme changes
  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    
    const handleSystemThemeChange = (e: MediaQueryListEvent) => {
      const newTheme = e.matches ? 'dark' : 'light';
      setSystemTheme(newTheme);
      
      if (currentTheme === 'system') {
        const root = window.document.documentElement;
        root.classList.remove('light', 'dark');
        root.classList.add(newTheme);
      }
    };
    
    // Set initial theme
    const root = window.document.documentElement;
    root.classList.remove('light', 'dark');
    
    if (currentTheme === 'system') {
      const systemTheme = mediaQuery.matches ? 'dark' : 'light';
      root.classList.add(systemTheme);
      setSystemTheme(systemTheme);
    } else {
      root.classList.add(currentTheme);
    }
    
    mediaQuery.addEventListener('change', handleSystemThemeChange);
    
    return () => {
      mediaQuery.removeEventListener('change', handleSystemThemeChange);
    };
  }, [theme]);

  const toggleTheme = () => {
    const newTheme = theme === 'light' ? 'dark' : 'light';
    setCurrentTheme(newTheme);
    setTheme(newTheme);
  };

  const handleSetTheme = (newTheme: Theme) => {
    setCurrentTheme(newTheme);
    setTheme(newTheme);
  };

  const value: ThemeContextType = {
    theme: currentTheme,
    setTheme: handleSetTheme,
    isDark,
    toggleTheme,
  };

  return (
    <ThemeContext.Provider value={value}>
      {children}
    </ThemeContext.Provider>
  );
}

export const useTheme = () => {
  const context = useContext(ThemeContext);
  
  if (context === undefined) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  
  return context;
};
