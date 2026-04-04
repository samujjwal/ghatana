/**
 * Root ESLint flat config for the Ghatana monorepo.
 *
 * Platform TypeScript packages (platform/typescript/) are type-checked via
 * tsc --noEmit. They are excluded here because the root does not bundle
 * @typescript-eslint/parser. Product-specific lint configs live alongside
 * each product (e.g. products/yappc/frontend/eslint.config.mjs).
 *
 * @type {import('eslint').Linter.FlatConfig[]}
 */
module.exports = [
  {
    ignores: [
      "**/dist/**",
      "**/build/**",
      "**/node_modules/**",
      "**/.turbo/**",
      "**/*.d.ts",
      "gradlew",
      "gradlew.bat",
      "platform/typescript/**",
    ],
  },
];
