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
    alias: [
      {
        find: '@mui/material/styles',
        replacement: resolve(__dirname, 'src/__mocks__/@mui/material/styles.ts'),
      },
      {
        find: '@mui/material',
        replacement: resolve(__dirname, 'src/__mocks__/@mui/material.ts'),
      },
      { find: '@', replacement: resolve(__dirname, 'src') },
      {
        find: '@ghatana/design-system',
        replacement: resolve(__dirname, '../../../../platform/typescript/design-system/src'),
      },
      { find: '@yappc/core', replacement: resolve(__dirname, '../libs/yappc-core/src') },
      { find: '@yappc/api', replacement: resolve(__dirname, '../libs/api/src') },
      {
        find: '@yappc/devsecops',
        replacement: resolve(__dirname, '../libs/yappc-devsecops/src'),
      },
      { find: '@yappc/auth', replacement: resolve(__dirname, '../libs/yappc-auth/src') },
      {
        find: '@yappc/auth/rbac',
        replacement: resolve(__dirname, '../libs/yappc-auth/src/auth/rbac'),
      },
      { find: '@yappc/chat', replacement: resolve(__dirname, '../libs/yappc-chat/src') },
      { find: '@yappc/collab', replacement: resolve(__dirname, '../libs/collab/src') },
      {
        find: '@yappc/initialization-ui',
        replacement: resolve(__dirname, '../libs/yappc-initialization-ui/src'),
      },
      {
        find: '@yappc/development-ui',
        replacement: resolve(__dirname, '../libs/yappc-development-ui/src'),
      },
      {
        find: '@ghatana/code-editor',
        replacement: resolve(__dirname, '../../../../platform/typescript/code-editor/src'),
      },
      {
        find: '@yappc/state',
        replacement: resolve(__dirname, 'src/__mocks__/@yappc/state.ts'),
      },
      {
        find: '@ghatana/platform-utils',
        replacement: resolve(
          __dirname,
          '../../../../platform/typescript/platform-utils/src'
        ),
      },
      {
        find: '@ghatana/ds-schema',
        replacement: resolve(
          __dirname,
          '../../../../platform/typescript/ds-schema/src/index.ts'
        ),
      },
      {
        find: '@ghatana/ds-registry',
        replacement: resolve(
          __dirname,
          '../../../../platform/typescript/ds-registry/src/index.ts'
        ),
      },
      {
        find: 'clsx',
        replacement: resolve(
          __dirname,
          '../../../../node_modules/.pnpm/clsx@2.1.1/node_modules/clsx/dist/clsx.mjs'
        ),
      },
      {
        find: 'tailwind-merge',
        replacement: resolve(
          __dirname,
          '../../../../node_modules/.pnpm/tailwind-merge@3.5.0/node_modules/tailwind-merge/dist/bundle-mjs.mjs'
        ),
      },
      {
        find: '@ghatana/theme',
        replacement: resolve(__dirname, 'src/__mocks__/@ghatana/theme.ts'),
      },
      {
        find: '@ghatana/theme/provider',
        replacement: resolve(__dirname, 'src/__mocks__/@ghatana/theme.ts'),
      },
      {
        find: '@ghatana/canvas',
        replacement: resolve(__dirname, 'src/__mocks__/@ghatana/canvas.ts'),
      },
      {
        find: '@ghatana/canvas/hybrid',
        replacement: resolve(__dirname, 'src/__mocks__/@ghatana/canvas.ts'),
      },
      {
        find: '@monaco-editor/react',
        replacement: resolve(__dirname, 'src/__mocks__/@monaco-editor/react.ts'),
      },
      {
        find: '@yappc/product-theme/lifecycle-presets',
        replacement: resolve(
          __dirname,
          '../libs/yappc-product-theme/src/lifecycle-presets.ts'
        ),
      },
      {
        find: '@yappc/product-theme/mui-bridge',
        replacement: resolve(__dirname, '../libs/yappc-product-theme/src/mui-bridge.tsx'),
      },
      {
        find: '@yappc/product-theme',
        replacement: resolve(__dirname, '../libs/yappc-product-theme/src/index.ts'),
      },
      {
        find: '@hookform/resolvers/zod',
        replacement: resolve(__dirname, 'src/__mocks__/@hookform/resolvers/zod.ts'),
      },
      {
        find: 'react-hook-form',
        replacement: resolve(__dirname, 'src/__mocks__/react-hook-form.ts'),
      },
      {
        find: '@ghatana/ui-builder/preview',
        replacement: resolve(__dirname, '../../../../platform/typescript/ui-builder/src/preview/index.ts'),
      },
      {
        find: '@ghatana/ui-builder',
        replacement: resolve(__dirname, '../../../../platform/typescript/ui-builder/src/index.ts'),
      },
      { find: '@yappc/ui', replacement: resolve(__dirname, '../libs/yappc-ui/src/index.ts') },
      {
        find: '@capacitor/core',
        replacement: resolve(__dirname, 'src/__mocks__/@capacitor/core.ts'),
      },
      { find: 'react', replacement: resolve(__dirname, 'node_modules/react') },
      {
        find: 'react/jsx-runtime',
        replacement: resolve(__dirname, 'node_modules/react/jsx-runtime'),
      },
      { find: 'react-dom', replacement: resolve(__dirname, 'node_modules/react-dom') },
      {
        find: 'react-dom/client',
        replacement: resolve(__dirname, 'node_modules/react-dom/client'),
      },
    ],
  },
  optimizeDeps: {
    include: ['react', 'react-dom'],
    esbuildOptions: {
      mainFields: ['module', 'main'],
    },
  },
  server: {
    fs: {
      allow: [
        __dirname,
        resolve(__dirname, '../../'),
        resolve(__dirname, '../../../../'),
      ],
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
