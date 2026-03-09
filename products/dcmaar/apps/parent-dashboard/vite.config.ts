import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { visualizer } from 'rollup-plugin-visualizer'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    visualizer({
      filename: 'bundle-stats.html',
      open: false,
      gzipSize: true,
      brotliSize: true,
    }) as unknown as Plugin,
  ],
  css: {
    postcss: './postcss.config.js',
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@ghatana/dcmaar-dashboard-core': path.resolve(__dirname, '../../libs/guardian-dashboard-core/src'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Vendor chunks for better caching
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'form-vendor': ['react-hook-form', '@hookform/resolvers', 'zod'],
          'state-vendor': ['jotai'],
          'socket-vendor': ['socket.io-client'],
        },
      },
    },
    // Performance optimizations
    chunkSizeWarningLimit: 600, // Warn if chunk exceeds 600KB
    sourcemap: 'hidden', // Generate sourcemaps but don't reference them in bundles (upload to Sentry separately)
    minify: 'terser', // Use terser for better minification
    terserOptions: {
      compress: {
        drop_console: true, // Remove console.logs in production
        drop_debugger: true,
      },
      // Keep source maps for production debugging
      sourceMap: true,
    },
  },
  // Development server optimizations
  server: {
    port: 5173,
    strictPort: false, // Allow fallback to another port if 5173 is busy
    open: true,
  },
  // Preview server config
  preview: {
    port: 4173,
    strictPort: true,
  },
  // Vitest settings: inline common React-related deps so workers use the same
  // instances and avoid duplicate React copies which cause hook errors.
  // @ts-ignore - Vite's test config is recognized at runtime by Vitest/Vite
  test: {
    globals: true,
    environment: 'jsdom',
    deps: {
      inline: ['react', 'react-dom', 'react-router-dom', 'jotai'],
    },
  },
})
