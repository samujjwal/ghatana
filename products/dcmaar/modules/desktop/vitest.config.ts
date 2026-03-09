/**
 * Vitest configuration for desktop application tests.
 * Uses happy-dom for browser API compatibility (IndexedDB, crypto, etc.).
 */

import { defineConfig } from 'vitest/config';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  test: {
    globals: true,
    environment: 'happy-dom',
    setupFiles: [
      './test/setup.ts'
    ],
    typecheck: {
      enabled: false,
    },
    include: [
      'src/**/__tests__/**/*.test.{ts,tsx}',
      'test/unit/**/*.test.{ts,tsx}',
      'test/integration/**/*.test.{ts,tsx}'
    ],
    exclude: [
      'node_modules',
      'dist',
      'test/e2e',
      '**/*.e2e.test.{ts,tsx}'
    ],
    testTimeout: 10000, // 10 second timeout for tests
    hookTimeout: 30000, // 30 second timeout for hooks
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'lcov', 'text-summary', 'clover', 'json-summary'],
      reportsDirectory: './coverage',
      exclude: [
        'node_modules/',
        '**/*.d.ts',
        '**/*.test.{js,jsx,ts,tsx}',
        '**/*.spec.{js,jsx,ts,tsx}',
        '**/__mocks__/**',
        '**/__fixtures__/**',
        'test/**',
        '**/types/**',
        '**/index.ts',
        '**/main.tsx',
        '**/vite-env.d.ts'
      ],
      include: ['src/**/*.{js,jsx,ts,tsx}'],
      all: true,
      // @ts-ignore - These are valid options but not in the type definition
      lines: 80,
      // @ts-ignore
      functions: 80,
      // @ts-ignore
      branches: 80,
      // @ts-ignore
      statements: 80,
      // @ts-ignore
      watermarks: {
        lines: [80, 95],
        functions: [80, 95],
        branches: [80, 95],
        statements: [80, 95],
      },
    },
    logHeapUsage: true,
    logHeapUsageCount: 10,
    restoreMocks: true,
    clearMocks: true,
    mockReset: true,
    testSequencer: './test/sequencer.js',
  },
  resolve: {
    alias: [
      {
        find: '@',
        replacement: path.resolve(__dirname, './src'),
      },
      {
        find: '@test',
        replacement: path.resolve(__dirname, './test'),
      },
      {
        find: '@ghatana/dcmaar-bridge-protocol',
        replacement: path.resolve(
          __dirname,
          '../../core/shared-ts/ts-packages/bridge-protocol/dist'
        ),
      },
    ],
  },
  esbuild: {
    target: 'es2020',
  },
});
