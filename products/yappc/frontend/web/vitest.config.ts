/// <reference types="vitest" />
import { resolve } from 'path';

import { defineConfig } from 'vitest/config';

// Minimal Vitest configuration to avoid version conflicts
export default defineConfig({
  root: __dirname,
  resolve: {
    // Enforce single React instance and path aliases
    extensions: ['.js', '.ts', '.tsx', '.json'],
    dedupe: ['react', 'react-dom'],
    alias: {
      '@': resolve(__dirname, 'src'),
      '@ghatana/design-system': resolve(__dirname, '../../../../platform/typescript/design-system/src'),
      '@yappc/api': resolve(__dirname, '../libs/api/src'),
      '@yappc/auth': resolve(__dirname, '../libs/auth/src'),
      '@yappc/collab': resolve(__dirname, '../libs/collab/src'),
      '@yappc/code-editor': resolve(__dirname, '../libs/code-editor/src'),
      '@yappc/state': resolve(__dirname, 'src/__mocks__/@yappc/state.ts'),
      '@yappc/testing': resolve(__dirname, '../libs/testing/src'),
      '@ghatana/platform-utils': resolve(
        __dirname,
        '../../../../platform/typescript/foundation/platform-utils/src'
      ),
      '@ghatana/theme': resolve(__dirname, 'src/__mocks__/@ghatana/theme.ts'),
      '@ghatana/theme/provider': resolve(__dirname, 'src/__mocks__/@ghatana/theme.ts'),
      '@ghatana/canvas': resolve(__dirname, 'src/__mocks__/@ghatana/canvas.ts'),
      '@ghatana/canvas/hybrid': resolve(__dirname, 'src/__mocks__/@ghatana/canvas.ts'),
      '@monaco-editor/react': resolve(__dirname, 'src/__mocks__/@monaco-editor/react.ts'),
      '@ghatana/canvas/hybrid': resolve(__dirname, 'src/__mocks__/@ghatana/canvas.ts'),
      react: resolve(__dirname, 'node_modules/react'),
      'react/jsx-runtime': resolve(__dirname, 'node_modules/react/jsx-runtime'),
      'react-dom': resolve(__dirname, 'node_modules/react-dom'),
      'react-dom/client': resolve(__dirname, 'node_modules/react-dom/client'),
    },
  },
  optimizeDeps: {
    include: ['react', 'react-dom'],
    esbuildOptions: {
      mainFields: ['module', 'main'],
    },
  },
  server: {
    fs: {
      allow: [__dirname, resolve(__dirname, '../../')],
    },
  },
  test: {
    // Basic test configuration
    globals: true,
    environment: 'jsdom',
    setupFiles: 'src/test-utils/setup.ts',

    // Test file patterns
    include: ['**/*.{test,spec}.{ts,tsx}'],
    exclude: [
      '**/node_modules/**',
      '**/dist/**',
      '**/.next/**',
      '**/e2e/**',
      '**/cypress/**',
      '**/playwright-report/**',
      '**/test-results/**',
    ],

    // Coverage configuration
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      exclude: [
        '**/node_modules/**',
        '**/dist/**',
        '**/coverage/**',
        '**/*.d.ts',
        '**/*.config.*',
        '**/test-utils/**',
        '**/__mocks__/**',
        '**/types/**',
        '**/src/main.tsx',
        '**/src/App.tsx',
      ],
      thresholds: {
        lines: 40,
        functions: 40,
        branches: 35,
        statements: 40,
      },
    },

    // Test timeout
    testTimeout: 10000,

    // Environment setup - use API Gateway port
    environmentOptions: {
      jsdom: {
        url: 'http://localhost:7002',
      },
    },

    // Disable watch mode by default
    watch: false,

    // Test execution options
    pool: 'threads',
  },
});
