/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import { fileURLToPath } from 'url';
import { dirname, resolve } from 'path';

const __dirname = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  test: {
    environment: 'node',
    globals: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.ts'],
      exclude: ['src/index.ts', 'src/test/**', 'src/**/__tests__/**'],
    },
  },
  resolve: {
    alias: {
      // canvas-core is a deprecated facade of @ghatana/canvas; resolve the
      // canonical package from the monorepo source to avoid a circular build dep.
      '@ghatana/canvas': resolve(__dirname, '../canvas/src/index.ts'),
    },
  },
});
