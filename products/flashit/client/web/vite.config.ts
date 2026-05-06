import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const reactPath = path.dirname(require.resolve('react/package.json'))
const reactDomPath = path.dirname(require.resolve('react-dom/package.json'))
const schedulerPath = path.dirname(require.resolve('scheduler/package.json'))
const reactRouterDomPath = path.dirname(require.resolve('react-router-dom/package.json'))
const reactRouterPath = path.dirname(
  require.resolve('react-router/package.json', { paths: [reactRouterDomPath] }),
)
const reactRouterEntryPath = path.join(reactRouterPath, 'dist/development/index.mjs')
const reactRouterDomEntryPath = path.join(reactRouterPath, 'dist/development/dom-export.mjs')

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    preserveSymlinks: true,
    dedupe: ['react', 'react-dom', 'react-router', 'react-router-dom', 'scheduler'],
    alias: [
      { find: '@', replacement: path.resolve(__dirname, './src') },
      { find: '@ghatana/design-system', replacement: path.resolve(__dirname, '../../../../platform/typescript/design-system/src/index.ts') },
      { find: '@ghatana/product-shell', replacement: path.resolve(__dirname, '../../../../platform/typescript/product-shell/src/index.ts') },
      { find: '@ghatana/theme/provider', replacement: path.resolve(__dirname, '../../../../platform/typescript/theme/src/provider.tsx') },
      { find: '@ghatana/theme', replacement: path.resolve(__dirname, '../../../../platform/typescript/theme/src/index.ts') },
      { find: '@ghatana/tokens', replacement: path.resolve(__dirname, '../../../../platform/typescript/tokens/src/index.ts') },
      { find: '@flashit/shared', replacement: path.resolve(__dirname, '../../libs/ts/shared/src/index.ts') },
      { find: /^react$/, replacement: reactPath },
      { find: /^react-dom$/, replacement: reactDomPath },
      { find: /^scheduler$/, replacement: schedulerPath },
      { find: /^react-router\/dom$/, replacement: reactRouterDomEntryPath },
      { find: /^react-router$/, replacement: reactRouterEntryPath },
      { find: /^react-router-dom$/, replacement: reactRouterDomPath },
    ],
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
