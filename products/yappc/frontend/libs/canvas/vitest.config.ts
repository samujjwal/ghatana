import { resolve } from 'path';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['../../libs/test/setupTests.ts'],
    include: [
      '**/__tests__/**/*.{test,spec}.{ts,tsx}',
      '**/*.{test,spec}.{ts,tsx}',
    ],
    exclude: [
      '**/node_modules/**',
      '**/dist/**',
      '**/build/**',
      '**/.{idea,git,cache,output,temp}/**',
      'e2e/**',
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      exclude: ['**/*.d.ts', '**/dist/**', '**/build/**', '**/__tests__/**'],
    },
    testTimeout: 10000,
    hookTimeout: 10000,
    teardownTimeout: 10000,
    isolate: true,
    reporters: ['default', 'json', 'junit'],
    outputFile: {
      json: './test-results/results.json',
      junit: './test-results/junit.xml',
    },
  },
  resolve: {
    alias: {
      '@ghatana/yappc-types': resolve(__dirname, '../../libs/types/src'),
      '@ghatana/yappc-api': resolve(__dirname, '../../libs/graphql/src'),
      '@ghatana/yappc-mocks': resolve(__dirname, '../../libs/mocks/src'),
      '@ghatana/yappc-diagram': resolve(__dirname, '../../libs/diagram/src'),
      '@ghatana/yappc-test-helpers': resolve(__dirname, '../../libs/test-helpers/src'),
      '@ghatana/ui': resolve(__dirname, '../../../../../libs/typescript/ui/src'),
    },
  },
});
