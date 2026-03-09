// @ts-nocheck
// Minimal flat ESLint config for services/desktop to satisfy ESLint v9
// Disable TypeScript checking in this config file to avoid editor diagnostic noise.
module.exports = [
  {
    files: ['**/*.ts', '**/*.tsx', '**/*.js', '**/*.jsx'],
    languageOptions: {
        parser: require('@typescript-eslint/parser'),
        parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
        ecmaFeatures: { jsx: true },
      },
        globals: {
        window: 'readonly',
        document: 'readonly',
        process: 'readonly',
      },
    },
      plugins: {
        '@typescript-eslint': require('@typescript-eslint/eslint-plugin'),
      },
      rules: {
        'no-unused-vars': 'off',
        '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      },
  },
];
