/**
 * ESLint flat config for DMOS UI workspace.
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
    files: ["**/*.js", "**/*.jsx", "**/*.ts", "**/*.tsx"],
    languageOptions: {
      parser: require("@typescript-eslint/parser"),
      parserOptions: {
        ecmaVersion: 2022,
        sourceType: "module",
        ecmaFeatures: { jsx: true },
      },
    },
    plugins: {
      ghatana: ghatanaArchitectureRules,
    },
    rules: {
      ...(ghatanaArchitectureRules.configs?.recommended?.rules ?? {}),
    },
  },
];
