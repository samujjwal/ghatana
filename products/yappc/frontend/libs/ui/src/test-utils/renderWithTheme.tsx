import React from 'react';

import ThemeProvider from '../theme/ThemeProvider';

/**
 *
 */
export function renderWithTheme(ui: React.ReactNode, mode: 'light' | 'dark' = 'light') {
  return (
    <ThemeProvider mode={mode}>
      {ui}
    </ThemeProvider>
  );
}
