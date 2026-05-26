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
    extensions: ['.ts', '.tsx', '.mjs', '.js', '.mts', '.jsx', '.json'],
    alias: {
      'yappc-config-schema': path.resolve(__dirname, '../config-schema/src/index.ts'),
      'yappc-core': path.resolve(__dirname, '../yappc-core/src'),
    },
  },
});
