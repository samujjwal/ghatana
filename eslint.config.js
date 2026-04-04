/**
 * Root ESLint flat config for the Ghatana monorepo.
 *
 * This minimal config allows ESLint to run from the workspace root
 * without errors. Per-package ESLint configs (eslint.config.js or
 * eslint.config.mjs) in individual packages take precedence when ESLint
 * is run scoped to those packages.
 *
 * TypeScript-aware lint rules are enforced through per-product eslint
 * configs (e.g. products/yappc/frontend/eslint.config.mjs) which import
 * the full rule set with typescript-eslint and typed checking.
 *
 * For packages without a dedicated config, this baseline applies:
 * - No rules (type-checking is enforced via tsc --noEmit in CI)
 * - Canonical ignore patterns for generated outputs
 *
 * @type {import('eslint').Linter.FlatConfig[]}
 */
module.exports = [
  {
    // Ignore generated dirs and third-party code across all workspaces
    ignores: [
      "**/dist/**",
      "**/build/**",
      "**/node_modules/**",
      "**/.turbo/**",
      "**/*.d.ts",
      "gradlew",
      "gradlew.bat",
    ],
  },
  {
    // Apply to all TypeScript source files so ESLint doesn't emit
    // "file ignored because no matching configuration" warnings.
    files: ["**/*.ts", "**/*.tsx"],
    rules: {},
  },
];
