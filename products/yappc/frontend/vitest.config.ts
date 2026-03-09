import { resolve } from 'path';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Temporarily cast plugins to any to avoid Vite v5/v7 type mismatch while we align versions
const plugins: any = [react()];

// Use a single .tsbuildinfo file for the root project
// prefixed with _ to indicate this value is intentionally retained for CI
// tooling but not referenced directly in code (satisfies unused-var rule)
const _tsBuildInfoFile = './node_modules/.cache/tsbuildinfo/root.tsbuildinfo';

export default defineConfig({
  plugins,
  build: {
    // Ensure build info goes to a unique location
    outDir: 'dist',
    rollupOptions: {
      output: {
        // Prevent build info conflicts
        entryFileNames: '[name].js',
        chunkFileNames: '[name].js',
        assetFileNames: '[name].[ext]',
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    // Explicitly include only Vitest test files (TypeScript only, no compiled .js)
    include: [
      '**/__tests__/**/*.{test,spec}.{ts,tsx}',
      '**/*.{test,spec}.{ts,tsx}',
    ],
    // Exclude Playwright E2E tests and other non-Vitest files
    exclude: [
      '**/node_modules/**',
      '**/dist/**',
      '**/.{idea,git,cache,output,temp}/**',
      '**/{karma,rollup,webpack,vite,vitest,jest,ava,babel,nyc,cypress,tsup,build}.config.*',
      '**/e2e/**',
      '**/playwright/**',
      '**/*.spec.js', // Exclude .js test files (TypeScript-only project)
      '**/*.test.js', // Exclude .js test files (TypeScript-only project)
    ],
    // no-op: aliases are handled in the top-level resolve section
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      exclude: [
        'node_modules/',
        'vitest.config.ts',
        'vitest.setup.ts',
        '**/*.d.ts',
        '**/*.config.{js,ts}',
        '**/dist/**',
        '**/build/**',
        '**/__tests__/**',
        '**/*.test.{ts,tsx}',
        '**/*.spec.{ts,tsx}',
        // Exclude React UI components from coverage (visual components best tested visually)
        '**/libs/accessibility-audit/src/AccessibilityAuditTool.tsx',
        '**/libs/accessibility-audit/src/AccessibilityReportViewer.tsx',
        '**/libs/accessibility-audit/src/useAccessibilityAudit.ts',
        // Exclude mock/test infrastructure
        '**/mocks/**',
        '**/test/**',
        '**/__mocks__/**',
      ],
      thresholds: {
        lines: 70,
        functions: 70,
        branches: 70,
        statements: 70,
      },
    },
    // Timeouts configuration
    testTimeout: 10000,
    hookTimeout: 10000,
    teardownTimeout: 10000,

    // Test isolation - ensures tests don't affect each other
    isolate: true,

    // Parallelization configuration
    pool: 'threads',
    poolOptions: {
      threads: {
        singleThread: false,
        minThreads: 2,
        maxThreads: 8, // Adjust based on available CPU cores
      },
    },

    // Test retry configuration for flaky tests
    retry: 2, // Retry failed tests up to 2 times

    // Test filtering configuration - these override the top-level include/exclude

    // Test tags for filtering
    typecheck: {
      enabled: true,
      tsconfig: './tsconfig.json',
    },

    // Test filtering options
    testNamePattern: undefined, // Can be set via CLI to filter tests by name pattern

    // Custom test tags for organization
    environmentMatchGlobs: [
      ['**/*.browser.test.{ts,tsx}', 'jsdom'],
      ['**/*.node.test.{ts,tsx}', 'node'],
      ['**/*.perf.test.{ts,tsx}', 'jsdom'],
    ],

    // Reporters configuration
    reporters: [
      'default',
      'html',
      'json',
      'junit',
      [
        'verbose',
        {
          showSummary: true,
          showSuccessfulTests: false,
          showSkippedTests: true,
        },
      ],
    ],
    outputFile: {
      html: './test-results/index.html',
      json: './test-results/results.json',
      junit: './test-results/junit.xml',
    },
  },
  resolve: {
    alias: {
      '@ghatana/yappc-canvas': resolve(__dirname, './libs/canvas/src/index.ts'),
      '@ghatana/yappc-types': resolve(__dirname, './libs/types/src'),
      '@ghatana/yappc-api': resolve(__dirname, './libs/graphql/src'),
      '@ghatana/yappc-mocks': resolve(__dirname, './libs/mocks/src'),
      '@ghatana/yappc-diagram': resolve(__dirname, './libs/diagram/src'),
      '@ghatana/yappc-test-helpers': resolve(__dirname, './libs/test-helpers/src'),
      '@ghatana/ui': resolve(__dirname, '../../../libs/typescript/ui/src'),
      // Use test-time mocks for Konva/react-konva to avoid jsdom canvas issues
      'react-konva': resolve(
        __dirname,
        './libs/test/vitest-mocks/react-konva.ts'
      ),
      konva: resolve(__dirname, './libs/test/vitest-mocks/konva.ts'),
    },
  },
});
