export default {
  stories: [
    '../libs/ui/src/**/*.mdx',
    '../libs/ui/src/**/*.stories.@(mjs|ts|tsx)',
    '../libs/canvas/src/**/*.stories.@(mjs|ts|tsx)',
    '../libs/diagram/src/**/*.stories.@(mjs|ts|tsx)',
    '../libs/designer/src/**/*.stories.@(mjs|ts|tsx)',
    '../apps/web/src/**/*.stories.@(mjs|ts|tsx)',
  ],
  addons: ['@storybook/addon-links', '@storybook/addon-a11y', '@storybook/addon-themes', '@storybook/addon-docs'],
  framework: { name: '@storybook/react-vite', options: {} },
};
