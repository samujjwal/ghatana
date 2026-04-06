/**
 * Root ESLint flat config for the Ghatana monorepo.
 *
 * Platform TypeScript packages (platform/typescript/) are type-checked via
 * tsc --noEmit. They are excluded here because the root does not bundle
 * @typescript-eslint/parser. Product-specific lint configs live alongside
 * each product (e.g. products/yappc/frontend/eslint.config.mjs).
 *
 * Ghatana architecture rules (ghatana-architecture-rules.js) are enforced
 * across all non-ignored JS/TS sources to prevent:
 *  - Cross-product imports
 *  - Imports from deleted/renamed V4.1 packages
 *  - Banned third-party libraries
 *  - Deprecated @ghatana/* package paths
 *
 * @type {import('eslint').Linter.FlatConfig[]}
 */
const ghatanaArchitectureRules = require("./eslint-rules/ghatana-architecture-rules");

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
  {
    files: [
      "**/*.js",
      "**/*.jsx",
      "**/*.ts",
      "**/*.tsx",
      "**/*.mjs",
      "**/*.cjs",
    ],
    plugins: {
      ghatana: ghatanaArchitectureRules,
    },
    rules: {
      "ghatana/no-cross-product-imports": "error",
      "ghatana/no-banned-libraries": "error",
      "ghatana/no-deprecated-ghatana-ui": "error",
      "ghatana/no-deleted-v41-packages": "error",
      "ghatana/no-design-system-internal-reimplementation": "error",
      "ghatana/no-dev-auth-in-prod": "error",
    },
  },
];
