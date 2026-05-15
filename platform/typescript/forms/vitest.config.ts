/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import { fileURLToPath } from 'url';
import { resolve, dirname } from 'path';

const __dirname = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/index.ts',
        'src/**/index.ts',
        'src/test/**',
        'src/**/__tests__/**',
        'src/**/*.d.ts',
      ],
      thresholds: {
        lines: 80,
        functions: 80,
        branches: 75,
        statements: 80,
      },
    },
  },
  resolve: {
    alias: {
      '@ghatana/design-system': resolve(__dirname, '../design-system/src/index.ts'),
      '@ghatana/platform-utils': resolve(__dirname, '../platform-utils/src/index.ts'),
      '@testing-library/react': resolve(__dirname, '../../../node_modules/.pnpm/@testing-library+react@16.3.2_@testing-library+dom@10.4.1_@types+react-dom@19.2.3_@type_893f466751a7d66081fd06e9edb9241a/node_modules/@testing-library/react'),
      '@testing-library/jest-dom': resolve(__dirname, '../../../node_modules/.pnpm/@testing-library+jest-dom@6.9.1/node_modules/@testing-library/jest-dom'),
    },
  },
});
