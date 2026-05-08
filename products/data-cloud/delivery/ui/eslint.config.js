/**
 * ESLint configuration for data-cloud UI
 *
 * Enforces:
 * - react-hooks/exhaustive-deps (FINDING-DC-UI-L1: useEffect dependency gaps)
 * - jsx-a11y (FINDING-DC-UI-M1: Accessibility coverage)
 * - no-console (FINDING-DC-UI-L2: console.log in prod code)
 * - no-unused-vars (FINDING-DC-UI-L4: Unused imports)
 * - react/jsx-boolean-value, react/self-closing-comp — code style
 * - no-restricted-imports: UI must not import backend internals (launcher, planes, plugins)
 */

import js from '@eslint/js';
import reactPlugin from 'eslint-plugin-react';
import reactHooksPlugin from 'eslint-plugin-react-hooks';
import prettierConfig from 'eslint-config-prettier';
import jsxA11yPlugin from 'eslint-plugin-jsx-a11y';

/** @type {import('eslint').Linter.FlatConfig[]} */
export default [
  js.configs.recommended,

  // ─── React ────────────────────────────────────────────────────────────────
  {
    plugins: {
      react: reactPlugin,
      'react-hooks': reactHooksPlugin,
      'jsx-a11y': jsxA11yPlugin,
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

      // --- Accessibility (FINDING-DC-UI-M1) — jsx-a11y recommended ----------
      // Enforces WCAG 2.1 AA best practices at lint time. This complements
      // the runtime axe-core tests in src/__tests__/accessibility/.
      'jsx-a11y/alt-text': 'error',
      'jsx-a11y/anchor-has-content': 'error',
      'jsx-a11y/anchor-is-valid': 'error',
      'jsx-a11y/aria-activedescendant-has-tabindex': 'error',
      'jsx-a11y/aria-props': 'error',
      'jsx-a11y/aria-proptypes': 'error',
      'jsx-a11y/aria-role': 'error',
      'jsx-a11y/aria-unsupported-elements': 'error',
      'jsx-a11y/click-events-have-key-events': 'warn',
      'jsx-a11y/heading-has-content': 'error',
      'jsx-a11y/html-has-lang': 'error',
      'jsx-a11y/interactive-supports-focus': 'warn',
      'jsx-a11y/label-has-associated-control': 'error',
      'jsx-a11y/no-autofocus': 'warn',
      'jsx-a11y/no-redundant-roles': 'error',
      'jsx-a11y/role-has-required-aria-props': 'error',
      'jsx-a11y/role-supports-aria-props': 'error',
      'jsx-a11y/tabindex-no-positive': 'warn',

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

      // --- Import boundary: UI must not import backend internals (DC-boundary) -
      // The UI boundary rule: delivery/ui must only use generated API clients,
      // shared UI platform packages, or intra-UI modules. It must never import
      // directly from the launcher, action plane, extension plugins, or any
      // Java compilation output.
      'no-restricted-imports': ['error', {
        patterns: [
          {
            // Launcher internals (Java HTTP server, settings, bootstrap)
            group: ['**/delivery/launcher/**', '*/launcher/**'],
            message: 'Do not import launcher internals from the UI. Use generated API clients (lib/api/*) instead.',
          },
          {
            // Data Cloud backend planes (action, intelligence, data, event, governance)
            group: ['**/planes/**', '*/planes/**'],
            message: 'Do not import Data Cloud plane internals from the UI. Use generated API clients (lib/api/*) instead.',
          },
          {
            // Extension plugins (postgres, kafka, etc.)
            group: ['**/extensions/plugins/**', '*/extensions/plugins/**'],
            message: 'Do not import extension plugin code from the UI. Use generated API clients (lib/api/*) instead.',
          },
          {
            // Build outputs that should not be directly referenced
            group: ['**/build/generated/**'],
            message: 'Do not import from build/generated directly. Use the generated SDK package instead.',
          },
        ],
      }],
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
