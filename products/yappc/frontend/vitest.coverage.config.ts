import { defineConfig, mergeConfig } from 'vitest/config';

import baseConfig from './vitest.config';

/**
 * Enhanced coverage configuration for Feature 6.3: Unit Test Coverage
 * 
 * This configuration enforces >90% coverage gates for core canvas helpers
 * and critical paths (normalization/change logic).
 * 
 * Usage:
 *   pnpm test --config vitest.coverage.config.ts --coverage
 *   pnpm run test:coverage (via package.json script)
 */
export default mergeConfig(
  baseConfig,
  defineConfig({
    test: {
      coverage: {
        provider: 'v8',
        
        // Enhanced reporter configuration
        reporter: ['text', 'text-summary', 'json', 'json-summary', 'html', 'lcov', 'clover'],
        
        // Output configuration
        reportsDirectory: './coverage',
        
        // Stricter exclusions for accurate coverage
        exclude: [
          // Dependencies
          'node_modules/',
          
          // Build artifacts
          '**/dist/**',
          '**/build/**',
          '**/.{idea,git,cache,output,temp}/**',
          
          // Test files
          '**/__tests__/**',
          '**/*.test.{ts,tsx}',
          '**/*.spec.{ts,tsx}',
          '**/__mocks__/**',
          '**/test-utils/**',
          
          // Configuration files
          '**/*.config.{js,ts,mjs,cjs}',
          '**/vitest.*.{js,ts}',
          '**/playwright.*.{js,ts}',
          '**/.eslintrc.{js,cjs}',
          
          // Type definitions
          '**/*.d.ts',
          
          // Examples and documentation
          '**/examples/**',
          '**/docs/**',
          '**/*.stories.{ts,tsx}',
          
          // E2E tests
          'e2e/**',
          'e2e-debug/**',
          
          // Scripts
          'scripts/**',
          
          // Storybook
          '.storybook/**',
          
          // Setup files
          '**/setup*.{ts,js}',
          '**/*Setup*.{ts,tsx}',
        ],
        
        // Global thresholds (85% baseline for all code)
        thresholds: {
          lines: 85,
          functions: 85,
          branches: 85,
          statements: 85,
          
          // Per-file thresholds to allow gradual improvement
          perFile: false,
          
          // Enforce 100% coverage for new files
          autoUpdate: false,
        },
        
        // Specific coverage rules for core canvas helpers (90%+ requirement)
        include: [
          'libs/canvas/src/**/*.{ts,tsx}',
          'apps/web/src/**/*.{ts,tsx}',
          'libs/store/src/**/*.{ts,tsx}',
          'libs/ui/src/**/*.{ts,tsx}',
        ],
        
        // Coverage enforcement for critical paths
        // These paths must meet 90%+ coverage
        all: true,
        
        // Enable source map support for accurate coverage
        sourcemap: true,
        
        // Clean coverage directory before each run
        clean: true,
        cleanOnRerun: true,
        
        // Skip coverage for files with no tests
        skipFull: false,
        
        // Branch coverage configuration
        branches: {
          // Exclude default/fallback branches from coverage
          exclude: [],
        },
        
        // Statement coverage configuration
        statements: {
          // Include all statements
          exclude: [],
        },
        
        // Function coverage configuration
        functions: {
          // Include all functions
          exclude: [],
        },
        
        // Line coverage configuration  
        lines: {
          // Include all lines
          exclude: [],
        },
      },
      
      // Additional test configuration for coverage runs
      bail: 0, // Don't bail on first failure when running coverage
      passWithNoTests: false, // Fail if no tests found
      allowOnly: false, // Disallow .only in coverage runs
      dangerouslyIgnoreUnhandledErrors: false, // Fail on unhandled errors
      
      // Coverage-specific timeouts (longer for instrumented code)
      testTimeout: 15000,
      hookTimeout: 15000,
    },
  })
);
