import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

const workspaceAliases = {
  '@ghatana/design-system': resolve(__dirname, '../../../platform/typescript/capabilities/design-system/src/index.ts'),
  '@ghatana/canvas-core': resolve(__dirname, '../../../platform/typescript/capabilities/canvas-core/src/index.ts'),
  '@ghatana/theme': resolve(__dirname, '../../../platform/typescript/theme/src/index.ts'),
  '@ghatana/tokens': resolve(__dirname, '../../../platform/typescript/tokens/src/index.ts'),
  '@ghatana/utils': resolve(__dirname, '../../../platform/typescript/foundation/platform-utils/src/index.ts'),
  '@ghatana/platform-utils': resolve(__dirname, '../../../platform/typescript/foundation/platform-utils/src/index.ts'),
};

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      ...workspaceAliases,
      '@': resolve(__dirname, 'src'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Heavy third-party libraries split into separate chunks (P7-3d)
          'vendor-react': ['react', 'react-dom', 'react-router'],
          'vendor-editor': ['@monaco-editor/react'],
          'vendor-flow': ['@xyflow/react'],
          'vendor-charts': ['recharts'],
          'vendor-query': ['@tanstack/react-query', 'jotai'],
        },
      },
    },
  },
  server: {
    port: 3001,
    proxy: {
      '/api': 'http://localhost:8090',
      '/admin': 'http://localhost:8090',
      '/events': 'http://localhost:8090',
    },
  },
});
