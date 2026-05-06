import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@ghatana/product-shell': path.resolve(__dirname, '../../../../platform/typescript/product-shell/src/index.ts'),
      '@flashit/shared': path.resolve(__dirname, '../../libs/ts/shared/src/index.ts'),
    },
  },
  test: {
    environment: 'jsdom',
  },
});
