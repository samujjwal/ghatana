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
const i18nCoverageRule = require("./eslint-rules/i18n-coverage-rule");
const noRawFetchRule = require("./eslint-rules/no-raw-fetch");
const noProductionTodosRule = require("./eslint-rules/no-production-todos");
const enforcePackageBoundariesRule = require("./eslint-rules/enforce-package-boundaries");

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
      "products/yappc/config/agents/event-schemas/**",
      // TypeScript files under products/yappc/frontend are handled by their own
      // eslint.config.mjs which registers @typescript-eslint/parser. Excluding
      // them here prevents the root CJS config (no TS parser) from issuing
      // spurious "Unexpected token interface/type/enum" parse errors in VS Code.
      "products/yappc/frontend/**",
      // data-cloud UI apps each have their own eslint.config.js with the TS parser.
      "products/data-cloud/delivery/ui/**",
      "products/data-cloud/planes/action/ui/**",
      "products/data-cloud/planes/action/gateway/**",
    ],
  },
  {
    files: ["**/*.js", "**/*.jsx", "**/*.ts", "**/*.tsx"],
    plugins: {
      ghatana: ghatanaArchitectureRules,
      i18n: {
        rules: {
          "i18n-coverage": i18nCoverageRule,
        },
      },
      "no-raw-fetch": {
        rules: {
          "no-raw-fetch": noRawFetchRule,
        },
      },
      "no-production-todos": {
        rules: {
          "no-production-todos": noProductionTodosRule,
        },
      },
      "enforce-package-boundaries": {
        rules: {
          "enforce-package-boundaries": enforcePackageBoundariesRule,
        },
      },
    },
    rules: {
      "ghatana/no-cross-product-imports": "error",
      "ghatana/no-platform-to-product-imports": "error",
      "ghatana/no-banned-libraries": "error",
      "ghatana/no-deprecated-ghatana-ui": "error",
      "ghatana/no-deleted-v41-packages": "error",
      "ghatana/no-design-system-internal-reimplementation": "error",
      "ghatana/no-dev-auth-in-prod": "error",
      "ghatana/no-duplicate-utilities": "error",
      "ghatana/no-duplicate-components": "error",
      "ghatana/no-yappc-direct-platform-imports": "error",
      "i18n/i18n-coverage": "warn",
      "no-raw-fetch/no-raw-fetch": "error",
      "no-production-todos/no-production-todos": "error",
      "enforce-package-boundaries/enforce-package-boundaries": "error",
      "no-restricted-imports": [
        "error",
        {
          paths: [
            {
              name: "@ghatana/sso-client",
              message:
                "Direct @ghatana/sso-client import is not allowed in product code. Use @yappc/auth or the platform auth abstraction.",
            },
          ],
          patterns: [
            {
              group: [
                "**/store/StateManager",
                "**/store/StateManager.ts",
                "@yappc/state/store/StateManager",
              ],
              message:
                "StateManager is deprecated. Import createAtom / createPersistentAtom / createDerivedAtom from @ghatana/state (re-exported via @yappc/state) instead.",
            },
          ],
        },
      ],
    },
  },
];
