import { resolve } from 'path';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const plugins: any = [react()];

export default defineConfig({
  plugins,
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    include: ['src/**/__tests__/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['**/node_modules/**', '**/dist/**'],
  },
  resolve: {
    alias: {
      'yappc-state': resolve(__dirname, './src'),
      'yappc-core': resolve(__dirname, '../yappc-core/src'),
      '@ghatana/ui-builder': resolve(
        __dirname,
        '../../../../../platform/typescript/ui-builder/src',
      ),
      '@ghatana/state': resolve(
        __dirname,
        '../../../../../platform/typescript/state/src',
      ),
    },
  },
});
