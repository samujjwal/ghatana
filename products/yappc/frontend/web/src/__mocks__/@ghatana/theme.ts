/**
 * Mock for @ghatana/theme in tests.
 * Provides a ThemeProvider that just renders children and a useTheme that
 * returns a stable default context without requiring an actual provider.
 */
import React from 'react';

export interface ThemeContextValue {
  theme: 'light' | 'dark' | 'auto';
  resolvedTheme: 'light' | 'dark';
  toggleTheme: () => void;
  setTheme: (t: 'light' | 'dark' | 'auto') => void;
}

const defaultContext: ThemeContextValue = {
  theme: 'light',
  resolvedTheme: 'light',
  toggleTheme: () => {},
  setTheme: () => {},
};

export const ThemeProvider = ({ children }: { children: React.ReactNode }) =>
  React.createElement(React.Fragment, null, children);

export function useTheme(): ThemeContextValue {
  return defaultContext;
}

export type ThemeMode = 'light' | 'dark' | 'auto';
export type ResolvedTheme = 'light' | 'dark';
