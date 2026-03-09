import { defineConfig, Plugin } from 'vitest/config';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);

// Resolve the workspace root React installation to prevent duplicate React copies
const jsxRuntimePath = require.resolve('react/jsx-runtime');
const jsxDevRuntimePath = require.resolve('react/jsx-dev-runtime');
const reactEntry = require.resolve('react');
const reactDomEntry = require.resolve('react-dom');

// Custom plugin to rewrite jsx-runtime imports before Vite's import-analysis processes them
// Custom plugin to rewrite jsx-runtime imports before Vite's import-analysis processes them
// Must transform compiled dist files too (e.g., from @ghatana/utils)
function resolveJsxRuntimePlugin(): Plugin {
  return {
    name: 'resolve-jsx-runtime',
    transform(code, id) {
      // Skip node_modules EXCEPT dist files from libraries (we need to transform dist files)
      if (id.includes('node_modules') && !id.includes('/dist/')) {
        return;
      }

      let transformed = code
        .replace(/from ['"]react\/jsx-dev-runtime['"]/g, `from '${jsxDevRuntimePath}'`)
        .replace(/from ['"]react\/jsx-runtime['"]/g, `from '${jsxRuntimePath}'`)
        .replace(/import\(['"]react\/jsx-dev-runtime['"]\)/g, `import('${jsxDevRuntimePath}')`)
        .replace(/import\(['"]react\/jsx-runtime['"]\)/g, `import('${jsxRuntimePath}')`);

      if (transformed !== code) {
        return { code: transformed, map: null };
      }
    },
  };
}

export default defineConfig({
  plugins: [resolveJsxRuntimePlugin()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@ghatana/dcmaar-dashboard-core': path.resolve(__dirname, '../../libs/guardian-dashboard-core/src'),
      react: reactEntry,
      'react-dom': reactDomEntry,
      '@testing-library/jest-dom': require.resolve('@testing-library/jest-dom'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setupTests.ts'],
    deps: {
      inline: [
        'react',
        'react-dom',
        'react-router-dom',
        'jotai',
        '@tanstack/react-query',
        '@testing-library/react',
        '@testing-library/jest-dom',
        'react-test-renderer',
        '@testing-library/user-event',
        '@ghatana/dcmaar-dashboard-core', // Ensure UI library is pre-bundled
        '@ghatana/utils', // Ensure utils library is pre-bundled
      ],
    },
    exclude: [
      '**/node_modules/**',
      '**/dist/**',
      '**/cypress/**',
      '**/.{idea,git,cache,output,temp}/**',
      '**/{karma,rollup,webpack,vite,vitest,jest,ava,babel,nyc,cypress,tsup,build}.config.*',
      '**/e2e/**',
    ],
  },
});
