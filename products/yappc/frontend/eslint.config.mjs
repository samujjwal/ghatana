import { fileURLToPath } from 'node:url';
import path from 'node:path';

import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import fs from 'node:fs';
import globals from 'globals';
import prettier from 'eslint-config-prettier';
let storybook;
try {
  // Try to dynamically import the storybook plugin if available. This avoids hard failure
  // when the environment doesn't have the plugin installed (for example on older Node
  // versions or when running in constrained CI). If not present we'll continue without it.
  // Top-level await is supported in ESM config files.
   
  storybook =
    (await import('eslint-plugin-storybook')).default ||
    (await import('eslint-plugin-storybook'));
} catch (e) {
  storybook = null;
}
import reactRefresh from 'eslint-plugin-react-refresh';
import pluginImport from 'eslint-plugin-import';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import security from 'eslint-plugin-security';
import jsdoc from 'eslint-plugin-jsdoc';
import testingLibrary from 'eslint-plugin-testing-library';
import reactHooks from 'eslint-plugin-react-hooks';

const projectRoot = path.dirname(fileURLToPath(new URL('.', import.meta.url)));

// Import custom design system ESLint rules
let yappcDesignSystem;
try {
  yappcDesignSystem = await import('./eslint-local-rules/dist/index.js');
  yappcDesignSystem = yappcDesignSystem.default || yappcDesignSystem;
} catch (e) {
  console.warn('Warning: Custom YAPPC design system ESLint rules not loaded:', e.message);
  yappcDesignSystem = null;
}
// repoRoot is the parent of app-creator (workspace root)
const repoRoot = path.resolve(projectRoot, '..');
const storybookFlatConfig = storybook
  ? storybook.configs?.['flat/recommended']
  : null;

export default tseslint.config(
  {
    linterOptions: {
      reportUnusedDisableDirectives: true,
    },
    ignores: [
      // Build outputs and compiled artifacts
      'dist',
      '**/dist/**',
      '**/build/**',
      'build',
      
      // All declaration files (generated TypeScript types)
      '**/*.d.ts',
      '**/*.d.ts.map',
      
      // Node modules and dependencies
      'node_modules',
      '**/node_modules/**',
      
      // Generated code
      '**/generated/**',
      '**/src/generated/**',
      'apps/**/src/generated/**',
      'libs/**/src/generated/**',
      'libs/graphql/src/generated/**',
      '**/*.generated.*',
      '**/*.generated.ts',
      '**/*.generated.tsx',
      
      // Build tool outputs
      '.turbo',
      '.next',
      '.parcel-cache',
      '.cache',
      'coverage',
      '**/storybook-static/**',
      
      // Config files (already handled by separate rules, but exclude from main linting)
      '*.config.js',
      '*.config.d.ts',
      '**/*.config.d.ts',
      'apps/**/jest.*.d.ts',
      
      // Third-party and generated assets
      '**/.jscpd-report/**',
      '**/public/assets/**',
      '**/ios/App/App/public/**',
      '**/android/app/src/main/assets/**',
      
      // Compiled story files
      '**/src/**/stories/**/*.js',
      '**/src/**/stories/**/*.cjs',
      '**/*.stories.js',
      'libs/diagram/src/components/stories/**',
      
      // Specific project files
      '.eslintrc.cjs',
      '.dependency-cruiser.js',
  // Ignore package-level archives and examples which are intentionally
  // kept as snapshots and may not be included in the TypeScript project.
  'libs/**/archive/**',
  'libs/**/examples/**',
  // Accessibility-audit package artifacts
  'libs/accessibility-audit/archive/**',
  'libs/accessibility-audit/examples/**',
  'libs/accessibility-audit/vitest.config.ts',
  'libs/accessibility-audit/vitest.config.*',
  // Explicitly ignore legacy .old source files that remain in src (archived elsewhere)
  'libs/accessibility-audit/src/AccessibilityAuditor.old.ts',
  'libs/accessibility-audit/src/AccessibilityReportViewer.old.tsx',
  // When ESLint is executed from inside a package directory (e.g. libs/accessibility-audit),
  // add package-relative ignore patterns so files like archive/** and examples/** are
  // ignored regardless of the current working directory.
  'archive/**',
  'examples/**',
  'vitest.config.ts',
  'vitest.config.*',
      
      // Compiled JS files from TypeScript sources (redundant with source)
      'vitest.config.js',
      'vitest.setup.js',
      'vitest.coverage.config.js',
      'sentry.client.config.js',
      'sentry.server.config.js',
      'playwright.config.js',
      
      // Test and coverage outputs
      'test-results/**',
      'playwright-report/**',
      'e2e-debug/**',
      '.nyc_output/**',
      
      // Environment files
      '**/*-env.d.ts',
      '**/vite-env.d.ts',
    ],
  },
  // For generated sources we don't want type-aware parsing (parserOptions.project)
  // This prevents fatal parser errors when linting generated files that may not
  // be present in the TypeScript project. Use a narrow file pattern so we avoid
  // accidentally disabling type-aware rules for authored code.
  {
    files: ['**/src/generated/**', '**/*.generated.*'],
    languageOptions: {
      parser: tseslint.parser,
      parserOptions: {
        // Intentionally do not set `project` here so the parser falls back to
        // non-type-aware mode for generated files.
        tsconfigRootDir: projectRoot,
      },
      sourceType: 'module',
      ecmaVersion: 'latest',
    },
    rules: {
      // Relax some rules for generated outputs
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      '@typescript-eslint/no-explicit-any': 'off',
      'jsdoc/require-jsdoc': 'off',
      'jsdoc/require-description': 'off',
      'import/no-duplicates': 'off',
    },
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...(tseslint.configs['recommended-type-checked'] || []),
  prettier,
  ...(storybookFlatConfig ? [storybookFlatConfig] : []),
  // Typed files (TypeScript) — enable type-aware rules by pointing to tsconfig(s)
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      parser: tseslint.parser,
      parserOptions: {
        // Use a stable set of tsconfigs for linting to avoid parser errors
        // while we incrementally rebuild project references. Only include
        // tsconfig files that actually exist to avoid TS5012 ENOENT parser
        // errors when ESLint is invoked from alternate working directories.
        project: (() => {
          const candidates = [
            path.resolve(projectRoot, 'tsconfig.eslint.json'),
            path.resolve(repoRoot, 'app-creator', 'tsconfig.eslint.json'),
            path.resolve(process.cwd(), 'tsconfig.eslint.json'),
            path.resolve(process.cwd(), 'app-creator', 'tsconfig.eslint.json'),
            path.resolve(
              process.cwd(),
              '..',
              'app-creator',
              'tsconfig.eslint.json'
            ),
          ];
          return candidates.filter((p) => fs.existsSync(p));
        })(),
        tsconfigRootDir: projectRoot,
      },
      sourceType: 'module',
      ecmaVersion: 'latest',
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.es2020,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      import: pluginImport,
      'jsx-a11y': jsxA11y,
      security,
      jsdoc,
      ...(storybook ? { storybook } : {}),
      ...(yappcDesignSystem ? { 'yappc-design-system': yappcDesignSystem } : {}),
    },
    rules: {
      'react-hooks/exhaustive-deps': 'error',
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
      'import/order': [
        'error',
        {
          groups: [
            'builtin',
            'external',
            'internal',
            ['parent', 'sibling'],
            'index',
            'object',
            'type',
          ],
          'newlines-between': 'always',
          alphabetize: { order: 'asc', caseInsensitive: true },
          pathGroups: [
            {
              pattern: '@yappc/**',
              group: 'internal',
              position: 'before',
            },
            {
              pattern: '@/**',
              group: 'internal',
              position: 'after',
            },
          ],
          pathGroupsExcludedImportTypes: ['builtin'],
        },
      ],
      'import/no-duplicates': 'error',
      'import/no-unresolved': 'off',
      'import/no-cycle': ['error', { maxDepth: 10, ignoreExternal: true }],
      'import/no-self-import': 'error',
      'import/no-useless-path-segments': ['error', { noUselessIndex: true }],
      'import/no-relative-parent-imports': 'off', // Allow ../imports (path aliases preferred but not enforced)

      // -----------------------------------------------------------------------
      // Deprecated module guards — keep these as 'error' so CI catches regressions
      // -----------------------------------------------------------------------
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            // Legacy canvas atom file (DELETED) — guard against re-introduction
            {
              group: ['**/state/atoms/canvasAtom', '**/state/atoms/canvasAtom.ts'],
              message:
                'canvasAtom.ts has been deleted. Import from workspace/canvasAtoms.ts or libs/canvas/src/state/atoms.ts instead.',
            },
            // Deprecated libs/store package (canvas-specific state)
            {
              group: ['@ghatana/yappc-store', '@ghatana/yappc-store/*'],
              message:
                '@ghatana/yappc-store is deprecated. Use Jotai atoms directly from workspace/canvasAtoms.ts.',
            },
            // Note: @ghatana/yappc-state is allowed temporarily - contains app-level atoms
            // that need dedicated migration effort (19 files). See TODO in migration docs.
            // Deprecated libs/design-tokens package
            {
              group: ['@ghatana/yappc-design-tokens', '@ghatana/yappc-design-tokens/*', '**/libs/design-tokens/**'],
              message:
                'libs/design-tokens is deprecated. Use Tailwind CSS classes or @ghatana/yappc-ui tokens instead.',
            },
            // Deprecated gRPC canvas AI client — use CanvasAIService (HTTP) instead
            {
              group: ['**/services/canvas/CanvasAIClient', '**/CanvasAIClient.ts'],
              message:
                'CanvasAIClient.ts (gRPC) is deprecated. Use getCanvasAIService() from services/canvas/api/CanvasAIService instead.',
            },
          ],
        },
      ],

      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      '@typescript-eslint/consistent-type-imports': [
        'error',
        { prefer: 'type-imports' },
      ],
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-non-null-assertion': 'warn',
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'prefer-const': 'error',
      'no-var': 'error',
      'object-shorthand': 'error',
      'prefer-template': 'error',
      'prefer-arrow-callback': 'error',
      'no-duplicate-imports': 'error',
      complexity: ['warn', 15],
      'max-depth': ['warn', 4],
      'max-lines-per-function': [
        'warn',
        { max: 100, skipBlankLines: true, skipComments: true },
      ],
      'jsx-a11y/alt-text': 'error',
      'jsx-a11y/anchor-has-content': 'error',
      'jsx-a11y/aria-props': 'error',
      'jsx-a11y/aria-role': 'error',
      'jsx-a11y/click-events-have-key-events': 'warn',
      'jsx-a11y/no-static-element-interactions': 'warn',
      'security/detect-object-injection': 'warn',
      'security/detect-non-literal-regexp': 'warn',
      'security/detect-unsafe-regex': 'error',
      'jsdoc/require-jsdoc': [
        'warn',
        {
          require: {
            FunctionDeclaration: true,
            MethodDefinition: true,
            ClassDeclaration: true,
          },
          contexts: ['TSInterfaceDeclaration', 'TSTypeAliasDeclaration'],
        },
      ],
      'jsdoc/require-description': 'warn',
      'jsdoc/require-param-description': 'warn',
      'jsdoc/require-returns-description': 'warn',
      '@typescript-eslint/naming-convention': [
        'error',
        {
          selector: 'variable',
          format: ['camelCase', 'UPPER_CASE', 'PascalCase'],
          leadingUnderscore: 'allow',
        },
        { selector: 'function', format: ['camelCase', 'PascalCase'] },
        { selector: 'typeLike', format: ['PascalCase'] },
        {
          selector: 'interface',
          format: ['PascalCase'],
          custom: { regex: '^I[A-Z]', match: false },
        },
      ],
      // YAPPC Design System rules
      ...(yappcDesignSystem ? {
        // Active rules (Tailwind migration)
        'yappc-design-system/no-hardcoded-colors': 'warn',
        'yappc-design-system/prefer-yappc-ui': 'warn',
        'yappc-design-system/prefer-tailwind-over-inline': 'warn',
        'yappc-design-system/require-cn-utility': 'warn',
        'yappc-design-system/no-arbitrary-tailwind': 'warn',
        
        // Legacy rules (MUI migration - being phased out)
        'yappc-design-system/no-magic-spacing': 'off',
        'yappc-design-system/prefer-sx-over-style': 'off',
      } : {}),
    },
  },
  // Untyped files (JavaScript) — do not provide parserOptions.project so the
  // parser won't try to perform type-aware linting on JS files. This prevents
  // "none of those TSConfigs include this file" errors when linting JS.
  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      parser: tseslint.parser,
      parserOptions: {
        tsconfigRootDir: projectRoot,
      },
      sourceType: 'module',
      ecmaVersion: 'latest',
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.es2020,
      },
    },
    plugins: {
      'react-refresh': reactRefresh,
      'react-hooks': reactHooks,
      import: pluginImport,
      'jsx-a11y': jsxA11y,
      security,
      jsdoc,
      ...(storybook ? { storybook } : {}),
    },
    rules: {
      // Keep similar stylistic rules for JS files but avoid type-aware rules
      'import/no-duplicates': 'error',
      'import/no-unresolved': 'off',
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'prefer-const': 'error',
      'no-var': 'error',
      'object-shorthand': 'error',
      'prefer-template': 'error',
      'prefer-arrow-callback': 'error',
      'no-duplicate-imports': 'error',
      complexity: ['warn', 15],
    },
  },
  {
    files: ['**/*.{test,spec}.{ts,tsx,js,jsx}'],
    plugins: {
      'testing-library': testingLibrary,
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      'max-lines-per-function': 'off',
      'jsdoc/require-jsdoc': 'off',
      'testing-library/await-async-queries': 'error',
      'testing-library/no-await-sync-queries': 'error',
      'testing-library/no-debugging-utils': 'warn',
      'testing-library/prefer-screen-queries': 'error',
    },
  },
  // Generated GraphQL artifacts are codegen outputs; relax a few stylistic rules
  {
    files: ['libs/graphql/src/generated/**'],
    rules: {
      'import/no-duplicates': 'off',
      'no-duplicate-imports': 'off',
      // Generated code often lacks JSDoc and can be complex; relax a few rules
      'jsdoc/require-description': 'off',
      'jsdoc/require-jsdoc': 'off',
      complexity: 'off',
      '@typescript-eslint/no-explicit-any': 'off',
    },
  }
);
