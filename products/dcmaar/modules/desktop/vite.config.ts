import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // Note: Grid is imported from '@mui/material/Grid' in v7
    },
  },
  server: {
    port: 3001,
    strictPort: true,
    headers: {
      'Content-Security-Policy': `
        default-src 'self';
        connect-src 'self' ws://localhost:3001 ws://localhost:9001 http://localhost:9001 https://localhost:9001 http://127.0.0.1:9001 https://api-dev.dcmaar.example.com https://api-staging.dcmaar.example.com https://api.dcmaar.example.com;
        script-src 'self' 'unsafe-eval' 'unsafe-inline';
        style-src 'self' 'unsafe-inline';
        img-src 'self' data:;
      `
        .replace(/\s+/g, ' ')
        .trim(),
    },
  },
  build: {
    target: ['es2021', 'chrome100', 'safari15'],
    outDir: './dist',
    emptyOutDir: true,
    rollupOptions: {
      external: ['@tauri-apps/api/tauri'],
    },
  },
  css: {
    modules: {
      localsConvention: 'camelCaseOnly',
    },
    preprocessorOptions: {
      scss: {
        additionalData: `@import "src/styles/variables.scss";`,
      },
    },
  },
  envPrefix: ['VITE_', 'TAURI_'],
});
