import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

const packageRoot = fileURLToPath(new URL('./', import.meta.url));
const setupFile = fileURLToPath(new URL('./vitest.setup.ts', import.meta.url));
const workspaceRoot = fileURLToPath(new URL('../../', import.meta.url));

export default defineConfig({
  root: packageRoot,
  resolve: {
    preserveSymlinks: true,
    extensions: ['.js', '.ts', '.tsx', '.json'],
    alias: [
      {
        find: '@ghatana/platform-events',
        replacement: fileURLToPath(new URL('../platform-events/src', import.meta.url)),
      },
      {
        find: '@ghatana/ds-schema',
        replacement: fileURLToPath(new URL('../ds-schema/src', import.meta.url)),
      },
      {
        find: '@ghatana/ds-registry',
        replacement: fileURLToPath(new URL('../ds-registry/src', import.meta.url)),
      },
      {
        find: '@ghatana/primitives',
        replacement: fileURLToPath(new URL('../primitives/src', import.meta.url)),
      },
    ],
  },
  server: {
    fs: {
      allow: [packageRoot, workspaceRoot],
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: [setupFile],
    globals: true,
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    deps: {
      interopDefault: true,
      inline: [
        /^@ghatana\/platform-events/,
        /^@ghatana\/ds-schema/,
        /^@ghatana\/ds-registry/,
        /^@ghatana\/primitives/,
        'zod',
      ],
    },
  },
});
