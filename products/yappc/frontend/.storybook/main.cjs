const { mergeConfig } = require('vite');
const path = require('path');

module.exports = {
  stories: [
    '../libs/ui/src/**/*.mdx',
    '../libs/ui/src/**/*.stories.@(mjs|ts|tsx)',
    '../libs/canvas/src/**/*.stories.@(mjs|ts|tsx)',
    '../libs/diagram/src/**/*.stories.@(mjs|ts|tsx)',
    '../libs/designer/src/**/*.stories.@(mjs|ts|tsx)',
    '../apps/web/src/**/*.stories.@(mjs|ts|tsx)',
  ],
  addons: [
    '@storybook/addon-links',
    '@storybook/addon-a11y',
    '@storybook/addon-themes',
    '@storybook/addon-docs',
    '@storybook/addon-essentials',
  ],
  framework: {
    name: '@storybook/react-vite',
    options: {},
  },
  async viteFinal(config) {
    return mergeConfig(config, {
      resolve: {
        alias: {
          // Project-specific aliases matching main Vite config
          '@yappc/canvas/examples/routes': path.resolve(__dirname, '../libs/canvas/examples/routes'),
          '@yappc/canvas/examples': path.resolve(__dirname, '../libs/canvas/examples'),
          '@yappc/canvas': path.resolve(__dirname, '../libs/canvas/src'),
          '@yappc/types': path.resolve(__dirname, '../libs/types/src'),
          '@yappc/store': path.resolve(__dirname, '../libs/store/src'),
          '@yappc/graphql': path.resolve(__dirname, '../libs/graphql/src'),
          '@yappc/mocks': path.resolve(__dirname, '../libs/mocks/src'),
          '@yappc/ui': path.resolve(__dirname, '../libs/ui/src'),
          '@yappc/websocket': path.resolve(__dirname, '../libs/websocket/src'),
          '@yappc/diagram': path.resolve(__dirname, '../libs/diagram/src'),
          '@yappc/designer': path.resolve(__dirname, '../libs/designer/src'),
          '@': path.resolve(__dirname, '../src'),
        },
      },
    });
  },
  // Add this to handle TypeScript path aliases
  typescript: {
    check: false,
    checkOptions: {},
    reactDocgen: 'react-docgen-typescript',
    reactDocgenTypescriptOptions: {
      shouldExtractLiteralValuesFromEnum: true,
      propFilter: (prop) => (prop.parent ? !/node_modules/.test(prop.parent.fileName) : true),
    },
  },
};
