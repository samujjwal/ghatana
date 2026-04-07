import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    preserveSymlinks: true,
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@ghatana/design-system': path.resolve(__dirname, '../../../../platform/typescript/design-system/src/index.ts'),
      '@ghatana/platform-utils': path.resolve(__dirname, '../../../../platform/typescript/foundation/platform-utils/src/index.ts'),
      '@ghatana/theme': path.resolve(__dirname, '../../../../platform/typescript/theme/src/index.ts'),
      '@ghatana/theme/provider': path.resolve(__dirname, '../../../../platform/typescript/theme/src/provider.tsx'),
      '@ghatana/tokens': path.resolve(__dirname, '../../../../platform/typescript/tokens/src/index.ts'),
      'react-router': path.resolve(__dirname, './node_modules/react-router'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/__tests__/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
});