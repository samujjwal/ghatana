import { reactRouter } from '@react-router/dev/vite';
import tailwindcss from '@tailwindcss/vite';
import { visualizer } from 'rollup-plugin-visualizer';
import { defineConfig } from 'vite';
import { URL } from 'url';
import path from 'path';
import fs from 'fs';
import type { Plugin } from 'vite';

const enableBuildSourcemaps = process.env.VITE_BUILD_SOURCEMAP === 'true';

// https://vitejs.dev/config/
const rrPlugin = reactRouter();

const repositoryRootNodeModules = path.resolve(
  __dirname,
  '../../../../node_modules'
);

const compareVersion = (left: string, right: string): number => {
  const leftParts = left
    .split('.')
    .map((part) => Number.parseInt(part, 10) || 0);
  const rightParts = right
    .split('.')
    .map((part) => Number.parseInt(part, 10) || 0);
  const maxLength = Math.max(leftParts.length, rightParts.length);

  for (let index = 0; index < maxLength; index += 1) {
    const l = leftParts[index] ?? 0;
    const r = rightParts[index] ?? 0;
    if (l > r) return 1;
    if (l < r) return -1;
  }

  return 0;
};

const resolveStandaloneDependency = (dependency: string): string | null => {
  const directNodeModulesPath = path.join(
    __dirname,
    'node_modules',
    dependency
  );
  if (fs.existsSync(directNodeModulesPath)) {
    return directNodeModulesPath;
  }

  const repoNodeModulesPath = path.join(repositoryRootNodeModules, dependency);
  if (fs.existsSync(repoNodeModulesPath)) {
    return repoNodeModulesPath;
  }

  const pnpmStoreRoot = path.join(repositoryRootNodeModules, '.pnpm');
  if (!fs.existsSync(pnpmStoreRoot)) {
    return null;
  }

  const candidates = fs
    .readdirSync(pnpmStoreRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .map((entry) => {
      const dependencyPattern = dependency
        .replace('/', '\\+')
        .replace(/-/g, '\\-');
      const match = new RegExp(
        `^${dependencyPattern}@([0-9]+(?:\\.[0-9]+){1,2})`
      ).exec(entry);
      return match ? { entry, version: match[1] } : null;
    })
    .filter(
      (candidate): candidate is { entry: string; version: string } =>
        candidate !== null
    )
    .sort((a, b) => compareVersion(b.version, a.version));

  for (const candidate of candidates) {
    const candidatePath = path.join(
      pnpmStoreRoot,
      candidate.entry,
      'node_modules',
      dependency
    );
    if (fs.existsSync(candidatePath)) {
      return candidatePath;
    }
  }

  return null;
};

const standaloneDependencyAliases: Record<string, string> = {};
const lucideReactPath = resolveStandaloneDependency('lucide-react');
if (lucideReactPath) {
  standaloneDependencyAliases['lucide-react'] = lucideReactPath;
}
const zodPath = resolveStandaloneDependency('zod');
if (zodPath) {
  standaloneDependencyAliases['zod'] = zodPath;
}
const clsxPath = resolveStandaloneDependency('clsx');
if (clsxPath) {
  standaloneDependencyAliases['clsx'] = clsxPath;
}
const axiosPath = resolveStandaloneDependency('axios');
if (axiosPath) {
  standaloneDependencyAliases['axios'] = axiosPath;
}
const perfectFreehandPath = resolveStandaloneDependency('perfect-freehand');
if (perfectFreehandPath) {
  standaloneDependencyAliases['perfect-freehand'] = perfectFreehandPath;
}
const lib0Path = resolveStandaloneDependency('lib0');
if (lib0Path) {
  standaloneDependencyAliases['lib0'] = lib0Path;
}
const tanstackReactQueryPath = resolveStandaloneDependency(
  '@tanstack/react-query'
);
if (tanstackReactQueryPath) {
  standaloneDependencyAliases['@tanstack/react-query'] = tanstackReactQueryPath;
}
const muiMaterialPath = resolveStandaloneDependency('@mui/material');
if (muiMaterialPath) {
  standaloneDependencyAliases['@mui/material'] = muiMaterialPath;
}
const muiIconsMaterialPath = resolveStandaloneDependency('@mui/icons-material');
if (muiIconsMaterialPath) {
  standaloneDependencyAliases['@mui/icons-material'] = muiIconsMaterialPath;
}
const reactKonvaPath = resolveStandaloneDependency('react-konva');
if (reactKonvaPath) {
  standaloneDependencyAliases['react-konva'] = reactKonvaPath;
}
const konvaPath = resolveStandaloneDependency('konva');
if (konvaPath) {
  standaloneDependencyAliases['konva'] = konvaPath;
}

/**
 * Middleware to handle non-route static requests
 * Prevents .well-known and other non-app requests from reaching React Router
 */
const staticRequestHandler = (): Plugin => ({
  name: 'handle-static-requests',
  apply: 'serve',
  configureServer(server) {
    return () => {
      server.middlewares.use((req, res, next) => {
        // Handle .well-known requests (browser extensions, protocols, etc.)
        if (req.url?.startsWith('/.well-known/')) {
          res.statusCode = 404;
          res.end('Not Found');
          return;
        }

        // Handle favicon requests
        if (req.url === '/favicon.ico' || req.url === '/favicon.svg') {
          // Let Vite handle these from the public folder
          next();
          return;
        }

        // Pass everything else to next handler
        next();
      });
    };
  },
});

export default defineConfig({
  // Optimize dependencies
  optimizeDeps: {
    include: [
      'react',
      'react-dom',
      'react/jsx-runtime',
      'react/jsx-dev-runtime',
      '@tanstack/react-query',
      '@dnd-kit/core',
      '@dnd-kit/sortable',
      '@dnd-kit/utilities',
    ],
    exclude: ['@grpc/grpc-js', '@grpc/proto-loader'],
  },
  resolve: {
    // Dedupe React to prevent multiple instances (fixes @dnd-kit hook errors)
    dedupe: [
      'react',
      'react-dom',
      'react/jsx-runtime',
      'react/jsx-dev-runtime',
      '@dnd-kit/core',
      '@dnd-kit/sortable',
      '@dnd-kit/utilities',
    ],
    alias: {
      ...standaloneDependencyAliases,
      // App-specific aliases
      '@/components': path.resolve(__dirname, 'src/components'),
      '@/hooks': path.resolve(__dirname, 'src/hooks'),
      '@/services': path.resolve(__dirname, 'src/services'),
      '@/state': path.resolve(__dirname, 'src/state'),
      '@/pages': path.resolve(__dirname, 'src/pages'),
      '@/routes': path.resolve(__dirname, 'src/routes'),
      '@/layouts': path.resolve(__dirname, 'src/layouts'),
      '@/shared': path.resolve(__dirname, '../apps/shared'),
      '@/utils': path.resolve(__dirname, 'src/utils'),
      '@': path.resolve(__dirname, 'src'),

      // Library aliases (matching tsconfig.base.json)
      '@yappc/ui': path.resolve(__dirname, '../libs/yappc-ui/src'),
      '@yappc/state': path.resolve(__dirname, '../libs/yappc-state/src'),
      '@yappc/shortcuts': path.resolve(__dirname, '../libs/shortcuts/src'),
      '@yappc/ai': path.resolve(__dirname, '../libs/yappc-ai/src'),
      '@yappc/api': path.resolve(__dirname, '../libs/api/src'),
      '@yappc/devsecops': path.resolve(__dirname, '../libs/yappc-devsecops/src'),
      '@yappc/auth': path.resolve(__dirname, '../libs/yappc-auth/src'),
      '@yappc/auth/rbac': path.resolve(__dirname, '../libs/yappc-auth/src/auth/rbac'),
      '@yappc/chat': path.resolve(__dirname, '../libs/yappc-chat/src'),
      '@yappc/collab': path.resolve(__dirname, '../libs/collab/src'),
      '@yappc/initialization-ui': path.resolve(__dirname, '../libs/yappc-initialization-ui/src'),
      '@yappc/development-ui': path.resolve(__dirname, '../libs/yappc-development-ui/src'),
      '@ghatana/code-editor': path.resolve(__dirname, '../../../../platform/typescript/code-editor/src'),
      '@yappc/ide': path.resolve(__dirname, '../libs/ide/src'),

      // Platform shared packages used by YAPPC and transitive UI deps
      '@ghatana/design-system': path.resolve(
        __dirname,
        'src/shims/ghatana-design-system.ts'
      ),
      '@ghatana/theme': path.resolve(
        __dirname,
        'src/theme/index.ts'
      ),
      '@ghatana/charts': path.resolve(
        __dirname,
        '../../../../platform/typescript/charts/src/index.ts'
      ),
      '@ghatana/platform-utils': path.resolve(
        __dirname,
        '../../../../platform/typescript/foundation/platform-utils/src/index.ts'
      ),
      '@ghatana/accessibility-audit': path.resolve(
        __dirname,
        '../../../../platform/typescript/accessibility/src/index.ts'
      ),

      // Legacy compatibility
      '@ghatana/canvas': path.resolve(
        __dirname,
        '../../../../platform/typescript/canvas/src'
      ),
      '@ghatana/types': path.resolve(__dirname, '../libs/types/src'),

      // Capacitor shims for web builds (native-only packages → no-op stubs)
      '@capacitor/haptics': path.resolve(
        __dirname,
        '../libs/mobile/capacitor-shims.ts'
      ),
      '@capacitor/share': path.resolve(
        __dirname,
        '../libs/mobile/capacitor-shims.ts'
      ),
      '@capacitor/network': path.resolve(
        __dirname,
        '../libs/mobile/capacitor-shims.ts'
      ),
      '@capacitor/local-notifications': path.resolve(
        __dirname,
        '../libs/mobile/capacitor-shims.ts'
      ),
      '@capacitor/camera': path.resolve(
        __dirname,
        '../libs/mobile/capacitor-shims.ts'
      ),
      '@capacitor/filesystem': path.resolve(
        __dirname,
        '../libs/mobile/capacitor-shims.ts'
      ),
      '@capacitor/core': path.resolve(
        __dirname,
        '../libs/mobile/capacitor-shims.ts'
      ),
      openai: path.resolve(__dirname, 'src/shims/openai.ts'),
      '@anthropic-ai/sdk': path.resolve(__dirname, 'src/shims/anthropic.ts'),
      'react-colorful': path.resolve(__dirname, 'src/shims/react-colorful.tsx'),
      '@ghatana/tokens': path.resolve(
        __dirname,
        '../../../../platform/typescript/tokens/src/index.ts'
      ),
      // Workspace compatibility: this app uses React Router v7 package naming.
      'react-router-dom': 'react-router',
    },
  },
  plugins: [
    staticRequestHandler(),
    rrPlugin,
    tailwindcss(),
    visualizer({
      filename: './dist/stats.html',
      open: false,
      gzipSize: true,
      brotliSize: true,
    }),
  ],
  ssr: {
    noExternal: ['@emotion/react', '@emotion/styled'],
  },
  build: {
    target: 'es2020',
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },
    rollupOptions: {
      output: {
        // Manual chunks for better code splitting
        manualChunks: (id) => {
          // Vendor chunks
          if (id.includes('node_modules')) {
            // React ecosystem
            if (
              id.includes('react') ||
              id.includes('react-dom') ||
              id.includes('react-router')
            ) {
              return 'vendor-react';
            }
            // UI libraries
            if (id.includes('@mui') || id.includes('@emotion')) {
              return 'vendor-ui';
            }
            // Canvas libraries
            if (
              id.includes('@xyflow') ||
              id.includes('konva') ||
              id.includes('perfect-freehand')
            ) {
              return 'vendor-canvas';
            }
            // Utilities
            if (
              id.includes('lodash') ||
              id.includes('date-fns') ||
              id.includes('axios')
            ) {
              return 'vendor-utils';
            }
            // Everything else
            return 'vendor-other';
          }

          // App chunks - lazy load heavy features
          if (id.includes('/routes/app/canvas')) {
            return 'app-canvas';
          }
          if (id.includes('/routes/app/project')) {
            return 'app-project';
          }
          if (id.includes('/routes/app/settings')) {
            return 'app-settings';
          }
          if (id.includes('libs/canvas')) {
            return 'lib-canvas';
          }
          if (id.includes('libs/ai')) {
            return 'lib-ai';
          }
        },
        // Optimize chunk file names
        chunkFileNames: 'assets/[name]-[hash].js',
        entryFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash].[ext]',
      },
    },
    chunkSizeWarningLimit: 500, // Enforce 500KB limit per chunk
    sourcemap: enableBuildSourcemaps,
    // Enable CSS code splitting
    cssCodeSplit: true,
    // Optimize asset inlining
    assetsInlineLimit: 4096, // 4KB - inline smaller assets
  },
  server: {
    fs: {
      allow: [
        '..', // allow parent directories
        '../libs/canvas/src',
        '../libs/canvas/examples',
        '../libs/page-builder/src',
        '../scripts',
      ],
    },
    port: parseInt(process.env.PORT || process.env.VITE_PORT || '7002', 10),
    strictPort: false,
    open: false,
    hmr: {
      overlay: true, // Show error overlay in the browser
    },
    watch: {
      usePolling: true, // Helps with file watching in some environments
    },
    // Proxy API requests to the backend gateway (separate port from Vite dev server)
    // The backend gateway internally routes to Java backend for /api/rail, /api/agents, etc.
    // Frontend is unaware of multiple backend services and always talks to a single gateway
    proxy: {
      '/api': {
        target: `http://localhost:${process.env.VITE_API_PORT || '7001'}`,
        changeOrigin: true,
        secure: false,
      },
      '/graphql': {
        target: `http://localhost:${process.env.VITE_API_PORT || '7001'}`,
        changeOrigin: true,
        secure: false,
        ws: true,
      },
    },
    // Dev-only seed endpoints are registered via the custom plugin above
    headers: {
      // Temporarily disable CSP for development to resolve React Router v7 script issues
      // 'Content-Security-Policy': `
      //   default-src 'self';
      //   script-src 'self' 'unsafe-inline' 'unsafe-eval' http://localhost:* http://127.0.0.1:* 'wasm-unsafe-eval' 'inline-speculation-rules' 'sha256-sH+rkw4g1i/rq56n+4KDNfkjaiXCw14gbiPgy+TPpEA=';
      //   style-src 'self' 'unsafe-inline';
      //   img-src 'self' data: blob:;
      //   font-src 'self' data:;
      //   connect-src 'self' http://localhost:* http://127.0.0.1:* ws://localhost:* ws://127.0.0.1:*;
      //   frame-src 'self';
      //   media-src 'self';
      //   object-src 'none';
      // `
      //     .replace(/\s+/g, ' ')
      //     .trim(),
    },
  },
  preview: {
    port: 4173,
    strictPort: false,
    headers: {
      'Content-Security-Policy': `
        default-src 'self';
        script-src 'self' 'unsafe-inline' 'unsafe-eval' 'wasm-unsafe-eval' 'inline-speculation-rules' 'sha256-sH+rkw4g1i/rq56n+4KDNfkjaiXCw14gbiPgy+TPpEA=';
        style-src 'self' 'unsafe-inline';
        img-src 'self' data: blob:;
        font-src 'self' data:;
        connect-src 'self';
        frame-src 'self';
        media-src 'self';
        object-src 'none';
      `
        .replace(/\s+/g, ' ')
        .trim(),
    },
  },
});
