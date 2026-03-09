// @ts-nocheck
// ESLint configuration - extends shared config with project-specific overrides
import baseConfig from './config/eslint.config.js';

/** @type {import('eslint').Linter.FlatConfig[]} */
export default [
  ...baseConfig,
  
  // Project-specific extensions and overrides
  {
    ignores: [
      // Additional project-specific ignores beyond base config
      'dist-scripts/**',
      'public/**',
      'scripts/**/*.js',
      'eslint-debt-prevention.js',
    ],
  },
  
  // Project-specific TypeScript rule overrides
  {
    files: ['**/*.ts', '**/*.tsx'],
    rules: {
      // Allow namespaces for this project's architecture
      '@typescript-eslint/no-namespace': [
        'error',
        {
          allowDeclarations: true,
          allowDefinitionFiles: true,
        },
      ],
    },
  },
  
  // Test and script specific overrides
  {
    files: [
      'tests/**/*.ts',
      'tests/**/*.tsx',
      'scripts/**/*.js',
      'scripts/**/*.ts',
      'scripts/**/*.tsx',
    ],
    rules: {
      '@typescript-eslint/no-require-imports': 'off',
      'import/order': 'off',
    },
  },
];
