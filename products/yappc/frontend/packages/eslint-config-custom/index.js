'use strict';
Object.defineProperty(exports, '__esModule', { value: true });
const config = {
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react/recommended',
    'plugin:react-hooks/recommended',
  ],
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaFeatures: {
      jsx: true,
    },
    ecmaVersion: 2021,
    sourceType: 'module',
  },
  plugins: ['@typescript-eslint', 'react', 'react-hooks'],
  rules: {
    'react/react-in-jsx-scope': 'off',
    'react/prop-types': 'off',
    '@typescript-eslint/explicit-function-return-type': 'off',
    '@typescript-eslint/explicit-module-boundary-types': 'off',
    // --- Library migration enforcement gate ---
    // The @ghatana/yappc-* packages are deprecated. New code MUST use @yappc/* equivalents.
    // See docs/LIBRARY_CONSOLIDATION_PLAN.md for migration guidance.
    'no-restricted-imports': [
      'error',
      {
        paths: [
          {
            name: '@ghatana/yappc-ui',
            message:
              'Deprecated: use @yappc/ui instead. See LIBRARY_CONSOLIDATION_PLAN.md.',
          },
          {
            name: '@ghatana/yappc-canvas',
            message:
              'Deprecated: use @yappc/canvas instead. See LIBRARY_CONSOLIDATION_PLAN.md.',
          },
          {
            name: '@ghatana/yappc-ai',
            message:
              'Deprecated: use @yappc/ai instead. See LIBRARY_CONSOLIDATION_PLAN.md.',
          },
          {
            name: '@ghatana/yappc-ide',
            message:
              'Deprecated: @ghatana/yappc-ide is sunset 2026-06-06 and has no replacement. Remove usages.',
          },
        ],
        patterns: [
          {
            group: ['@ghatana/yappc-ui/*'],
            message: 'Deprecated: use @yappc/ui/* instead.',
          },
          {
            group: ['@ghatana/yappc-canvas/*'],
            message: 'Deprecated: use @yappc/canvas/* instead.',
          },
          {
            group: ['@ghatana/yappc-ai/*'],
            message: 'Deprecated: use @yappc/ai/* instead.',
          },
          {
            group: ['@ghatana/yappc-*'],
            message:
              'Deprecated namespace: migrate to @yappc/* equivalents per LIBRARY_CONSOLIDATION_PLAN.md.',
          },
        ],
      },
    ],
  },
  settings: {
    react: {
      version: 'detect',
    },
  },
  overrides: [
    {
      files: ['**/*.ts', '**/*.tsx'],
      rules: {
        '@typescript-eslint/no-explicit-any': 'warn',
        '@typescript-eslint/no-unused-vars': [
          'warn',
          { argsIgnorePattern: '^_' },
        ],
      },
    },
  ],
};
exports.default = config;
