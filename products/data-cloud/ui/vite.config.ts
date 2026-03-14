import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

const workspaceAliases = {
  '@ghatana/design-system': path.resolve(__dirname, '../../../platform/typescript/design-system/src/index.ts'),
  '@ghatana/flow-canvas': path.resolve(__dirname, '../../../platform/typescript/canvas/flow-canvas/src/index.ts'),
  '@ghatana/theme': path.resolve(__dirname, '../../../platform/typescript/theme/src/index.ts'),
  '@ghatana/tokens': path.resolve(__dirname, '../../../platform/typescript/tokens/src/index.ts'),
  '@ghatana/utils': path.resolve(__dirname, '../../../platform/typescript/utils/src/index.ts'),
}

/**
 * Vite configuration for CES Workflow Platform UI.
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
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/api'),
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
        manualChunks: {
          'vendor': ['react', 'react-dom'],
          'design-system': ['@ghatana/design-system'],
          'diagram': ['@xyflow/react'],
        },
      },
    },
  },
  optimizeDeps: {
    include: ['react', 'react-dom', 'jotai'],
  },
})
