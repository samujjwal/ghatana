import path from 'path';
import { fileURLToPath } from 'url';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workspaceAliases = {
  '@': path.resolve(__dirname, './src'),
  '@ghatana/dcmaar-dashboard-core': path.resolve(__dirname, '../../libs/guardian-dashboard-core/src'),
  '@ghatana/design-system': path.resolve(__dirname, '../../../../platform/typescript/design-system/src/index.ts'),
  '@ghatana/tokens': path.resolve(__dirname, '../../../../platform/typescript/tokens/src/index.ts'),
  '@ghatana/theme': path.resolve(__dirname, '../../../../platform/typescript/theme/src/index.ts'),
  '@ghatana/utils': path.resolve(__dirname, '../../../../platform/typescript/utils/src/index.ts'),
  'react-router-dom': path.resolve(__dirname, './node_modules/react-router-dom'),
  'react-router': path.resolve(__dirname, './node_modules/react-router'),
};

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: workspaceAliases,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    alias: workspaceAliases,
    setupFiles: ['./src/test/setup.ts'],
    deps: {
      inline: ['react', 'react-dom', 'react-router-dom', 'jotai'],
    },
  },
});
