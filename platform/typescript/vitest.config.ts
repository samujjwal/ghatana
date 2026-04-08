import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    globals: true,
  },
  resolve: {
    alias: {
      '@ghatana/privacy-ui': path.resolve(__dirname, './privacy-ui/src'),
      '@ghatana/security-ui': path.resolve(__dirname, './security-ui/src'),
      '@ghatana/voice-ui': path.resolve(__dirname, './voice-ui/src'),
      '@ghatana/nlp-ui': path.resolve(__dirname, './nlp-ui/src'),
      '@ghatana/audit-ui': path.resolve(__dirname, './audit-ui/src'),
      '@ghatana/selection-ui': path.resolve(__dirname, './selection-ui/src'),
    },
  },
});
