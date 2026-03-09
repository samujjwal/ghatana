import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig({
  resolve: {
    alias: {
      // Add any aliases used in your project
      '@': resolve(__dirname, '../src'),
      // Add other aliases as needed
    },
  },
  // Add any other Vite configuration needed for Storybook
});
