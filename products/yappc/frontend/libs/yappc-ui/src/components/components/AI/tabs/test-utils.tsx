/* eslint-disable import/order */
import React from 'react';
import { render as rtlRender, type RenderResult } from '@testing-library/react';
import { ThemeProvider as PlatformThemeProvider } from '@ghatana/theme/provider';
import { ThemeProvider as AppThemeProvider } from '../../../theme/ThemeProvider';

/**
 * Render helper that wraps components in MUI ThemeProvider for tests.
 */
export function renderWithTheme(ui: React.ReactElement): RenderResult {
  return rtlRender(
    <PlatformThemeProvider defaultTheme="light">
      <AppThemeProvider defaultMode="light">{ui}</AppThemeProvider>
    </PlatformThemeProvider>
  );
}

// Backwards-compatible named export expected by tests
export const render = renderWithTheme;

export default renderWithTheme;
