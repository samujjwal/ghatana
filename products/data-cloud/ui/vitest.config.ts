import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';
import { createRequire } from 'module';

const require = createRequire(import.meta.url);
const workspaceAliases = {
  '@ghatana/design-system': path.resolve(__dirname, '../../../platform/typescript/design-system/src/index.ts'),
  '@ghatana/theme': path.resolve(__dirname, '../../../platform/typescript/theme/src/index.ts'),
  '@ghatana/tokens': path.resolve(__dirname, '../../../platform/typescript/tokens/src/index.ts'),
  '@ghatana/platform-utils': path.resolve(__dirname, '../../../platform/typescript/foundation/platform-utils/src/index.ts'),
  '@ghatana/canvas': path.resolve(__dirname, '../../../platform/typescript/canvas'),
  '@ghatana/realtime': path.resolve(__dirname, '../../../platform/typescript/realtime/src/index.ts'),
};

/**
 * Vitest configuration for Data Cloud Platform.
 *
 * @doc.type config
 * @doc.purpose Test runner configuration
 */
// Resolve potential package subpath differences for `entities` package used by jsdom/parse5
let entitiesDecodePath: string | undefined;
let entitiesEscapePath: string | undefined;
try {
  entitiesDecodePath = require.resolve('entities/lib/decode.js');
  entitiesEscapePath = require.resolve('entities/lib/escape.js');
} catch (e) {
  // Fallback: leave undefined - Vitest resolver will use normal resolution and fail if unresolved
  // This fallback keeps the config robust if package layout changes.
}

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/__tests__/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}', 'tests/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: [
        'node_modules/',
        'src/__tests__/',
        '**/*.stories.tsx',
        '**/*.d.ts',
      ],
    },
  },
  resolve: {
    alias: {
      ...workspaceAliases,
      '@': path.resolve(__dirname, './src'),
      // Short aliases matching tsconfig.json paths
      '@components': path.resolve(__dirname, './src/components'),
      '@hooks': path.resolve(__dirname, './src/hooks'),
      '@stores': path.resolve(__dirname, './src/stores'),
      '@types': path.resolve(__dirname, './src/types'),
      '@api': path.resolve(__dirname, './src/api'),
      '@utils': path.resolve(__dirname, './src/utils'),
      // Redirect @ghatana/flow-canvas to a lightweight stub so jsdom tests don't
      // need ReactFlow's browser-only DOM APIs. vi.mock() calls in tests supersede this.
      '@ghatana/flow-canvas': path.resolve(__dirname, 'src/__tests__/stubs/flow-canvas.tsx'),
      // Provide explicit aliases for `entities` subpaths used by jsdom/parse5 to avoid ESM exports mismatch
      ...(entitiesDecodePath ? { 'entities/decode': entitiesDecodePath } : {}),
      ...(entitiesEscapePath ? { 'entities/escape': entitiesEscapePath } : {}),
    },
  },
});
