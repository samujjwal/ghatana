import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    extensions: ['.ts', '.tsx', '.mts', '.cts', '.js', '.jsx', '.mjs', '.cjs', '.json'],
    alias: [
      { find: '@', replacement: path.resolve(__dirname, './src') },
      { find: '@ghatana/design-system', replacement: path.resolve(__dirname, '../design-system/src/index.ts') },
      { find: '@ghatana/platform-utils', replacement: path.resolve(__dirname, '../platform-utils/src/index.ts') },
      { find: '@ghatana/product-shell', replacement: path.resolve(__dirname, '../product-shell/src/index.ts') },
      { find: '@ghatana/api', replacement: path.resolve(__dirname, '../api/src/index.ts') },
      { find: '@ghatana/tokens', replacement: path.resolve(__dirname, '../tokens/src/index.ts') },
      { find: '@ghatana/theme', replacement: path.resolve(__dirname, '../theme/src/index.ts') },
      { find: '@ghatana/i18n', replacement: path.resolve(__dirname, '../i18n/src/index.ts') },
      { find: '@ghatana/artifact-compiler-ts', replacement: path.resolve(__dirname, '../artifact-compiler-ts/src/index.ts') },
      { find: '@ghatana/artifact-contracts', replacement: path.resolve(__dirname, '../artifact-contracts/src/index.ts') },
      { find: '@ghatana/ds-generator/adapters', replacement: path.resolve(__dirname, '../ds-generator/src/adapters/index.ts') },
      { find: '@ghatana/ds-generator', replacement: path.resolve(__dirname, '../ds-generator/src/index.ts') },
      { find: '@ghatana/ds-registry', replacement: path.resolve(__dirname, '../ds-registry/src/index.ts') },
      { find: '@ghatana/ds-schema', replacement: path.resolve(__dirname, '../ds-schema/src/index.ts') },
      { find: '@ghatana/ui-builder/preview', replacement: path.resolve(__dirname, '../ui-builder/src/preview/index.ts') },
      { find: '@ghatana/ui-builder/web', replacement: path.resolve(__dirname, '../ui-builder/src/web/index.ts') },
      { find: '@ghatana/ui-builder/react', replacement: path.resolve(__dirname, '../ui-builder/src/react/index.ts') },
      { find: '@ghatana/ui-builder/testing', replacement: path.resolve(__dirname, '../ui-builder/src/testing/index.ts') },
      { find: '@ghatana/ui-builder', replacement: path.resolve(__dirname, '../ui-builder/src/index.ts') },
      { find: '@ghatana/canvas/hybrid', replacement: path.resolve(__dirname, '../canvas/src/hybrid/index.ts') },
      { find: '@ghatana/canvas', replacement: path.resolve(__dirname, '../canvas/src/index.ts') },
    ],
  },
  test: {
    globals: true,
    environment: 'jsdom',
    testTimeout: 20000,
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
