import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import importPlugin from 'eslint-plugin-import';
import reactPlugin from 'eslint-plugin-react';
import reactHooksPlugin from 'eslint-plugin-react-hooks';
import jsdocPlugin from 'eslint-plugin-jsdoc';
import securityPlugin from 'eslint-plugin-security';
import sonarjsPlugin from 'eslint-plugin-sonarjs';
import unicornPlugin from 'eslint-plugin-unicorn';
import jsxA11yPlugin from 'eslint-plugin-jsx-a11y';

/**
 * ESLint Configuration with Governance Rules
 * 
 * Phase 3: Warning Mode (Non-blocking)
 * Phase 6: Will switch to Error Mode (Blocking)
 * 
 * @see docs/NAMING_CONVENTIONS.md
 * @see docs/GOVERNANCE_IMPLEMENTATION_PLAN.md
 */

// ============================================================================
// GOVERNANCE RULES (Phase 3 - Warning Mode)
// ============================================================================

const governanceRules = {
  // Import order enforcement
  'import/order': ['warn', {
    groups: [
      'builtin',      // Node.js built-ins (fs, path)
      'external',     // npm packages (react, lodash)
      'internal',     // @ghatana/* and @yappc/*
      'parent',       // ../
      'sibling',      // ./
      'index',        // ./index
    ],
    pathGroups: [
      // Platform libraries first
      {
        pattern: '@ghatana/**',
        group: 'internal',
        position: 'before',
      },
      // Product libraries second
      {
        pattern: '@yappc/**',
        group: 'internal',
        position: 'after',
      },
      // Other product scopes
      {
        pattern: '@data-cloud/**',
        group: 'internal',
        position: 'after',
      },
      {
        pattern: '@flashit/**',
        group: 'internal',
        position: 'after',
      },
    ],
    pathGroupsExcludedImportTypes: ['builtin'],
    alphabetize: {
      order: 'asc',
      caseInsensitive: true,
    },
    'newlines-between': 'always',
    warnOnUnassignedImports: true,
  }],

  // No self-imports
  'import/no-self-import': 'error',

  // No cycle dependencies (warning during Phase 3)
  'import/no-cycle': ['warn', {
    maxDepth: 3,
    ignoreExternal: true,
  }],

  // Naming convention warnings
  'import/no-restricted-paths': ['warn', {
    zones: [
      // Rule: Products should not depend on each other directly
      // Products should use @ghatana/* abstractions
      {
        target: './products/data-cloud',
        from: './products/yappc',
        message: '🏛️ GOVERNANCE: Products should not depend on each other. ' +
                 'Use @ghatana/* platform abstractions instead. ' +
                 'See docs/NAMING_CONVENTIONS.md',
      },
      {
        target: './products/flashit',
        from: './products/yappc',
        message: '🏛️ GOVERNANCE: Products should not depend on each other. ' +
                 'Use @ghatana/* platform abstractions instead. ' +
                 'See docs/NAMING_CONVENTIONS.md',
      },
      {
        target: './products/data-cloud',
        from: './products/flashit',
        message: '🏛️ GOVERNANCE: Products should not depend on each other. ' +
                 'Use @ghatana/* platform abstractions instead. ' +
                 'See docs/NAMING_CONVENTIONS.md',
      },
    ],
  }],

  // Prefer type imports
  '@typescript-eslint/consistent-type-imports': ['warn', {
    prefer: 'type-imports',
    fixStyle: 'inline-type-imports',
  }],

  // No unused imports
  'import/no-unused-modules': ['warn', {
    unusedExports: true,
    missingExports: false,
  }],

  // JSDoc documentation requirements
  'jsdoc/require-jsdoc': ['warn', {
    require: {
      FunctionDeclaration: false,
      MethodDefinition: true,
      ClassDeclaration: true,
      ArrowFunctionExpression: false,
      FunctionExpression: false,
    },
  }],

  // @doc.* tag requirements (custom rule simulated via jsdoc)
  'jsdoc/check-tag-names': ['warn', {
    definedTags: [
      'doc.type',
      'doc.purpose',
      'doc.layer',
      'doc.pattern',
      'doc.component',
    ],
  }],
};

// ============================================================================
// STANDARD RULES (Preserved from existing config)
// ============================================================================

const standardRules = {
  // React
  'react/react-in-jsx-scope': 'off',
  'react/prop-types': 'off',
  'react-hooks/rules-of-hooks': 'error',
  'react-hooks/exhaustive-deps': 'warn',

  // TypeScript
  '@typescript-eslint/no-unused-vars': ['warn', {
    argsIgnorePattern: '^_',
    varsIgnorePattern: '^_',
  }],
  '@typescript-eslint/no-explicit-any': 'warn',
  '@typescript-eslint/explicit-function-return-type': 'off',
  '@typescript-eslint/explicit-module-boundary-types': 'off',

  // Security
  'security/detect-object-injection': 'warn',
  'security/detect-non-literal-regexp': 'warn',
  'security/detect-unsafe-regex': 'error',

  // Code Quality
  'sonarjs/cognitive-complexity': ['warn', 15],
  'sonarjs/no-duplicate-string': 'warn',
  'sonarjs/no-identical-functions': 'warn',

  // Best Practices
  'unicorn/filename-case': ['warn', {
    case: 'kebabCase',
    ignore: [
      /^\.*/,           // dotfiles
      /\.test\./,       // test files
      /\.spec\./,       // spec files
      /\.config\./,     // config files
    ],
  }],
  'unicorn/prefer-node-protocol': 'warn',
  'unicorn/no-array-reduce': 'off',

  // Accessibility
  'jsx-a11y/anchor-is-valid': 'warn',
  'jsx-a11y/alt-text': 'warn',
  'jsx-a11y/aria-props': 'warn',
  'jsx-a11y/aria-proptypes': 'warn',
  'jsx-a11y/aria-unsupported-elements': 'warn',
  'jsx-a11y/role-has-required-aria-props': 'warn',
  'jsx-a11y/role-supports-aria-props': 'warn',
};

// ============================================================================
// GOVERNANCE-SPECIFIC OVERRIDES
// ============================================================================

const governanceOverrides = [
  // Canvas components - relaxed complexity due to domain complexity
  {
    files: ['**/components/canvas/**/*.ts', '**/components/canvas/**/*.tsx'],
    rules: {
      'sonarjs/cognitive-complexity': ['warn', 20],
    },
  },

  // Test files - relaxed rules
  {
    files: ['**/*.test.ts', '**/*.test.tsx', '**/*.spec.ts', '**/*.spec.tsx', '**/__tests__/**/*'],
    rules: {
      'sonarjs/no-duplicate-string': 'off',
      'unicorn/filename-case': 'off',
      'jsdoc/require-jsdoc': 'off',
    },
  },

  // Configuration files - relaxed rules
  {
    files: ['*.config.{js,ts}', 'scripts/**/*.{js,ts}'],
    rules: {
      'jsdoc/require-jsdoc': 'off',
      'unicorn/filename-case': 'off',
      'import/no-unused-modules': 'off',
    },
  },

  // Generated files - ignore
  {
    files: [
      '**/generated/**/*',
      '**/dist/**/*',
      '**/build/**/*',
      '**/node_modules/**/*',
      '**/*.d.ts',
    ],
    rules: {
      'jsdoc/require-jsdoc': 'off',
      'import/order': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
    },
  },
];

// ============================================================================
// MAIN CONFIGURATION
// ============================================================================

export default tseslint.config(
  // Base configurations
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...tseslint.configs.recommendedTypeChecked,

  // Plugin configurations
  {
    plugins: {
      import: importPlugin,
      react: reactPlugin,
      'react-hooks': reactHooksPlugin,
      jsdoc: jsdocPlugin,
      security: securityPlugin,
      sonarjs: sonarjsPlugin,
      unicorn: unicornPlugin,
      'jsx-a11y': jsxA11yPlugin,
    },
    languageOptions: {
      parserOptions: {
        project: './tsconfig.eslint.json',
        tsconfigRootDir: import.meta.dirname,
      },
    },
    settings: {
      react: {
        version: 'detect',
      },
      'import/resolver': {
        typescript: {
          alwaysTryTypes: true,
          project: './tsconfig.eslint.json',
        },
      },
    },
    rules: {
      ...standardRules,
      ...governanceRules,
    },
  },

  // Governance overrides
  ...governanceOverrides,

  // Global ignores
  {
    ignores: [
      '**/dist/**',
      '**/build/**',
      '**/node_modules/**',
      '**/.turbo/**',
      '**/coverage/**',
      '**/*.stories.*',
      '**/__tests__/**',
      '**/test/**',
      '**/test-utils/**',
      '**/__examples__/**',
      '**/*.config.*',
      '**/scripts/**',
    ],
  }
);

// ============================================================================
// PHASE TRANSITION GUIDE
// ============================================================================

/**
 * To switch to Phase 6 (Error Mode):
 * 
 * 1. Change 'warn' to 'error' for:
 *    - import/order
 *    - import/no-restricted-paths
 *    - import/no-cycle
 *    - @typescript-eslint/consistent-type-imports
 * 
 * 2. Update lint-staged to block commits on errors
 * 
 * 3. Update CI to fail on lint errors
 * 
 * 4. Remove this comment block after transition
 */
