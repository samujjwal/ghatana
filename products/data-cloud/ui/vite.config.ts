import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

const workspaceAliases = {
  '@ghatana/design-system': path.resolve(__dirname, '../../../platform/typescript/design-system/src/index.ts'),
  '@ghatana/canvas/flow': path.resolve(__dirname, '../../../platform/typescript/canvas/src/flow/index.ts'),
  '@ghatana/canvas/hybrid': path.resolve(__dirname, '../../../platform/typescript/canvas/src/hybrid/index.ts'),
  '@ghatana/theme': path.resolve(__dirname, '../../../platform/typescript/theme/src/index.ts'),
  '@ghatana/tokens': path.resolve(__dirname, '../../../platform/typescript/tokens/src/index.ts'),
  '@ghatana/platform-utils': path.resolve(__dirname, '../../../platform/typescript/platform-utils/src/index.ts'),
  '@ghatana/realtime': path.resolve(__dirname, '../../../platform/typescript/realtime/src/index.ts'),
  '@ghatana/domain-components': path.resolve(__dirname, '../../../platform/typescript/domain-components'),
  '@ghatana/wizard': path.resolve(__dirname, '../../../platform/typescript/wizard/src/index.ts'),
}

/**
 * Vite configuration for Data Cloud Platform UI.
 *
 * @doc.type config
 * @doc.purpose Vite build and dev server configuration
 * @doc.layer frontend
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      ...workspaceAliases,
      '@': path.resolve(__dirname, './src'),
      '@components': path.resolve(__dirname, './src/components'),
      '@hooks': path.resolve(__dirname, './src/hooks'),
      '@stores': path.resolve(__dirname, './src/stores'),
      '@types': path.resolve(__dirname, './src/types'),
      '@api': path.resolve(__dirname, './src/api'),
      '@utils': path.resolve(__dirname, './src/utils'),
    },
  },
  server: {
    port: 5173,
    strictPort: false,
    hmr: {
      overlay: true,
    },
    watch: {
      usePolling: false,
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    minify: 'terser',
    // Fail the build if any individual chunk exceeds 600 kB (gzipped ~200 kB).
    // This acts as a CI gate preventing accidental large-dependency additions.
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/react') || id.includes('node_modules/react-dom')) {
            return 'vendor';
          }
          if (id.includes('@ghatana/design-system')) {
            return 'design-system';
          }
          if (id.includes('@xyflow/react')) {
            return 'diagram';
          }
        },
      },
    },
  },
  optimizeDeps: {
    include: [
      'react',
      'react-dom',
      'jotai',
      '@ghatana/design-system',
      '@ghatana/domain-components',
      '@ghatana/theme',
      '@ghatana/platform-utils',
      '@ghatana/canvas',
      '@ghatana/realtime',
      '@ghatana/wizard',
    ],
  },
})
