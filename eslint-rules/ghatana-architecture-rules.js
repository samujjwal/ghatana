/**
 * @fileoverview ESLint custom rules for monorepo architecture enforcement
 * @rules no-cross-product-imports, enforce-platform-boundaries, no-banned-libraries, no-deprecated-ghatana-ui, no-deleted-v41-packages, no-design-system-internal-reimplementation
 */

"use strict";

const fs = require("fs");
const path = require("path");

// Load dependency policy
const DEPENDENCY_POLICY = require("./dependency-policy.json");

// Product boundaries - no cross-product imports allowed
const PRODUCT_BOUNDARIES = {
  yappc: { allow: ["yappc", "platform", "shared-services"] },
  flashit: { allow: ["flashit", "platform", "shared-services"] },
  tutorputor: { allow: ["tutorputor", "platform", "shared-services"] },
  "data-cloud": { allow: ["data-cloud", "platform", "shared-services"] },
  dcmaar: { allow: ["dcmaar", "platform", "shared-services"] },
  "audio-video": { allow: ["audio-video", "platform", "shared-services"] },
  aep: { allow: ["aep", "platform", "shared-services"] },
};

// Platform layers (dependency direction: foundation → platform → domain → app)
const PLATFORM_LAYERS = {
  "@ghatana/tokens": 0, // Foundation
  "@ghatana/theme": 0, // Foundation
  "@ghatana/platform-utils": 1, // Platform base
  "@ghatana/design-system": 2, // Platform capabilities
  "@ghatana/canvas": 2, // Platform capabilities
  "@ghatana/crdt": 2, // Platform capabilities
  "@ghatana/realtime": 2, // Platform capabilities
  "@ghatana/event-cloud": 3, // Platform integration
};

// Banned libraries
const BANNED_LIBRARIES = [
  "lodash",
  "axios",
  "moment",
  "jquery",
  "request",
  "uuid", // Use crypto.randomUUID() instead
  "classnames", // Use clsx or @ghatana/platform-utils/cn
];

module.exports = {
  meta: {
    name: "ghatana-architecture-rules",
    version: "1.0.0",
  },

  rules: {
    /**
     * Rule: no-cross-product-imports
     * Prevents importing from other product domains
     */
    "no-cross-product-imports": {
      meta: {
        type: "error",
        docs: {
          description: "Disallow imports from other product domains",
          category: "Architecture",
          recommended: true,
        },
        schema: [],
        messages: {
          crossProductImport:
            "🚫 Cross-product import detected: '{{import}}' belongs to '{{targetProduct}}'. Products must not depend on each other. Use shared-services or platform instead.",
        },
      },

      create(context) {
        const currentFilePath = context.getFilename();
        const currentProduct = getProductFromPath(currentFilePath);

        function checkImport(node, importPath) {
          // Only check scoped packages
          if (!importPath.startsWith("@")) return;

          const targetProduct = getProductFromPackage(importPath);

          if (
            targetProduct &&
            currentProduct &&
            targetProduct !== currentProduct
          ) {
            // Check if allowed
            const allowed = PRODUCT_BOUNDARIES[currentProduct]?.allow || [];
            if (!allowed.includes(targetProduct)) {
              context.report({
                node,
                messageId: "crossProductImport",
                data: { import: importPath, targetProduct },
              });
            }
          }
        }

        return {
          ImportDeclaration(node) {
            checkImport(node, node.source.value);
          },
          ExportNamedDeclaration(node) {
            if (node.source) {
              checkImport(node, node.source.value);
            }
          },
          ExportAllDeclaration(node) {
            checkImport(node, node.source.value);
          },
          CallExpression(node) {
            // Check dynamic imports
            if (
              node.callee.type === "Import" &&
              node.arguments[0]?.type === "Literal"
            ) {
              checkImport(node, node.arguments[0].value);
            }
          },
        };
      },
    },

    /**
     * Rule: enforce-platform-boundaries
     * Ensures platform libraries follow layering rules
     */
    "enforce-platform-boundaries": {
      meta: {
        type: "error",
        docs: {
          description: "Enforce platform library layering rules",
          category: "Architecture",
          recommended: true,
        },
        schema: [],
        messages: {
          invalidLayerDependency:
            "🚫 Invalid layer dependency: '{{import}}' (layer {{targetLayer}}) cannot be imported by '{{current}}' (layer {{currentLayer}}). Lower layers cannot depend on higher layers.",
        },
      },

      create(context) {
        const currentFilePath = context.getFilename();
        const currentPackage = getPackageNameFromPath(currentFilePath);
        const currentLayer = PLATFORM_LAYERS[currentPackage];

        // Only enforce for platform packages
        if (currentLayer === undefined) return {};

        function checkLayer(node, importPath) {
          const targetPackage = getBasePackageName(importPath);
          const targetLayer = PLATFORM_LAYERS[targetPackage];

          if (targetLayer !== undefined && targetLayer > currentLayer) {
            context.report({
              node,
              messageId: "invalidLayerDependency",
              data: {
                import: importPath,
                targetLayer,
                current: currentPackage,
                currentLayer,
              },
            });
          }
        }

        return {
          ImportDeclaration(node) {
            checkLayer(node, node.source.value);
          },
        };
      },
    },

    /**
     * Rule: no-banned-libraries
     * Prevents use of banned/deprecated libraries
     */
    "no-banned-libraries": {
      meta: {
        type: "error",
        docs: {
          description: "Disallow banned libraries",
          category: "Best Practices",
          recommended: true,
        },
        schema: [],
        messages: {
          bannedLibrary: "🚫 '{{library}}' is banned. {{alternative}}",
        },
      },

      create(context) {
        const ALTERNATIVES = {
          lodash: "Use native ES6+ or @ghatana/platform-utils",
          axios: "Use native fetch or @ghatana/http-client",
          moment: "Use date-fns or native Intl.DateTimeFormat",
          jquery: "Use native DOM APIs or React refs",
          request: "Use native fetch or @ghatana/http-client",
          uuid: "Use crypto.randomUUID() or nanoid",
          classnames: "Use clsx or @ghatana/platform-utils/cn",
        };

        function checkImport(node, importPath) {
          for (const banned of BANNED_LIBRARIES) {
            if (importPath === banned || importPath.startsWith(`${banned}/`)) {
              context.report({
                node,
                messageId: "bannedLibrary",
                data: {
                  library: banned,
                  alternative:
                    ALTERNATIVES[banned] ||
                    "Find an alternative in the approved tech stack",
                },
              });
            }
          }
        }

        return {
          ImportDeclaration(node) {
            checkImport(node, node.source.value);
          },
          CallExpression(node) {
            if (
              node.callee.type === "Import" &&
              node.arguments[0]?.type === "Literal"
            ) {
              checkImport(node, node.arguments[0].value);
            }
          },
        };
      },
    },

    /**
     * Rule: no-platform-to-product-imports
     * platform/typescript modules must not import from products/
     *
     * This is the TypeScript equivalent of the Java ArchUnit rule:
     * platformMustNotImportProducts() in GhatanaBoundaryRules.java
     *
     * BDY-6-4: Added 2026-03-21 as part of architecture guard hardening.
     */
    "no-platform-to-product-imports": {
      meta: {
        type: "error",
        docs: {
          description:
            "Disallow imports from products/ inside platform/typescript modules",
          category: "Architecture",
          recommended: true,
        },
        schema: [],
        messages: {
          platformImportsProduct:
            "🚫 platform/typescript module imports from products/: '{{import}}'. Platform code must be product-agnostic. Move shared code to platform or create product-local code. See BOUNDARY_IMPLEMENTATION_PLAN.md §BDY-6.",
        },
      },

      create(context) {
        const currentFilePath = context.getFilename();

        // Only enforce for files inside platform/typescript/
        if (!currentFilePath.includes("/platform/typescript/")) return {};

        function checkImport(node, importPath) {
          // Catch relative imports that escape into products/ (../../products/...)
          if (importPath.includes("/products/")) {
            context.report({
              node,
              messageId: "platformImportsProduct",
              data: { import: importPath },
            });
          }
          // Catch scoped package imports that belong to a product scope
          const PRODUCT_SCOPES = [
            "@yappc/",
            "@flashit/",
            "@tutorputor/",
            "@data-cloud/",
            "@dcmaar/",
            "@audio-video/",
            "@aep/",
          ];
          for (const scope of PRODUCT_SCOPES) {
            if (importPath.startsWith(scope)) {
              context.report({
                node,
                messageId: "platformImportsProduct",
                data: { import: importPath },
              });
              return;
            }
          }
        }

        return {
          ImportDeclaration(node) {
            checkImport(node, node.source.value);
          },
          ExportAllDeclaration(node) {
            if (node.source) checkImport(node, node.source.value);
          },
          ExportNamedDeclaration(node) {
            if (node.source) checkImport(node, node.source.value);
          },
        };
      },
    },

    /**
     * Rule: no-deprecated-ghatana-ui
     * Prevents use of deprecated @ghatana/ui
     */
    "no-deprecated-ghatana-ui": {
      meta: {
        type: "error",
        docs: {
          description: "Disallow deprecated @ghatana/ui package",
          category: "Migration",
          recommended: true,
        },
        schema: [],
        messages: {
          deprecatedPackage:
            "🚫 @ghatana/ui is deprecated. Use @ghatana/design-system for platform components or create product-local components.",
        },
      },

      create(context) {
        return {
          ImportDeclaration(node) {
            const importPath = node.source.value;
            if (
              importPath === "@ghatana/ui" ||
              importPath.startsWith("@ghatana/ui/")
            ) {
              context.report({
                node,
                messageId: "deprecatedPackage",
              });
            }
          },
        };
      },
    },

    /**
     * Rule: prefer-platform-utils
     * Encourages use of platform utilities over external libs
     */
    "prefer-platform-utils": {
      meta: {
        type: "warn",
        docs: {
          description: "Prefer @ghatana/platform-utils over external utilities",
          category: "Best Practices",
          recommended: true,
        },
        schema: [],
        messages: {
          usePlatformUtils:
            "⚠️ Consider using @ghatana/platform-utils instead of external libraries for common utilities.",
        },
      },

      create(context) {
        const REPLACEMENT_PATTERNS = [
          { pattern: /^date-fns/, alternative: "@ghatana/platform-utils/date" },
          { pattern: /^lodash-es/, alternative: "@ghatana/platform-utils" },
        ];

        return {
          ImportDeclaration(node) {
            const importPath = node.source.value;
            for (const { pattern } of REPLACEMENT_PATTERNS) {
              if (pattern.test(importPath)) {
                context.report({
                  node,
                  messageId: "usePlatformUtils",
                });
              }
            }
          },
        };
      },
    },

    /**
     * Rule: no-deleted-v41-packages
     * Prevents re-introduction of symbols deleted/renamed in V4.1 deduplication pass.
     *
     * Deleted in V4.1:
     *  - @ghatana/ui                  → @ghatana/design-system (blocked by no-deprecated-ghatana-ui)
     *  - @yappc/compat-*              → canonical packages (blocked by product compat ESLint)
     *
     * TypeScript symbols that were audited and confirmed NOT to be duplicates
     * (all had different semantics) — no rule needed for those.
     *
     * This rule focuses on blocking patterns that WOULD cause duplication:
     *  - Importing from re-exported barrel files that duplicate canonical exports
     *  - Importing stale design-system internal paths that bypass the public API
     */
    "no-deleted-v41-packages": {
      meta: {
        type: "error",
        docs: {
          description:
            "Disallow import paths deleted or reorganized in platform V4.1 deduplication",
          category: "Migration",
          recommended: true,
        },
        schema: [],
        messages: {
          deletedPath:
            "🚫 {{importPath}} references a removed or reorganized V4.1 path. {{fix}}",
        },
      },

      create(context) {
        /** Map from banned import path prefix → helpful fix message */
        const DELETED_PATHS = new Map([
          [
            "@ghatana/design-system/src/components/CommandPalette",
            'Use the public API: import { CommandPalette } from "@ghatana/design-system"',
          ],
          [
            "@ghatana/design-system/src/typography/List",
            'Use the public API: import { List } from "@ghatana/design-system" (via typography index)',
          ],
          [
            "@ghatana/design-system/components/CommandPalette",
            'Use the public API: import { CommandPalette } from "@ghatana/design-system"',
          ],
        ]);

        return {
          ImportDeclaration(node) {
            const importPath = node.source.value;
            for (const [bannedPrefix, fix] of DELETED_PATHS) {
              if (
                importPath === bannedPrefix ||
                importPath.startsWith(bannedPrefix + "/")
              ) {
                context.report({
                  node,
                  messageId: "deletedPath",
                  data: { importPath, fix },
                });
              }
            }
          },
        };
      },
    },

    /**
     * Rule: no-design-system-internal-reimplementation
     *
     * Prevents re-implementing components that have been consolidated into
     * canonical locations within @ghatana/design-system (V4.1 consolidation).
     *
     * Specifically guards against:
     * - Reimplementing CommandPalette outside molecules/CommandPalette.tsx
     *   (components/CommandPalette.tsx is now a re-export, not a reimplementation)
     * - Importing deep internal paths of @ghatana/design-system that bypass the
     *   public barrel (src/index.ts). Always import from "@ghatana/design-system".
     */
    "no-design-system-internal-reimplementation": {
      meta: {
        type: "problem",
        docs: {
          description:
            "Prevents importing from @ghatana/design-system internal subpaths (V4.1 consolidation)",
          category: "Architecture",
          recommended: true,
        },
        messages: {
          internalSubpath:
            "🚫 Deep import from {{importPath}} bypasses the design-system public API. " +
            'Use: import { ... } from "@ghatana/design-system" instead.',
        },
        schema: [],
      },

      create(context) {
        // Blocked internal subpath patterns — always use the canonical barrel export
        const BLOCKED_SUBPATHS = [
          "@ghatana/design-system/src/",
          "@ghatana/design-system/dist/components/",
          "@ghatana/design-system/dist/molecules/",
          "@ghatana/design-system/dist/organisms/",
          "@ghatana/design-system/dist/typography/",
        ];

        return {
          ImportDeclaration(node) {
            const importPath = node.source.value;
            for (const blocked of BLOCKED_SUBPATHS) {
              if (importPath.startsWith(blocked)) {
                context.report({
                  node,
                  messageId: "internalSubpath",
                  data: { importPath },
                });
              }
            }
          },
        };
      },
    },
  },
};

// Helper functions
function getProductFromPath(filePath) {
  const match = filePath.match(/\/products\/([^/]+)/);
  return match ? match[1] : null;
}

function getProductFromPackage(packageName) {
  if (packageName.startsWith("@yappc/")) return "yappc";
  if (packageName.startsWith("@flashit/")) return "flashit";
  if (packageName.startsWith("@tutorputor/")) return "tutorputor";
  if (packageName.startsWith("@data-cloud/")) return "data-cloud";
  if (packageName.startsWith("@dcmaar/")) return "dcmaar";
  if (packageName.startsWith("@audio-video/")) return "audio-video";
  if (packageName.startsWith("@aep/")) return "aep";
  return null;
}

function getPackageNameFromPath(filePath) {
  // Try to read from nearest package.json
  let dir = path.dirname(filePath);
  while (dir !== path.dirname(dir)) {
    const pkgPath = path.join(dir, "package.json");
    if (fs.existsSync(pkgPath)) {
      try {
        const pkg = JSON.parse(fs.readFileSync(pkgPath, "utf-8"));
        return pkg.name;
      } catch {
        // Continue
      }
    }
    dir = path.dirname(dir);
  }
  return null;
}

function getBasePackageName(importPath) {
  // Extract @scope/name from @scope/name/subpath
  const match = importPath.match(/^(@[^/]+\/[^/]+)/);
  return match ? match[1] : importPath;
}
