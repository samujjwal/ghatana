import { resolve } from 'node:path';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

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
      'yappc-core': resolve(__dirname, '../yappc-core/src'),
      'yappc-core/*': resolve(__dirname, '../yappc-core/src/*'),
      'yappc-state': resolve(__dirname, '../yappc-state/src'),
      'yappc-state/*': resolve(__dirname, '../yappc-state/src/*'),
      'yappc-ui': resolve(__dirname, './src/components/components/index.ts'),
      'yappc-ui/base-ui': resolve(
        __dirname,
        './src/components/base-ui/index.ts'
      ),
      'yappc-ui/development-ui': resolve(
        __dirname,
        './src/components/components/development/index.ts'
      ),
      'yappc-ui/initialization-ui': resolve(
        __dirname,
        './src/components/components/initialization/index.ts'
      ),
      'yappc-ui/navigation-ui': resolve(
        __dirname,
        './src/components/navigation-ui/index.ts'
      ),
      '@ghatana/platform-utils': resolve(
        __dirname,
        '../../../../../platform/typescript/platform-utils/src'
      ),
      '@ghatana/state': resolve(
        __dirname,
        '../../../../../platform/typescript/state/src'
      ),
      '@ghatana/ui-builder': resolve(
        __dirname,
        '../../../../../platform/typescript/ui-builder/src'
      ),
      // Point design-system to source to bypass the broken dist TextField.js
      '@ghatana/design-system': resolve(
        __dirname,
        '../../../../../platform/typescript/design-system/src'
      ),
      // Stub @tanstack/react-query — not installed in the workspace but pulled in
      // transitively by yappc-state barrel exports (hooks/useConfigData).
      '@tanstack/react-query': resolve(
        __dirname,
        './src/components/test/__stubs__/react-query.stub.ts'
      ),
    },
  },
});
