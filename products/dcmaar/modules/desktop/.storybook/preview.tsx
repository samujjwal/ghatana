import type { Preview } from '@storybook/react';
import { ThemeProvider, CssBaseline } from '@mui/material';
import React from 'react';
import { theme } from '../src/theme';

const withThemeProvider = (Story: any) => (
  <ThemeProvider theme={theme}>
    <CssBaseline />
    <div style={{ 
      padding: '24px', 
      background: theme.palette.background.default, 
      minHeight: '100vh' 
    }}>
      <Story />
    </div>
  </ThemeProvider>
);

const preview: Preview = {
  decorators: [withThemeProvider],
  parameters: {
    actions: { argTypesRegex: '^on[A-Z].*' },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/,
      },
    },
  },
};

export default preview;

declare module '@storybook/react' {
  interface Parameters {
    layout?: 'centered' | 'fullscreen' | 'padded' | 'none';
  }
}
