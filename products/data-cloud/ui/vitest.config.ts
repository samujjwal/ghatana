import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';
import { createRequire } from 'module';

const require = createRequire(import.meta.url);
const workspaceAliases = {
  '@ghatana/design-system': path.resolve(__dirname, '../../../platform/typescript/design-system/src/index.ts'),
  '@ghatana/theme': path.resolve(__dirname, '../../../platform/typescript/theme/src/index.ts'),
  '@ghatana/tokens': path.resolve(__dirname, '../../../platform/typescript/tokens/src/index.ts'),
  '@ghatana/utils': path.resolve(__dirname, '../../../platform/typescript/utils/src/index.ts'),
};

/**
 * Vitest configuration for CES Workflow Platform.
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
      // Redirect @ghatana/flow-canvas to a lightweight stub so jsdom tests don't
      // need ReactFlow's browser-only DOM APIs. vi.mock() calls in tests supersede this.
      '@ghatana/flow-canvas': path.resolve(__dirname, 'src/__tests__/stubs/flow-canvas.tsx'),
      // Provide explicit aliases for `entities` subpaths used by jsdom/parse5 to avoid ESM exports mismatch
      ...(entitiesDecodePath ? { 'entities/decode': entitiesDecodePath } : {}),
      ...(entitiesEscapePath ? { 'entities/escape': entitiesEscapePath } : {}),
    },
  },
});
