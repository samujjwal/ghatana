import path from 'path';
import { fileURLToPath } from 'url';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const reactRouterModule = path.resolve(
  __dirname,
  '../../../../platform/typescript/design-system/node_modules/react-router/dist/development/index.mjs',
);
const workspaceAliases = {
  '@': path.resolve(__dirname, './src'),
  '@ghatana/dcmaar-dashboard-core': path.resolve(__dirname, '../../libs/guardian-dashboard-core/src'),
  '@ghatana/design-system': path.resolve(__dirname, '../../../../platform/typescript/design-system/src/index.ts'),
  '@ghatana/tokens': path.resolve(__dirname, '../../../../platform/typescript/tokens/src/index.ts'),
  '@ghatana/theme': path.resolve(__dirname, '../../../../platform/typescript/theme/src/index.ts'),
  'react-router-dom': reactRouterModule,
  'react-router': reactRouterModule,
};

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: workspaceAliases,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    testTimeout: 30_000,
    alias: workspaceAliases,
    setupFiles: ['./src/test/setup.ts'],
    exclude: ['e2e/**', '**/node_modules/**', '**/dist/**'],
    deps: {
      inline: ['react', 'react-dom', 'react-router-dom', 'jotai'],
    },
  },
});
