/**
 * YAPPC MUI bridge — adapts @ghatana/theme (CSS variables) to a MUI ThemeProvider.
 *
 * This is product-specific because:
 * - it imports YAPPC MUI theme objects (lightTheme, darkTheme) from @yappc/ui
 * - it merges platform @ghatana/theme resolved values into MUI palette overrides
 *
 * Consumers: app-theme.tsx in @yappc/web-app
 *
 * @doc.type module
 * @doc.purpose MUI theme bridge for YAPPC app
 * @doc.layer product
 * @doc.pattern Adapter
 */

import { ThemeProvider as MuiThemeProvider, createTheme, CssBaseline } from '@mui/material';
import type { Theme as MuiTheme } from '@mui/material/styles';
import { useTheme as useGhatanaTheme } from '@ghatana/theme';
import React, { useMemo } from 'react';

import { lightTheme, darkTheme } from '@yappc/ui';

/**
 * Reads the resolved theme from @ghatana/theme and syncs a MUI theme accordingly.
 * Place this inside the @ghatana/theme provider tree.
 */
export function MuiThemeConnector({
  children,
}: {
  children: React.ReactNode;
}): React.ReactElement {
  const { resolvedTheme, themeDefinition } = useGhatanaTheme();

  const muiTheme = useMemo((): MuiTheme => {
    const baseTheme = resolvedTheme === 'dark' ? darkTheme : lightTheme;

    const colors = themeDefinition?.computed?.colors;
    if (colors) {
      return createTheme(baseTheme, {
        palette: {
          mode: resolvedTheme === 'dark' ? 'dark' : 'light',
          background: {
            default: (colors.background as Record<string, string>)['default'],
            paper: (colors.background as Record<string, string>)['paper'],
          },
          text: {
            primary: (colors.text as Record<string, string>)['primary'],
            secondary: (colors.text as Record<string, string>)['secondary'],
            disabled: (colors.text as Record<string, string>)['disabled'],
          },
          divider: colors.divider as string,
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
