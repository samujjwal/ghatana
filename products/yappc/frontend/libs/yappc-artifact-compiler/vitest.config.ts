import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

export default defineConfig({
  resolve: {
    alias: {
      '@ghatana/ui-builder': resolve(
        __dirname,
        '../../../../../platform/typescript/ui-builder/src/index.ts',
      ),
      '@ghatana/ds-schema': resolve(
        __dirname,
        '../../../../../platform/typescript/ds-schema/src/index.ts',
      ),
    },
  },
  test: {
    globals: true,
    environment: 'node',
    testTimeout: 30_000,
    include: ['src/**/*.test.ts'],
    pool: 'forks',
    deps: {
      interopDefault: true,
      inline: ['typescript'],
    },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: [
        'node_modules/',
        'dist/',
        '**/*.test.ts',
        '**/*.test.tsx',
        '__tests__/',
      ],
    },
  },
});
