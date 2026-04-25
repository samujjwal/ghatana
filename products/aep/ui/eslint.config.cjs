/**
 * ESLint flat config for AEP UI workspace.
 *
 * Extends the root Ghatana architecture rules and adds TypeScript parser support.
 */

const ghatanaArchitectureRules = require("../../../eslint-rules/ghatana-architecture-rules");

module.exports = [
  {
    ignores: [
      "**/dist/**",
      "**/build/**",
      "**/node_modules/**",
      "**/.turbo/**",
      "**/*.d.ts",
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
    languageOptions: {
      parser: require("@typescript-eslint/parser"),
      parserOptions: {
        ecmaVersion: "latest",
        sourceType: "module",
        project: "./tsconfig.json",
      },
    },
    plugins: {
      ghatana: ghatanaArchitectureRules,
      "react-hooks": require("eslint-plugin-react-hooks"),
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
      "ghatana/prefer-design-system-primitives": "warn",
      "react-hooks/exhaustive-deps": "off",
    },
  },
];
