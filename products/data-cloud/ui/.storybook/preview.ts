import type { Preview } from '@storybook/react';

/**
 * Storybook preview configuration.
 *
 * @doc.type config
 * @doc.purpose Global Storybook settings and decorators
 */
const preview: Preview = {
  parameters: {
    actions: { argTypesRegex: '^on[A-Z].*' },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    docs: {
      toc: true,
    },
  },
};

export default preview;
