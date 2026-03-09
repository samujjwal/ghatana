import type { StorybookConfig } from '@storybook/react-vite';

/**
 * Storybook configuration for CES Workflow Platform.
 *
 * @doc.type config
 * @doc.purpose Storybook setup and configuration
 */
const config: StorybookConfig = {
  stories: ['../src/**/*.stories.@(ts|tsx|js|jsx)'],
  addons: [
    '@storybook/addon-links',
    '@storybook/addon-a11y',
  ],
  framework: {
    name: '@storybook/react-vite',
    options: {},
  },
  docs: {
    autodocs: 'tag',
  },
};

export default config;
