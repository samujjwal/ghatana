// Shared Prettier configuration for DCMAAR Extension
// This configuration ensures consistent code formatting across all files

/** @type {import('prettier').Config} */
module.exports = {
  // Basic formatting
  printWidth: 100,
  tabWidth: 2,
  useTabs: false,
  semi: true,
  singleQuote: true,
  quoteProps: 'as-needed',
  
  // Arrays and objects
  trailingComma: 'es5',
  bracketSpacing: true,
  bracketSameLine: false,
  
  // Arrow functions
  arrowParens: 'always',
  
  // HTML/JSX
  htmlWhitespaceSensitivity: 'css',
  vueIndentScriptAndStyle: false,
  
  // Line endings
  endOfLine: 'lf',
  
  // Embedded language formatting
  embeddedLanguageFormatting: 'auto',
  
  // File-specific overrides
  overrides: [
    {
      files: '*.json',
      options: {
        printWidth: 80,
        tabWidth: 2,
      },
    },
    {
      files: '*.md',
      options: {
        printWidth: 80,
        proseWrap: 'preserve',
        tabWidth: 2,
      },
    },
    {
      files: '*.yml',
      options: {
        tabWidth: 2,
        singleQuote: false,
      },
    },
    {
      files: '*.yaml',
      options: {
        tabWidth: 2,
        singleQuote: false,
      },
    },
    {
      files: ['*.js', '*.ts'],
      options: {
        printWidth: 100,
        singleQuote: true,
        trailingComma: 'es5',
      },
    },
    {
      files: '*.tsx',
      options: {
        printWidth: 100,
        singleQuote: true,
        jsxSingleQuote: true,
        trailingComma: 'es5',
      },
    },
  ],
};