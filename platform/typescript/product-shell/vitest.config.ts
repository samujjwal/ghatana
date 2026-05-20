/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
  },
  resolve: {
    alias: {
      '@ghatana/design-system': resolve(__dirname, '../design-system/src/index.ts'),
      '@ghatana/platform-utils': resolve(__dirname, '../platform-utils/src/index.ts'),
      '@ghatana/theme': resolve(__dirname, '../theme/src/index.ts'),
    },
  },
});
