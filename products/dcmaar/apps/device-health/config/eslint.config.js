// Consolidated ESLint configuration for DCMAAR Extension
// This configuration provides strict linting rules to prevent technical debt

import globals from 'globals';
import tsParser from '@typescript-eslint/parser';
import tsPlugin from '@typescript-eslint/eslint-plugin';
import importPlugin from 'eslint-plugin-import';
import unusedImportsPlugin from 'eslint-plugin-unused-imports';
import { chronicDebtPreventionRules, extensionSpecificRules } from '../eslint-debt-prevention.js';
// Debt prevention rules imported as needed for specific configurations

/** @type {import('eslint').Linter.FlatConfig[]} */
export default [
  // Global ignores
  {
    ignores: [
      '**/node_modules/**',
      '**/dist/**',
      '**/build/**',
      '**/coverage/**',
      '**/.next/**',
      '**/.vercel/**',
      '**/.vscode/**',
      '**/.idea/**',
      '**/.git/**',
      '**/.husky/**',
      '**/playwright-report/**',
      '**/test-results/**',
      '**/*.d.ts',
    ],
  },

  // Base JavaScript configuration
  {
    files: ['**/*.js', '**/*.mjs', '**/*.cjs'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.webextensions,
      },
    },
    rules: {
      // Apply debt prevention rules to JS files
      ...chronicDebtPreventionRules,
      ...extensionSpecificRules,
      
      // Basic quality rules
      'no-unused-vars': 'warn',
      'prefer-const': 'error',
      'no-var': 'error',
    },
  },

  // TypeScript configuration — only enable type-aware (project) parsing for
  // authored source files under `src/`. Other TypeScript files (configs,
  // generated files, scripts) will be linted without `project` to avoid
  // "file not found in provided project(s)" parser errors.
  {
    files: ['src/**/*.ts', 'src/**/*.tsx'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaFeatures: {
          jsx: true,
        },
        ecmaVersion: 'latest',
        sourceType: 'module',
        project: './tsconfig.eslint.json',
      },
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.webextensions,
      },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
      'unused-imports': unusedImportsPlugin,
      'import': importPlugin,
    },
    rules: {
      // Base TypeScript rules
      ...tsPlugin.configs['eslint-recommended'].rules,
      ...tsPlugin.configs['recommended'].rules,
      
      // Basic debt prevention rules (safe subset)
      '@typescript-eslint/explicit-module-boundary-types': 'off',
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': 'off',
      
      // Import management
      'unused-imports/no-unused-imports': 'error',
      'unused-imports/no-unused-vars': [
        'warn',
        {
          vars: 'all',
          varsIgnorePattern: '^_',
          args: 'after-used',
          argsIgnorePattern: '^_',
        },
      ],
      
      // Import ordering
      'import/order': [
        'warn',
        {
          groups: [
            'builtin',
            'external',
            'internal',
            'parent',
            'sibling',
            'index',
            'object',
            'type',
          ],
          'newlines-between': 'always',
          alphabetize: {
            order: 'asc',
            caseInsensitive: true,
          },
        },
      ],
      
      // Namespace handling for extension APIs
      '@typescript-eslint/no-namespace': [
        'error',
        {
          allowDeclarations: true,
          allowDefinitionFiles: true,
        },
      ],
      
      // Disable some overly strict rules for extension context
      '@typescript-eslint/explicit-module-boundary-types': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'warn', // Downgrade to warning
      '@typescript-eslint/no-unsafe-member-access': 'warn', // Downgrade to warning
    },
    settings: {
      'import/resolver': {
        typescript: {
          project: './tsconfig.eslint.json',
          extensions: ['.ts', '.tsx', '.d.ts', '.json'],
          alwaysTryTypes: true,
        },
        node: {
          extensions: ['.js', '.jsx', '.ts', '.tsx', '.json'],
          moduleDirectory: ['node_modules', 'src'],
        },
      },
    },
  },

  // Non-src TypeScript files should not use type-aware parsing (no `project`)
  {
    files: ['**/*.ts', '**/*.tsx'],
    ignores: ['src/**/*.ts', 'src/**/*.tsx'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaFeatures: { jsx: true },
        ecmaVersion: 'latest',
        sourceType: 'module',
        tsconfigRootDir: __dirname,
      },
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.webextensions,
      },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
      'unused-imports': unusedImportsPlugin,
      'import': importPlugin,
    },
    rules: {
      // Keep most rules but avoid type-aware checks that require parserOptions.project
      '@typescript-eslint/no-unused-vars': 'off',
    },
  },

  // Test files configuration
  {
    files: [
      '**/*.test.{js,ts,mjs,cjs,ts,tsx}',
      '**/*.spec.{js,ts,mjs,cjs,ts,tsx}',
      'tests/**/*.{js,ts,mjs,cjs,tsx}',
      'test/**/*.{js,ts,mjs,cjs,tsx}',
    ],
    rules: {
      // Relax some rules for test files
      'no-console': 'off',
      '@typescript-eslint/no-explicit-any': 'warn',
      'max-lines-per-function': 'off',
      'max-statements': 'off',
      'no-magic-numbers': 'off',
      '@typescript-eslint/no-non-null-assertion': 'off',
      '@typescript-eslint/no-unsafe-any': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
      '@typescript-eslint/no-unsafe-call': 'off',
    },
  },

  // Configuration files
  {
    files: [
      '**/*.config.{js,ts,mjs,cjs}',
      '**/vite.config.{js,ts,mjs,cjs}',
      '**/vitest.config.{js,ts,mjs,cjs}',
      '**/playwright.config.{js,ts,mjs,cjs}',
      '**/eslint.config.{js,ts,mjs,cjs}',
    ],
    rules: {
      // Relax rules for config files
      'no-console': 'off',
      '@typescript-eslint/no-require-imports': 'off',
      'import/no-default-export': 'off',
      'import/order': 'off',
    },
  },

  // Script files
  {
    files: ['scripts/**/*.{js,ts,mjs,cjs}'],
    rules: {
      // Allow console in scripts
      'no-console': 'off',
      'no-process-exit': 'off',
      '@typescript-eslint/no-require-imports': 'off',
    },
  },
];
