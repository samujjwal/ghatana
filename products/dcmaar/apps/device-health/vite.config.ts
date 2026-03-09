// vite.config.ts
import { defineConfig, loadEnv, type ConfigEnv, type UserConfig, type PluginOption } from 'vite';
import { crx } from '@crxjs/vite-plugin';
import istanbul from 'vite-plugin-istanbul';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import { fileURLToPath } from 'url';

// Helper to get the directory name in ESM
const __dirname = fileURLToPath(new URL('.', import.meta.url));

// Import manifest with proper typing
import type { ManifestV3Export } from '@crxjs/vite-plugin';
import manifestJson from './manifest.config';

// Type assertion for the manifest
const manifest = manifestJson as ManifestV3Export;

// Define types for environment variables
interface EnvVariables {
  BROWSER?: string;
  FIREFOX_EXTENSION_ID?: string;
  __DCMAAR_TEST_HELPERS?: string;
  PORT?: string;
  [key: string]: string | undefined;
}

// Define browser-specific configurations
const getBrowserConfig = (env: EnvVariables): typeof manifest => {
  const browser = env['BROWSER'] || 'chrome';
  const isFirefox = browser === 'firefox';
  const isEdge = browser === 'edge';

  // Create a deep copy of the manifest
  const browserManifest = JSON.parse(JSON.stringify(manifest));

  if (isFirefox) {
    // Firefox specific overrides
    browserManifest.browser_specific_settings = {
      gecko: {
        id: env['FIREFOX_EXTENSION_ID'] || '{dcmaar-extension@example.com}',
        strict_min_version: '109.0',
      },
    };

    // Firefox requires a different approach for background scripts
    if (browserManifest.background?.service_worker) {
      const { service_worker, ...rest } = browserManifest.background;
      browserManifest.background = {
        ...rest,
        type: 'module',
        scripts: [service_worker],
      };
    }
  } else if (isEdge) {
    // Edge specific overrides
    browserManifest.minimum_edge_version = '88.0';
  }

  return browserManifest;
};

export default defineConfig(({ mode }: ConfigEnv): UserConfig => {
  // Load env file based on `mode` in the current working directory
  const env = loadEnv(mode, process.cwd(), '') as EnvVariables;
  
  // Set up process.env for browser compatibility
  const define = {
    'process.env': {},
    'process.platform': JSON.stringify('browser'),
    'process.browser': true,
    'process.versions': { node: false },
    'global': 'globalThis',
    ...(mode === 'production' ? { 'process.env.NODE_ENV': '"production"' } : { 'process.env.NODE_ENV': '"development"' })
  };

  // Get browser configuration
  const browser = env['BROWSER'] || 'chrome';
  const isTest = mode === 'test';

  // Determine whether test helpers were enabled via env
  const dcmaarTestHelpersFlag = env['__DCMAAR_TEST_HELPERS'] === 'true';

  // Safety: do not allow production builds with test helpers enabled
  if (mode === 'production' && dcmaarTestHelpersFlag) {
    throw new Error(
      'Production build refused: __DCMAAR_TEST_HELPERS must not be enabled in production builds. Unset __DCMAAR_TEST_HELPERS and retry.'
    );
  }

  const browserConfig = getBrowserConfig(env);

  const isEdge = browser === 'edge';

  return {
    define: {
      ...define,
      __BROWSER__: JSON.stringify(browser),
      __IS_EDGE__: JSON.stringify(isEdge),
      'process.env.NODE_ENV': JSON.stringify(mode),
      __DCMAAR_TEST_HELPERS: JSON.stringify(dcmaarTestHelpersFlag),
    },
    resolve: {
      dedupe: ['react', 'react-dom'],
      alias: [
        // Core aliases
        { find: '@', replacement: resolve(__dirname, './src') },
        { find: '@core', replacement: resolve(__dirname, './src/core') },
        { find: '@core/utils', replacement: resolve(__dirname, './src/core/utils') },
        { find: '@core/storage', replacement: resolve(__dirname, './src/core/storage') },

        // Extension aliases
        { find: '@extension', replacement: resolve(__dirname, './src') },
        { find: '@app', replacement: resolve(__dirname, './src/app') },
        { find: '@app/background', replacement: resolve(__dirname, './src/app/background') },
        { find: '@interactions', replacement: resolve(__dirname, './src/interactions') },
        {
          find: '@interactions/content',
          replacement: resolve(__dirname, './src/interactions/content'),
        },
        { find: '@pipeline', replacement: resolve(__dirname, './src/pipeline') },
        { find: '@pipeline/sources', replacement: resolve(__dirname, './src/pipeline/sources') },
        { find: '@pipeline/sinks', replacement: resolve(__dirname, './src/pipeline/sinks') },
        { find: '@shared', replacement: resolve(__dirname, './src/shared') },
        { find: '@components', replacement: resolve(__dirname, './src/components') },

        // Features aliases
        { find: '@features', replacement: resolve(__dirname, './src/features') },
        { find: '@features/security', replacement: resolve(__dirname, './src/features/security') },
        { find: '@features/privacy', replacement: resolve(__dirname, './src/features/privacy') },

        // UI aliases
        { find: '@ui', replacement: resolve(__dirname, './src/components/ui') },

        // Services aliases
        { find: '@services', replacement: resolve(__dirname, './src/services') },
        { find: '@services/auth', replacement: resolve(__dirname, './src/services/auth') },

        // Legacy aliases for backward compatibility
        { find: '@schemas', replacement: resolve(__dirname, './src/schemas') },
        { find: 'extensionShared', replacement: resolve(__dirname, './src/shared') },
      ],
    },
    build: {
      outDir: `dist/${browser}`,
      emptyOutDir: !isTest, // Don't clean in test mode to avoid race conditions
      rollupOptions: {
        external: [
          // Node.js built-ins that should be externalized
          'events',
          'stream',
          'crypto',
          'util',
          'buffer',
          'path',
          'fs',
          'os',
          'http',
          'https',
          'url',
          'zlib',
          'assert',
          'tty'
        ],
        input: {
          popup: 'src/popup/index.html',
          'background/index': 'src/app/background/index.ts',
          content: 'src/interactions/content/index.ts',
          // TODO: Re-enable options after installing React
          // 'options/index': 'src/options/index.tsx',
        },
        output: {
          entryFileNames: '[name].js',
          chunkFileNames: 'assets/[name]-[hash].js',
          assetFileNames: 'assets/[name]-[hash].[ext]',
          // Ensure proper module format for service worker
          format: 'esm',
          exports: 'auto',
          // Ensure React is bundled as a single instance
          manualChunks(id) {
            // Bundle React and React DOM together
            if (id.includes('node_modules/react/') || id.includes('node_modules/react-dom/')) {
              return 'react-vendor';
            }
            // Bundle scheduler with React
            if (id.includes('node_modules/scheduler/')) {
              return 'react-vendor';
            }
          },
        },
        // Ensure proper handling of dynamic imports
        preserveEntrySignatures: 'strict',
      },
      target: 'es2022',
      minify: isTest ? false : 'esbuild',
      // Enable source maps for better debugging
      sourcemap: isTest ? 'inline' : true,
      // Ensure proper module type for service worker
      modulePreload: { polyfill: false },
      // Ensure proper chunking for service worker
      chunkSizeWarningLimit: 2000,
    },
    plugins: [
      react({
        // Use React's JSX runtime
        jsxRuntime: 'automatic',
        // Use the new JSX transform
        jsxImportSource: 'react',
        // Babel configuration
        babel: {
          plugins: [
            // Add any Babel plugins here
          ],
        },
      }),
      {
        name: 'ignore-react-query-use-client',
        transform(code, id) {
          if (id.includes('@tanstack/react-query')) {
            return code.replace(/^\s*['"]use client['"];?\s*/gm, '');
          }
          return code;
        },
      },
      ...(isTest
        ? [
            istanbul({
              include: ['src/**/*.{ts,tsx}'],
              exclude: ['node_modules', 'test/'],
              extension: ['.ts', '.tsx'],
              requireEnv: false,
              forceBuildInstrument: true,
            }) as PluginOption,
          ]
        : []),
      crx({
        manifest: browserConfig as unknown as chrome.runtime.ManifestV3,
      } as any),
    ],
    server: (() => {
      const serverConfig: any = {
        port: env['PORT'] ? parseInt(env['PORT']) : 3000,
        strictPort: true,
        hmr: {
          port: 3000,
        },
      };

      if (isTest) {
        serverConfig.watch = {
          ignored: ['**/coverage/**'],
        };
      }

      return serverConfig;
    })(),
    // Optimize dependencies for test environment
    optimizeDeps: isTest
      ? {
          include: [],
        }
      : {
          include: ['react', 'react-dom'],
          exclude: [],
        },
    // Clear screen only in non-test mode
    clearScreen: !isTest,
  };
});
