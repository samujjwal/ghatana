import { resolve } from 'node:path';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// yappc-development-ui is 5 levels deep from the monorepo root:
// products/yappc/frontend/libs/yappc-development-ui/
const MONOREPO_ROOT = resolve(__dirname, '../../../../../');

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
    include: ['src/**/*.{spec,test}.{ts,tsx}'],
    exclude: ['dist', 'node_modules'],
  },
  resolve: {
    alias: {
      // Product packages
      '@yappc/core': resolve(__dirname, '../yappc-core/src'),
      '@yappc/state': resolve(__dirname, '../yappc-state/src'),

      // Platform packages — use source to avoid broken dist issues
      '@ghatana/platform-utils': resolve(
        MONOREPO_ROOT,
        'platform/typescript/platform-utils/src'
      ),
      '@ghatana/state': resolve(
        MONOREPO_ROOT,
        'platform/typescript/state/src'
      ),
      '@ghatana/ui-builder': resolve(
        MONOREPO_ROOT,
        'platform/typescript/ui-builder/src'
      ),
      '@ghatana/design-system': resolve(
        MONOREPO_ROOT,
        'platform/typescript/design-system/src'
      ),

      // @tanstack/react-query is not installed; stub it for tests that hit
      // @yappc/state barrel → hooks/useConfigData path.
      '@tanstack/react-query': resolve(
        __dirname,
        '../yappc-ui/src/components/test/__stubs__/react-query.stub.ts'
      ),
    },
  },
});
