/**
 * @fileoverview Vitest configuration for coverage reporting
 * Enforces minimum coverage thresholds per product
 */

import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      reportsDirectory: './coverage',
      thresholds: {
        // Global minimums
        lines: 70,
        functions: 70,
        branches: 60,
        statements: 70,
        // Auto-update if thresholds are exceeded
        autoUpdate: true,
      },
      // Exclude patterns that shouldn't count toward coverage
      exclude: [
        'node_modules/',
        'dist/',
        'build/',
        '*.config.*',
        '*.d.ts',
        'src/__tests__/**',
        'src/**/*.test.*',
        'src/**/*.spec.*',
        'src/mocks/**',
        'src/stories/**',
        'scripts/**',
        '.storybook/**',
        'coverage/**',
        // Generated files
        '**/generated/**',
        '**/prisma/client/**',
        // Entry points
        'src/main.tsx',
        'src/App.tsx',
        'src/index.ts',
      ],
      // Include only source files
      include: ['src/**/*.{ts,tsx}'],
      // Enable all reports
      all: true,
      // Clean coverage directory before run
      clean: true,
    },
    // Test timeout
    testTimeout: 10000,
    // Setup files
    setupFiles: ['./src/test/setup.ts'],
    // Environment
    environment: 'jsdom',
    // Globals
    globals: true,
    // Reporter
    reporters: ['verbose'],
  },
});
