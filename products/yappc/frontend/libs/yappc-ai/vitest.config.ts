import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

export default defineConfig({
  test: {
    globals: true,
    environment: 'jsdom',
    testTimeout: 30_000,
  },
  resolve: {
    alias: {
      '@ghatana/ui-builder': resolve(
        __dirname,
        '../../../../../platform/typescript/ui-builder/src/index.ts',
      ),
      '@ghatana/design-system': resolve(
        __dirname,
        '../../../../../platform/typescript/design-system/src/index.ts',
      ),
      '@ghatana/platform-events': resolve(
        __dirname,
        '../../../../../platform/typescript/platform-events/src/index.ts',
      ),
    },
  },
});
