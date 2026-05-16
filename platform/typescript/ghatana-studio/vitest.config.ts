import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    extensions: ['.ts', '.tsx', '.mts', '.cts', '.js', '.jsx', '.mjs', '.cjs', '.json'],
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@ghatana/design-system': path.resolve(__dirname, '../design-system/src/index.ts'),
      '@ghatana/platform-utils': path.resolve(__dirname, '../platform-utils/src/index.ts'),
      '@ghatana/api': path.resolve(__dirname, '../api/src/index.ts'),
      '@ghatana/tokens': path.resolve(__dirname, '../tokens/src/index.ts'),
      '@ghatana/theme': path.resolve(__dirname, '../theme/src/index.ts'),
      '@ghatana/i18n': path.resolve(__dirname, '../i18n/src/index.ts'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['src/**/__tests__/**/*.test.ts', 'src/**/__tests__/**/*.test.tsx'],
    setupFiles: ['./src/__tests__/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      exclude: ['node_modules/', 'dist/', '**/*.test.ts', '**/*.test.tsx'],
      thresholds: {
        lines: 65,
        functions: 65,
        branches: 60,
        statements: 65,
      },
    },
  },
});
