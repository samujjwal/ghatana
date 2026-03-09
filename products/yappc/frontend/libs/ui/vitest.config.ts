/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  resolve: {
    alias: {
      '@ghatana/yappc-crdt/websocket': path.resolve(__dirname, '../crdt/src/websocket'),
      '@ghatana/yappc-types': path.resolve(__dirname, '../types/src'),
      '@ghatana/yappc-utils': path.resolve(__dirname, '../utils/src'),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
    fakeTimers: {
      enableGlobally: true,
      toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'],
    },
  },
});
