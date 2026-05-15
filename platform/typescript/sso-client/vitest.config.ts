import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    environmentOptions: {
      jsdom: {
        url: 'http://localhost/',
      },
    },
    exclude: ['dist/**', 'node_modules/**'],
    globals: true,
    include: ['src/**/*.test.ts'],
    setupFiles: ['./vitest.setup.ts'],
  },
});
