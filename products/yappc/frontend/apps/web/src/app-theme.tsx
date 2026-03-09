/**
 * App Theme Provider Component
 *
 * <p><b>Purpose</b><br>
 * Unified theme provider that bridges @ghatana/theme (CSS variables) and MUI ThemeProvider.
 * Ensures consistent theming across all components - both @ghatana/ui and @ghatana/yappc-ui (MUI).
 *
 * @doc.type component
 * @doc.purpose Unified theme provider
 * @doc.layer ui
 * @doc.pattern Provider
 */

import { ThemeProvider as GhatanaThemeProvider, useTheme as useGhatanaTheme } from '@ghatana/theme';
import { Provider as JotaiProvider } from 'jotai';
import React, { useMemo } from 'react';

import { lightTheme, darkTheme } from '@ghatana/yappc-ui';

/**
 * Props for AppThemeProvider component
 */
interface AppThemeProviderProps {
  children: React.ReactNode;
}

/**
 * MUI Theme Connector
 * 
 * Reads the resolved theme from @ghatana/theme context and applies the corresponding
 * MUI theme. This ensures MUI components use the same light/dark mode as the rest of the app.
 */
function MuiThemeConnector({ children }: { children: React.ReactNode }) {
  const { resolvedTheme, themeDefinition } = useGhatanaTheme();
  
  // Create MUI theme based on resolved theme mode
  const muiTheme = useMemo(() => {
    const baseTheme = resolvedTheme === 'dark' ? darkTheme : lightTheme;
    
    // Merge with any custom colors from @ghatana/theme if available
    if (themeDefinition?.computed?.colors) {
      const colors = themeDefinition.computed.colors;
      return createTheme(baseTheme, {
        palette: {
          mode: resolvedTheme,
          background: {
            default: colors.background.default,
            paper: colors.background.paper,
          },
          text: {
            primary: colors.text.primary,
            secondary: colors.text.secondary,
            disabled: colors.text.disabled,
          },
          divider: colors.divider,
        },
      });
    }
    
    return baseTheme;
  }, [resolvedTheme, themeDefinition]);

  return (
    <MuiThemeProvider theme={muiTheme}>
      <CssBaseline />
      {children}
    </MuiThemeProvider>
  );
}

/**
 * Inner component that provides both theme systems
 */
function ThemedContent({ children }: { children: React.ReactNode }) {
  return (
    <GhatanaThemeProvider 
      defaultTheme="system"
      attribute="class"
      enableStorage={true}
      enableSystem={true}
    >
      <MuiThemeConnector>
        {children}
      </MuiThemeConnector>
    </GhatanaThemeProvider>
  );
}

/**
 * App Theme Provider component
 *
 * Provides:
 * - Jotai context for state management
 * - @ghatana/theme for CSS variables and theme mode
 * - MUI ThemeProvider for Material UI components
 * 
 * This unified approach ensures:
 * 1. Single source of truth for theme mode (light/dark/system)
 * 2. CSS variables from @ghatana/theme for custom components
 * 3. MUI theme for Material UI components
 * 4. Automatic sync between both theme systems
 */
export default function AppThemeProvider({ children }: AppThemeProviderProps) {
  return (
    <JotaiProvider>
      <ThemedContent>{children}</ThemedContent>
    </JotaiProvider>
  );
}
