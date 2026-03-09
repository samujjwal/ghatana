/**
 * Enhanced Theme Provider
 *
 * Combines MUI theming with our multi-layer theme system.
 * Provides backward compatibility with existing ThemeProvider
 * while adding multi-layer capabilities.
 */

import React, { useMemo } from 'react';

import {
  MultiLayerThemeProvider,
  useMultiLayerTheme
} from './MultiLayerThemeContext';
import { lightTheme, darkTheme } from './theme';

import type {
  MultiLayerThemeProviderProps} from './MultiLayerThemeContext';

/**
 * Inner theme provider that consumes multi-layer context
 * and creates MUI theme
 */
function MuiThemeConnector({ children }: { children: React.ReactNode }) {
  const { mergedThemeOptions, mode } = useMultiLayerTheme();

  const theme = useMemo(() => {
    // Start with base theme (light or dark)
    const baseTheme = mode === 'dark' ? darkTheme : lightTheme;

    // Merge with multi-layer options
    return createTheme(baseTheme, mergedThemeOptions);
  }, [mergedThemeOptions, mode]);

  return (
    <MuiThemeProvider theme={theme}>
      <CssBaseline />
      {children}
    </MuiThemeProvider>
  );
}

/**
 * Enhanced theme provider props
 */
export interface EnhancedThemeProviderProps extends Omit<MultiLayerThemeProviderProps, 'baseThemeOptions'> {
  children: React.ReactNode;
  mode?: 'light' | 'dark';
}

/**
 * Enhanced Theme Provider
 *
 * Provides multi-layer theming with MUI integration.
 *
 * @example Basic usage (backward compatible)
 * ```tsx
 * <EnhancedThemeProvider mode="dark">
 *   <App />
 * </EnhancedThemeProvider>
 * ```
 *
 * @example With brand customization
 * ```tsx
 * <EnhancedThemeProvider
 *   mode="light"
 *   brandThemeOptions={{
 *     palette: {
 *       primary: { main: '#ff0000' }
 *     }
 *   }}
 * >
 *   <App />
 * </EnhancedThemeProvider>
 * ```
 *
 * @example With all layers
 * ```tsx
 * <EnhancedThemeProvider
 *   mode="light"
 *   brandThemeOptions={{ palette: { primary: { main: '#ff0000' } } }}
 *   workspaceThemeOptions={{ typography: { fontFamily: 'Roboto' } }}
 *   appThemeOptions={{ spacing: 8 }}
 * >
 *   <App />
 * </EnhancedThemeProvider>
 * ```
 */
export function EnhancedThemeProvider({
  children,
  mode = 'light',
  brandThemeOptions,
  workspaceThemeOptions,
  appThemeOptions,
}: EnhancedThemeProviderProps) {
  // Use the base theme as the foundation
  const baseTheme = mode === 'dark' ? darkTheme : lightTheme;

  return (
    <MultiLayerThemeProvider
      initialMode={mode}
      baseThemeOptions={baseTheme}
      brandThemeOptions={brandThemeOptions}
      workspaceThemeOptions={workspaceThemeOptions}
      appThemeOptions={appThemeOptions}
    >
      <MuiThemeConnector>{children}</MuiThemeConnector>
    </MultiLayerThemeProvider>
  );
}

// Re-export hooks for convenience
export {
  useMultiLayerTheme,
  useThemeMode,
  useBrandTheme,
  useWorkspaceTheme,
  useAppTheme,
  LayerPriority,
} from './MultiLayerThemeContext';

export type { ThemeLayer, MultiLayerThemeContextValue } from './MultiLayerThemeContext';
