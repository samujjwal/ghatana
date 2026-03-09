import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@ghatana/flashit-shared': path.resolve(__dirname, '../../libs/ts/shared/dist/index.js'),
    },
  },
  server: {
    port: 2901,
    proxy: {
      '/api': {
        target: 'http://localhost:2900',
        changeOrigin: true,
      },
      '/auth': {
        target: 'http://localhost:2900',
        changeOrigin: true,
      },
    },
  },
})

