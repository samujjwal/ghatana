import { resolve } from 'node:path';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
    include: ['src/**/*.{spec,test}.{ts,tsx}'],
    exclude: ['dist', 'node_modules'],
  },
  resolve: {
    alias: {
      '@yappc/core': resolve(__dirname, '../yappc-core/src'),
      '@yappc/core/*': resolve(__dirname, '../yappc-core/src/*'),
      '@yappc/state': resolve(__dirname, '../yappc-state/src'),
      '@yappc/state/*': resolve(__dirname, '../yappc-state/src/*'),
      '@ghatana/platform-utils': resolve(
        __dirname,
        '../../../../../platform/typescript/platform-utils/src'
      ),
    },
  },
});