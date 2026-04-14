/**
 * Minimal @mui/material mock for the web app test environment.
 *
 * The web app uses @ghatana/design-system, not MUI directly.
 * This mock prevents resolution failures when loading @yappc/product-theme/mui-bridge
 * (which wraps MUI ThemeProvider) during unit tests.
 */

import React from 'react';

export const ThemeProvider = ({ children }: React.PropsWithChildren): React.ReactElement =>
  React.createElement(React.Fragment, null, children);

export const CssBaseline = (): null => null;

export const createTheme = (options?: Record<string, unknown>): Record<string, unknown> =>
  options ?? {};
