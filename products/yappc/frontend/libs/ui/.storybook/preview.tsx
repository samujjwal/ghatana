import type { Preview } from '@storybook/react-vite';
import { Provider as JotaiProvider, createStore } from 'jotai';
import { useMemo } from 'react';
import { ThemeProvider } from '../src/theme/ThemeContext';

const preview: Preview = {
  parameters: {
    actions: { argTypesRegex: '^on[A-Z].*' },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    backgrounds: {
      options: {
        light: {
          name: 'light',
          value: '#ffffff',
        },

        dark: {
          name: 'dark',
          value: '#212121',
        }
      }
    },
    // Disable testing features in Storybook to avoid vitest/jest conflicts
    test: {
      globals: false,
    },
  },

  decorators: [
    (Story) => {
      // Create a fresh Jotai store for each story to ensure isolation
      // This prevents state conflicts between stories and infinite loops.
      // Each story gets its own isolated store instance.
      const store = useMemo(() => createStore(), []);

      return (
        <JotaiProvider store={store}>
          <ThemeProvider>
            <div style={{ padding: '2rem' }}>
              <Story />
            </div>
          </ThemeProvider>
        </JotaiProvider>
      );
    },
  ],

  tags: ['autodocs'],

  initialGlobals: {
    backgrounds: {
      value: 'light'
    }
  }
};

export default preview;
