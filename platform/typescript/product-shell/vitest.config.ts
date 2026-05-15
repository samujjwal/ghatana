/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
  },
  resolve: {
    alias: {
      '@ghatana/design-system': resolve(__dirname, '../design-system/src/index.ts'),
      '@ghatana/platform-utils': resolve(__dirname, '../platform-utils/src/index.ts'),
      '@ghatana/theme': resolve(__dirname, '../theme/src/index.ts'),
      '@testing-library/react': resolve(
        __dirname,
        '../../../node_modules/.pnpm/@testing-library+react@16.3.2_@testing-library+dom@10.4.1_@types+react-dom@19.2.3_@type_893f466751a7d66081fd06e9edb9241a/node_modules/@testing-library/react',
      ),
      '@testing-library/jest-dom': resolve(
        __dirname,
        '../../../node_modules/.pnpm/@testing-library+jest-dom@6.9.1/node_modules/@testing-library/jest-dom',
      ),
    },
  },
});
