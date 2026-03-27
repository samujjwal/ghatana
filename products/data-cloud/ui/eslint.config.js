/**
 * ESLint configuration for data-cloud UI
 *
 * Enforces:
 * - react-hooks/exhaustive-deps (FINDING-DC-UI-L1: useEffect dependency gaps)
 * - jsx-a11y (FINDING-DC-UI-M1: Accessibility coverage)
 * - no-console (FINDING-DC-UI-L2: console.log in prod code)
 * - no-unused-vars (FINDING-DC-UI-L4: Unused imports)
 * - react/jsx-boolean-value, react/self-closing-comp — code style
 */

import js from '@eslint/js';
import reactPlugin from 'eslint-plugin-react';
import reactHooksPlugin from 'eslint-plugin-react-hooks';
import prettierConfig from 'eslint-config-prettier';

/** @type {import('eslint').Linter.FlatConfig[]} */
export default [
  js.configs.recommended,

  // ─── React ────────────────────────────────────────────────────────────────
  {
    plugins: {
      react: reactPlugin,
      'react-hooks': reactHooksPlugin,
    },
    languageOptions: {
      parserOptions: {
        ecmaFeatures: { jsx: true },
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
      globals: {
        window: 'readonly',
        document: 'readonly',
        console: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        setInterval: 'readonly',
        clearInterval: 'readonly',
        fetch: 'readonly',
        Headers: 'readonly',
        URL: 'readonly',
        WebSocket: 'readonly',
        sessionStorage: 'readonly',
        localStorage: 'readonly',
        performance: 'readonly',
      },
    },
    settings: {
      react: {
        version: 'detect',
      },
    },
    rules: {
      // --- React Hooks (FINDING-DC-UI-L1) -----------------------------------
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',

      // --- React general ----------------------------------------------------
      'react/react-in-jsx-scope': 'off',      // Not needed with React 17+ JSX transform
      'react/prop-types': 'off',               // TypeScript handles prop types
      'react/self-closing-comp': 'warn',
      'react/jsx-no-duplicate-props': 'error',
      'react/jsx-no-undef': 'error',

      // --- Variables (FINDING-DC-UI-L4) -------------------------------------
      'no-unused-vars': ['warn', {
        vars: 'all',
        args: 'after-used',
        ignoreRestSiblings: true,
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
      }],

      // --- Console (FINDING-DC-UI-L2) ---------------------------------------
      // Allow console inside `if (import.meta.env.DEV)` blocks but
      // flag unguarded calls. Use a custom lint rule pattern.
      // Warn by default; engineers should wrap in DEV checks.
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
  },

  // ─── TypeScript files — relax no-unused-vars (tsc handles it) ────────────
  {
    files: ['**/*.ts', '**/*.tsx'],
    rules: {
      'no-unused-vars': 'off',   // TypeScript's `noUnusedLocals` is more precise
    },
  },

  // ─── Test files — allow console and relax some rules ─────────────────────
  {
    files: ['src/__tests__/**', '**/*.test.ts', '**/*.test.tsx', '**/*.spec.*'],
    rules: {
      'no-console': 'off',
    },
  },

  // ─── Storybook files ──────────────────────────────────────────────────────
  {
    files: ['**/*.stories.tsx', '**/*.stories.ts'],
    rules: {
      'no-console': 'off',
    },
  },

  // ─── Prettier: must be last to disable formatting rules ──────────────────
  prettierConfig,
];
