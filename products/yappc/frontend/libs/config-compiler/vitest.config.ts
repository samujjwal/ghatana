import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: [
        'node_modules/',
        'dist/',
        '**/*.test.ts',
        '**/*.test.tsx',
        '__tests__/',
      ],
    },
  },
  resolve: {
    alias: {
      '@yappc/config-schema': path.resolve(__dirname, '../config-schema/src'),
      '@yappc/core': path.resolve(__dirname, '../yappc-core/src'),
    },
  },
});
