import { defineConfig, mergeConfig } from 'vitest/config';
import baseConfig from './config/vitest.config.ts';

export default defineConfig(
  mergeConfig(baseConfig, {
    test: {
      // Consolidated test patterns
      include: [
        // Unit tests
        '__tests__/unit/**/*.test.ts',
        '__tests__/unit/**/*.spec.ts',
        // Integration tests
        '__tests__/it/**/*.test.ts',
        '__tests__/it/**/*.spec.ts',
      ],

      // Project-specific setup
      setupFiles: ['./__tests__/setup.ts'],

      coverage: {
        // Project-specific coverage configuration
        reportsDirectory: './coverage/vitest',
        include: ['src/**/*.{ts,tsx}', '../packages/extension-shared/src/**/*.{ts,tsx}'],
        exclude: ['tests/**/*', '__tests__/**/*', '**/*.d.ts', '**/__mocks__/**'],
      },
    },
  })
);
