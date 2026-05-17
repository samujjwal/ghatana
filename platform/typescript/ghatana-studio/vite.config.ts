import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@ghatana/platform-utils': path.resolve(__dirname, '../platform-utils/src/index.ts'),
      '@ghatana/product-shell': path.resolve(__dirname, '../product-shell/src/index.ts'),
      '@ghatana/tokens': path.resolve(__dirname, '../tokens/src/index.ts'),
      '@ghatana/theme': path.resolve(__dirname, '../theme/src/index.ts'),
    },
  },
  server: {
    port: 3000,
    open: true,
  },
});
