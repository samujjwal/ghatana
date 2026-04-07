import { CssBaseline } from '@mui/material';
import {
  createTheme,
  ThemeProvider as MuiThemeProvider,
} from '@mui/material/styles';
import React, { useMemo } from 'react';

import {
  LayerPriority,
  MultiLayerThemeProvider,
  useAppTheme,
  useBrandTheme,
  useMultiLayerTheme,
  useThemeMode,
  useWorkspaceTheme,
} from './MultiLayerThemeContext';
import type {
  MultiLayerThemeContextValue,
  MultiLayerThemeProviderProps,
  ThemeLayer,
} from './MultiLayerThemeContext';
import { darkTheme, lightTheme } from './theme';

function MuiThemeConnector({ children }: { children: React.ReactNode }) {
  const { mergedThemeOptions, mode } = useMultiLayerTheme();

  const theme = useMemo(() => {
    const baseTheme = mode === 'dark' ? darkTheme : lightTheme;
    return createTheme(baseTheme, mergedThemeOptions);
  }, [mergedThemeOptions, mode]);

  return (
    <MuiThemeProvider theme={theme}>
      <CssBaseline />
      {children}
    </MuiThemeProvider>
  );
}

export interface EnhancedThemeProviderProps extends Omit<
  MultiLayerThemeProviderProps,
  'baseThemeOptions'
> {
  children: React.ReactNode;
  mode?: 'light' | 'dark';
}

export function EnhancedThemeProvider({
  children,
  mode = 'light',
  brandThemeOptions,
  workspaceThemeOptions,
  appThemeOptions,
}: EnhancedThemeProviderProps) {
  const baseTheme = mode === 'dark' ? darkTheme : lightTheme;

  return (
    <MultiLayerThemeProvider
      initialMode={mode}
      baseThemeOptions={baseTheme as unknown as Record<string, unknown>}
      brandThemeOptions={brandThemeOptions}
      workspaceThemeOptions={workspaceThemeOptions}
      appThemeOptions={appThemeOptions}
    >
      <MuiThemeConnector>{children}</MuiThemeConnector>
    </MultiLayerThemeProvider>
  );
}

export {
  LayerPriority,
  useAppTheme,
  useBrandTheme,
  useMultiLayerTheme,
  useThemeMode,
  useWorkspaceTheme,
};

export type { MultiLayerThemeContextValue, ThemeLayer };
