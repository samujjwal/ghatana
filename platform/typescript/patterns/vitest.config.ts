/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'url';
import { dirname, resolve } from 'path';

const __dirname = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/index.ts', 'src/**/index.ts', 'src/test/**', 'src/**/__tests__/**'],
      thresholds: { lines: 80, functions: 80, branches: 75, statements: 80 },
    },
  },
  resolve: {
    alias: {
      '@ghatana/platform-events': resolve(__dirname, '../platform-events/src/index.ts'),
      '@ghatana/primitives': resolve(__dirname, '../primitives/src/index.ts'),
      '@ghatana/design-system': resolve(__dirname, '../design-system/src/index.ts'),
      '@ghatana/tokens': resolve(__dirname, '../tokens/src/index.ts'),
      clsx: resolve(__dirname, '../primitives/node_modules/clsx'),
      'tailwind-merge': resolve(__dirname, '../primitives/node_modules/tailwind-merge'),
    },
  },
});
