/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

const DESIGN_SYSTEM_MODULES = resolve(
  __dirname,
  '../design-system/node_modules',
);

export default defineConfig({
  resolve: {
    alias: {
      '@ghatana/realtime': resolve(__dirname, './src'),
      // zod is not yet installed directly — use design-system's copy for tests
      zod: resolve(DESIGN_SYSTEM_MODULES, 'zod'),
    },
  },
  test: {
    globals: true,
    environment: 'node',
    setupFiles: [],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts}'],
      exclude: [
        'node_modules/',
        'dist/',
        'src/**/*.d.ts',
        'src/__tests__/**',
        'src/**/*.test.ts',
      ],
      thresholds: {
        lines: 80,
        functions: 80,
        branches: 75,
        statements: 80,
      },
    },
  },
});
