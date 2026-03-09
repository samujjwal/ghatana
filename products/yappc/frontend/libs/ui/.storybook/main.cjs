const { dirname, join } = require('path');
const { mergeConfig } = require('vite');

const config = {
  stories: [
    // UI library stories
    '../src/**/*.mdx',
    '../src/**/*.stories.@(mjs|ts|tsx)',
    // Canvas library stories - EXCLUDING interactions stories that cause ReactFlow infinite loops
    '../../canvas/src/**/*.stories.@(mjs|ts|tsx)',
    '!../../canvas/src/**/*.interactions.stories.@(mjs|ts|tsx)',
    // Diagram library stories
    '../../diagram/src/**/*.stories.@(mjs|ts|tsx)',
    // Designer library stories
    '../../designer/src/**/*.stories.@(mjs|ts|tsx)',
    // EXCLUDED: Web app stories - apps/web components have store side-effects that cause infinite
    // re-renders in Storybook preview. Store subscriptions trigger on mount, causing a cascade of
    // updates. This is expected behavior for app-level components and they should be tested separately.
    // '../../../apps/web/src/**/*.stories.@(mjs|ts|tsx)',
  ],

  addons: [
    getAbsolutePath('@storybook/addon-links'),
    getAbsolutePath('@storybook/addon-a11y'),
    getAbsolutePath('@storybook/addon-themes'),
    getAbsolutePath('@storybook/addon-docs'),
  ],

  framework: {
    name: getAbsolutePath('@storybook/react-vite'),
    options: {},
  },

  viteFinal: async (config) => {
    return mergeConfig(config, {
      resolve: {
        alias: {
          '@yappc/ui': join(__dirname, '../src'),
          '@yappc/canvas': join(__dirname, '../../canvas/src'),
          '@yappc/diagram': join(__dirname, '../../diagram/src'),
          '@yappc/designer': join(__dirname, '../../designer/src'),
          'vitest': join(__dirname, './vitest-stub.js'),
        },
      },
      ssr: {
        // Prevent Vite from pre-bundling packages that include "use client" directives
        // which cause transform errors during Storybook build
        noExternal: [
          '@base-ui-components/react',
          '@base-ui-components/utils',
          '@radix-ui/react-dialog',
          '@radix-ui/react-portal',
          '@radix-ui/react-focus-scope',
          '@radix-ui/react-dismissable-layer',
          '@radix-ui/react-presence',
          'framer-motion',
          '@mui/material',
          '@mui/system',
          '@mui/styled-engine',
          '@mui/utils',
          '@mui/private-theming',
          '@mui/icons-material',
        ],
      },
    });
  },
};

module.exports = config;

function getAbsolutePath(value) {
  return dirname(require.resolve(join(value, 'package.json')));
}
