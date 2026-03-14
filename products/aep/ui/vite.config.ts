import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

const workspaceAliases = {
  '@ghatana/design-system': resolve(__dirname, '../../../platform/typescript/design-system/src/index.ts'),
  '@ghatana/flow-canvas': resolve(__dirname, '../../../platform/typescript/canvas/flow-canvas/src/index.ts'),
  '@ghatana/theme': resolve(__dirname, '../../../platform/typescript/theme/src/index.ts'),
  '@ghatana/tokens': resolve(__dirname, '../../../platform/typescript/tokens/src/index.ts'),
  '@ghatana/utils': resolve(__dirname, '../../../platform/typescript/utils/src/index.ts'),
};

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      ...workspaceAliases,
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3001,
    proxy: {
      '/api': 'http://localhost:8081',
      '/admin': 'http://localhost:8081',
    },
  },
});
