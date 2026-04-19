/**
 * ESLint configuration for Software-Org client web
 *
 * Configures TypeScript parser and extends root architecture rules
 */

const ghatanaArchitectureRules = require("../../../../eslint-rules/ghatana-architecture-rules");
const localRules = require('./eslint-local-rules/index');

module.exports = [
  {
    ignores: [
      "**/dist/**",
      "**/build/**",
      "**/node_modules/**",
      "**/.turbo/**",
      "**/*.d.ts",
      "public/**",
    ],
  },
  {
    files: ["**/*.js", "**/*.jsx", "**/*.ts", "**/*.tsx"],
    languageOptions: {
      parser: require("@typescript-eslint/parser"),
      parserOptions: {
        ecmaVersion: "latest",
        sourceType: "module",
        ecmaFeatures: {
          jsx: true,
        },
      },
      globals: {
        ...require("globals").browser,
        ...require("globals").node,
      },
    },
    plugins: {
      ghatana: ghatanaArchitectureRules,
      local: localRules,
    },
    rules: {
      "ghatana/no-cross-product-imports": "error",
      "ghatana/no-banned-libraries": "error",
      "ghatana/no-deprecated-ghatana-ui": "error",
      "ghatana/no-deleted-v41-packages": "error",
      "ghatana/no-design-system-internal-reimplementation": "error",
      "ghatana/no-dev-auth-in-prod": "error",
      "ghatana/no-duplicate-utilities": "warn",
      "ghatana/no-duplicate-components": "warn",
      "local/prefer-ghatana-ui": "warn",
    },
  },
];
