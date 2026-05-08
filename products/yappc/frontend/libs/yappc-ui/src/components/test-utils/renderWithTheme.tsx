import React from 'react';
import { ThemeProvider as PlatformThemeProvider } from '@ghatana/theme';

import ThemeProvider from '../theme/ThemeProvider';

/**
 *
 */
export function renderWithTheme(
  ui: React.ReactNode,
  mode: 'light' | 'dark' = 'light'
) {
  return (
    <PlatformThemeProvider defaultTheme={mode} enableStorage={false}>
      <ThemeProvider mode={mode}>{ui}</ThemeProvider>
    </PlatformThemeProvider>
  );
}
