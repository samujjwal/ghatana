declare module '@storybook/react' {
  export type Meta<T> = Record<string, unknown>;
  export type StoryObj<T> = Record<string, unknown>;
  const defaultExport: unknown;
  export default defaultExport;
}

declare module '@storybook/addon-links';
declare module '@storybook/addon-essentials';
declare module '@storybook/addon-interactions';
