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
      '**/*compat-migration.test.{ts,tsx}',
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

    // Unit tests must fail loudly; quarantine handling belongs in explicit reruns, not default CI.
    retry: 0,

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
      '@': resolve(__dirname, './web/src'),
      'yappc-api': resolve(__dirname, './libs/api/src'),
      'yappc-ai': resolve(__dirname, './libs/yappc-ai/src'),
      'yappc-devsecops': resolve(__dirname, './libs/yappc-devsecops/src'),
      'yappc-auth': resolve(__dirname, './libs/yappc-auth/src'),
      'yappc-auth/rbac': resolve(__dirname, './libs/yappc-auth/src/auth/rbac'),
      'yappc-chat': resolve(__dirname, './libs/yappc-chat/src'),
      'yappc-collab': resolve(__dirname, './libs/collab/src'),
      'yappc-core/testing/mocks/faker-shim': resolve(
        __dirname,
        './test-utils/faker-shim.ts'
      ),
      'yappc-core': resolve(__dirname, './libs/yappc-core/src'),
      '@ghatana/yappc-ide': resolve(__dirname, './libs/ide/src'),
      'yappc-initialization-ui': resolve(__dirname, './libs/yappc-initialization-ui/src'),
      '@ghatana/yappc-development-ui': resolve(__dirname, './libs/yappc-development-ui/src'),
      'yappc-product-theme': resolve(__dirname, './libs/yappc-product-theme/src'),
      'yappc-state': resolve(__dirname, './libs/yappc-state/src'),
      'yappc-ui': resolve(__dirname, './libs/yappc-ui/src'),
      '@ghatana/canvas': resolve(__dirname, '../../../platform/typescript/canvas/src'),
      '@ghatana/code-editor': resolve(__dirname, '../../../platform/typescript/code-editor/src'),
      '@ghatana/ui-builder': resolve(__dirname, '../../../platform/typescript/ui-builder/src/index.ts'),
      '@ghatana/design-system': resolve(
        __dirname,
        '../../../platform/typescript/design-system/src'
      ),
      // Use test-time mocks for Konva/react-konva to avoid jsdom canvas issues
      'react-konva': resolve(
        __dirname,
        './libs/test/vitest-mocks/react-konva.ts'
      ),
      konva: resolve(__dirname, './libs/test/vitest-mocks/konva.ts'),
    },
  },
});
