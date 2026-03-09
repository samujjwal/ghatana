// Shared Vitest configuration for DCMAAR Extension
// Provides consistent testing setup across all test suites

import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

export default defineConfig({
  test: {
    // Test environment
    environment: 'jsdom',
    // Use the repository's canonical unit setup file directly
    setupFiles: ['./__tests__/setup.ts'],

    // Global test configuration
    globals: true,
    clearMocks: true,
    restoreMocks: true,

    // Coverage configuration
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      reportsDirectory: './coverage',
      exclude: [
        'node_modules/**',
        'dist/**',
        'coverage/**',
        '**/*.d.ts',
        '**/*.config.{js,ts}',
        'scripts/**',
        'tests/**',
        'playwright-report/**',
        'test-results/**',
      ],
      // Coverage thresholds
      thresholds: {
        global: {
          branches: 80,
          functions: 80,
          lines: 80,
          statements: 80,
        },
      },
    },

    // Test inclusion/exclusion
    include: [
      'src/**/*.{test,spec}.{js,ts,tsx}',
      'tests/unit/**/*.{test,spec}.{js,ts,tsx}',
      'tests/integration/**/*.{test,spec}.{js,ts,tsx}',
    ],
    exclude: [
      'node_modules/**',
      'dist/**',
      'playwright-report/**',
      'test-results/**',
      'tests/e2e/**',
    ],

    // Test timeouts
    testTimeout: 10000,
    hookTimeout: 10000,

    // Reporter configuration
    reporters: ['verbose'],

    // Watch mode configuration
    watch: false,

    // Mock configuration
    // Note: server.deps.inline moved to resolve.alias or optimizeDeps in Vitest 4+
    server: {
      deps: {
        inline: [
          // Inline dependencies that need to be transformed
          'webextension-polyfill',
          'extension-shared',
        ],
      },
    },
  },

  resolve: {
    alias: {
      '@': resolve(__dirname, '../src'),
      '@/types': resolve(__dirname, '../src/types'),
      '@/utils': resolve(__dirname, '../src/shared/utils'),
      '@/config': resolve(__dirname, '../src/shared/config'),
      '@/platform': resolve(__dirname, '../src/platform'),
      '@/adapters': resolve(__dirname, '../src/adapters'),
      '@/collectors': resolve(__dirname, '../src/collectors'),
      '@/transport': resolve(__dirname, '../src/transport'),
      '@/storage': resolve(__dirname, '../src/storage'),
      'extension-shared': resolve(__dirname, '../../../packages/extension-shared/src/index.ts'),
      react: resolve(__dirname, '../node_modules/react'),
      'react-dom': resolve(__dirname, '../node_modules/react-dom'),
    },
  },

  define: {
    // Define global constants for tests
    __TEST__: true,
    __DEV__: true,
  },
});
