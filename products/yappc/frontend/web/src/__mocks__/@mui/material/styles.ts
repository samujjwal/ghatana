/**
 * Minimal @mui/material/styles mock for Vitest.
 *
 * The web app test environment only needs these exports to satisfy
 * @yappc/ui theme re-exports while shared MUI providers are mocked out.
 */

import React from 'react';

export type Theme = Record<string, unknown>;

export const createTheme = (options?: Theme): Theme => options ?? {};

export const ThemeProvider = ({ children }: React.PropsWithChildren): React.ReactElement =>
  React.createElement(React.Fragment, null, children);

export const useTheme = (): Theme => ({});

export const alpha = (color: string, value: number): string => {
  if (!Number.isFinite(value)) {
    return color;
  }

  const opacity = Math.max(0, Math.min(1, value));
  return `${color}${Math.round(opacity * 255)
    .toString(16)
    .padStart(2, '0')}`;
};