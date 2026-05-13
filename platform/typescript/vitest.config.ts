import { defineConfig } from 'vitest/config';
import { resolve } from 'node:path';

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: [resolve(__dirname, './vitest.setup.ts')],
    globals: true,
  },
});
