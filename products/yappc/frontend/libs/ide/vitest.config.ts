import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    include: ['src/**/*.{spec,test}.{ts,tsx}'],
    setupFiles: ['./src/setupTests.ts'],
    exclude: ['node_modules', 'dist'],
  },
});
