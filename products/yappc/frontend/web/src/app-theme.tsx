/**
 * App Theme Provider Component
 *
 * Unified theme provider that bridges @ghatana/theme (CSS variables) and MUI ThemeProvider.
 * Ensures consistent theming across all components — both @ghatana design-system and MUI.
 *
 * @doc.type component
 * @doc.purpose Unified theme provider
 * @doc.layer ui
 * @doc.pattern Provider
 */

import { ThemeProvider as GhatanaThemeProvider } from '@ghatana/theme';
import { Provider as JotaiProvider } from 'jotai';
import React from 'react';

import { MuiThemeConnector } from '@yappc/product-theme/mui-bridge';

interface AppThemeProviderProps {
  children: React.ReactNode;
}

/**
 * Wraps the app with:
 * 1. Jotai store — shared atom state
 * 2. @ghatana/theme — CSS variables + light/dark/system detection
 * 3. MuiThemeConnector — syncs MUI theme to the resolved platform theme
 */
function ThemedContent({ children }: { children: React.ReactNode }): React.ReactElement {
  return (
    <GhatanaThemeProvider
      defaultTheme="system"
      attribute="class"
      enableStorage={true}
      enableSystem={true}
    >
      <MuiThemeConnector>{children}</MuiThemeConnector>
    </GhatanaThemeProvider>
  );
}

/**
 * Root app theme provider.
 *
 * @doc.type component
 * @doc.purpose Root provider for theme + state
 * @doc.layer ui
 * @doc.pattern Provider
 */
export default function AppThemeProvider({
  children,
}: AppThemeProviderProps): React.ReactElement {
  return (
    <JotaiProvider>
      <ThemedContent>{children}</ThemedContent>
    </JotaiProvider>
  );
}
