import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

export default defineConfig({
  resolve: {
    alias: {
      '@ghatana/realtime': resolve(__dirname, '../../../../../platform/typescript/realtime/src/index.ts'),
      '@ghatana/platform-events': resolve(__dirname, '../../../../../platform/typescript/platform-events/src/index.ts'),
    },
  },
  test: {
    globals: true,
    environment: 'node',
    include: ['src/__tests__/**/*.test.ts'],
  },
});