import { defineConfig } from 'vite';
import { visualizer } from 'rollup-plugin-visualizer';

/**
 * Vite Bundle Analysis Configuration
 * Part of Execution Plan item #11: Performance Optimization
 */

export default defineConfig({
  plugins: [
    visualizer({
      filename: './dist/bundle-analysis.html',
      open: false,
      gzipSize: true,
      brotliSize: true,
      template: 'treemap',
      sourcemap: true,
    }),
  ],
  build: {
    sourcemap: true,
    rollupOptions: {
      output: {
        manualChunks: {
          // Separate vendor chunks for better caching
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-ui': ['@ghatana/design-system', '@ghatana/theme', '@ghatana/tokens'],
          'vendor-data': ['@tanstack/react-query', 'zod'],
          'vendor-charts': ['recharts', '@ghatana/charts'],
          'vendor-simulation': ['@tutorputor/simulation'],
        },
      },
    },
    chunkSizeWarningLimit: 500, // kB
  },
});
