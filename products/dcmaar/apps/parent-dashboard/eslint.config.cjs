const js = require('@eslint/js');
const globals = require('globals');
const reactHooks = require('eslint-plugin-react-hooks');
const reactRefresh = require('eslint-plugin-react-refresh');
const tseslint = require('@typescript-eslint/eslint-plugin');
const jsdoc = require('eslint-plugin-jsdoc');
const { fileURLToPath } = require('node:url');

module.exports = [
  {
    ignores: [
      'dist/**',
      '**/*.d.ts',
      '**/*.config.js',
      '**/*.config.ts',
      '**/test/**',
      '**/__tests__/**',
      '**/*.test.ts',
      '**/*.test.tsx',
      '**/*.spec.ts',
      '**/*.spec.tsx',
      '**/stories/**',
      '**/__mocks__/**',
      '**/mock/**',
      '**/coverage/**',
    ],
  },
  // Base configuration for all files EXCEPT CommonJS
  {
    files: ['**/*.{js,jsx,ts,tsx}'],
    ignores: ['**/*.cjs', '**/*.mjs'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.es2020,
        ...globals.node,
        ...globals.jest,
      },
      parserOptions: {
        ecmaFeatures: {
          jsx: true,
        },
      },
    },
  },
  // TypeScript specific configuration - type-aware for source files only
  {
    files: ['**/src/**/*.ts', '**/src/**/*.tsx'],
    languageOptions: {
      parser: require('@typescript-eslint/parser'),
      parserOptions: {
        // Enable type-aware linting only for files that are covered by the
        // package tsconfig (usually the app's src folder). Use the app
        // tsconfig directly to ensure the parser sees the correct include
        // set when the project uses referenced configs.
        project: ['./tsconfig.app.json', './tsconfig.node.json'],
        tsconfigRootDir: __dirname,
        ecmaFeatures: {
          jsx: true,
        },
      },
    },
    plugins: {
      '@typescript-eslint': tseslint,
    },
    rules: {
      ...tseslint.configs.recommended.rules,
    },
  },

  // TypeScript files outside src/ - parse without type-aware checks
  {
    files: ['**/*.ts', '**/*.tsx'],
    ignores: ['**/src/**/*.ts', '**/src/**/*.tsx'],
    languageOptions: {
      parser: require('@typescript-eslint/parser'),
      parserOptions: {
        ecmaFeatures: {
          jsx: true,
        },
      },
    },
    plugins: {
      '@typescript-eslint': tseslint,
    },
    rules: {
      ...tseslint.configs.recommended.rules,
    },
  },
  // React specific configuration
  {
    files: ['**/*.{jsx,tsx}'],
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    settings: {
      react: {
        version: 'detect',
      },
    },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
    },
  },
  // JSDoc configuration
  {
    plugins: {
      jsdoc,
    },
    rules: {
      'jsdoc/require-jsdoc': 'off',
      'jsdoc/require-param': 'off',
      'jsdoc/require-returns': 'off',
    },
  },
];
