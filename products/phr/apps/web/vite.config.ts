import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const reactRouterPath = path.dirname(require.resolve('react-router/package.json'));
const reactRouterDomPath = path.dirname(require.resolve('react-router-dom/package.json'));

export default defineConfig({
  plugins: [react()],
  resolve: {
    preserveSymlinks: true,
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@ghatana/design-system': path.resolve(__dirname, '../../../../platform/typescript/design-system/src/index.ts'),
      '@ghatana/product-shell': path.resolve(__dirname, '../../../../platform/typescript/product-shell/src/index.ts'),
      '@ghatana/platform-utils': path.resolve(__dirname, '../../../../platform/typescript/platform-utils/src/index.ts'),
      '@ghatana/theme': path.resolve(__dirname, '../../../../platform/typescript/theme/src/index.ts'),
      '@ghatana/theme/provider': path.resolve(__dirname, '../../../../platform/typescript/theme/src/provider.tsx'),
      '@ghatana/tokens': path.resolve(__dirname, '../../../../platform/typescript/tokens/src/index.ts'),
      'react-router': reactRouterPath,
      'react-router-dom': reactRouterDomPath,
    },
  },
  server: {
    port: 4180,
    strictPort: false,
  },
  preview: {
    port: 4180,
    strictPort: false,
  },
});
