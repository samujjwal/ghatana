import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      input: path.resolve(__dirname, 'lifecycle-index.html'),
    },
  },
  resolve: {
    alias: {
      '@ghatana/product-shell': path.resolve(__dirname, '../../../../platform/typescript/product-shell/src/index.ts'),
      '@flashit/shared-contracts': path.resolve(__dirname, '../../libs/ts/shared/src/contracts/routes.ts'),
    },
  },
});
