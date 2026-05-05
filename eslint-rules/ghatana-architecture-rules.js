/**
 * @fileoverview ESLint custom rules for monorepo architecture enforcement
 * @rules no-cross-product-imports, enforce-platform-boundaries, no-banned-libraries, no-deprecated-ghatana-ui, no-deleted-v41-packages, no-design-system-internal-reimplementation, no-rest-for-graphql-domains, no-raw-rest-fetch
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
          [
            "@yappc/canvas",
            'Import directly from "@ghatana/canvas" — @yappc/canvas was removed in Sprint 4 (zero additions over @ghatana/canvas)',
          ],
          [
            "@ghatana/audit-ui",
            'Import from "@ghatana/design-system/audit" — @ghatana/audit-ui was merged into @ghatana/design-system in Sprint 1',
          ],
          [
            "@ghatana/privacy-ui",
            'Import from "@ghatana/design-system/privacy" — @ghatana/privacy-ui was merged into @ghatana/design-system in Sprint 1',
          ],
          [
            "@ghatana/security-ui",
            'Import from "@ghatana/design-system/security" — @ghatana/security-ui was merged into @ghatana/design-system in Sprint 1',
          ],
          [
            "@ghatana/voice-ui",
            'Import from "@ghatana/design-system/voice" — @ghatana/voice-ui was merged into @ghatana/design-system in Sprint 1',
          ],
          [
            "@ghatana/nlp-ui",
            'Import from "@ghatana/design-system/nlp" — @ghatana/nlp-ui was merged into @ghatana/design-system in Sprint 1',
          ],
          [
            "@ghatana/selection-ui",
            'Import from "@ghatana/design-system/selection" — @ghatana/selection-ui was merged into @ghatana/design-system in Sprint 1',
          ],
          [
            "@dcmaar/agent-types",
            'Import from "@dcmaar/types/agent" — @dcmaar/agent-types was merged into @dcmaar/types in Sprint 2',
          ],
          [
            "@dcmaar/agent-ui",
            'Import from "@dcmaar/ui/agent" — @dcmaar/agent-ui was merged into @dcmaar/ui in Sprint 2',
          ],
          [
            "@dcmaar/browser-extension-ui",
            'Import from "@dcmaar/ui/extension" — @dcmaar/browser-extension-ui was merged into @dcmaar/ui in Sprint 2',
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

    /**
     * Rule: no-duplicate-utilities
     * Prevents re-implementing utility functions that exist in @ghatana/platform-utils.
     *
     * Catches common patterns like reimplementing truncate, capitalize, formatDate etc.
     */
    "no-duplicate-utilities": {
      meta: {
        type: "suggestion",
        docs: {
          description:
            "Disallow reimplementing utilities already in @ghatana/platform-utils",
          category: "Duplication",
          recommended: true,
        },
        schema: [],
        messages: {
          duplicateUtil:
            "⚠️ '{{name}}' reimplements a utility already in @ghatana/platform-utils. " +
            "Import it instead: import { {{name}} } from '@ghatana/platform-utils'",
        },
      },

      create(context) {
        // Common utility function names that exist in @ghatana/platform-utils
        const PLATFORM_UTILS = new Set([
          "truncate",
          "capitalize",
          "formatDate",
          "getCurrentTimestamp",
          "formatDistanceToNow",
          "cn",
          "clsx",
          "debounce",
          "throttle",
          "deepEqual",
          "deepClone",
          "omit",
          "pick",
          "groupBy",
          "chunk",
          "formatBytes",
          "formatDuration",
          "slugify",
        ]);

        // Only flag if the current file is NOT inside platform-utils
        const currentFilePath = context.getFilename();
        if (
          currentFilePath.includes("/platform/typescript/platform-utils/") ||
          currentFilePath.includes("eslint-rules/")
        ) {
          return {};
        }

        return {
          FunctionDeclaration(node) {
            if (node.id && PLATFORM_UTILS.has(node.id.name)) {
              context.report({
                node,
                messageId: "duplicateUtil",
                data: { name: node.id.name },
              });
            }
          },
          VariableDeclarator(node) {
            if (
              node.id &&
              node.id.type === "Identifier" &&
              PLATFORM_UTILS.has(node.id.name) &&
              node.init &&
              (node.init.type === "ArrowFunctionExpression" ||
                node.init.type === "FunctionExpression")
            ) {
              context.report({
                node,
                messageId: "duplicateUtil",
                data: { name: node.id.name },
              });
            }
          },
        };
      },
    },

    /**
     * Rule: no-duplicate-components
     * Prevents reimplementing UI primitives that exist in @ghatana/design-system.
     *
     * Flags component declarations whose names match canonical design-system exports.
     */
    "no-duplicate-components": {
      meta: {
        type: "suggestion",
        docs: {
          description:
            "Disallow reimplementing components already in @ghatana/design-system",
          category: "Duplication",
          recommended: true,
        },
        schema: [],
        messages: {
          duplicateComponent:
            "⚠️ '{{name}}' reimplements a component already in @ghatana/design-system. " +
            "Import it instead: import { {{name}} } from '@ghatana/design-system'",
        },
      },

      create(context) {
        // Core primitive components from @ghatana/design-system
        const DESIGN_SYSTEM_COMPONENTS = new Set([
          "Button",
          "Input",
          "Card",
          "Modal",
          "Spinner",
          "Badge",
          "Checkbox",
          "Select",
          "Tooltip",
          "Tabs",
          "Alert",
          "Avatar",
          "Dropdown",
          "Popover",
          "Progress",
          "Switch",
          "Textarea",
          "Label",
          "Separator",
        ]);

        const currentFilePath = context.getFilename();
        if (
          currentFilePath.includes("/platform/typescript/design-system/") ||
          currentFilePath.includes("eslint-rules/")
        ) {
          return {};
        }

        function checkExportedComponent(node, name) {
          if (DESIGN_SYSTEM_COMPONENTS.has(name)) {
            context.report({
              node,
              messageId: "duplicateComponent",
              data: { name },
            });
          }
        }

        return {
          ExportNamedDeclaration(node) {
            if (!node.declaration) return;
            const decl = node.declaration;
            if (
              (decl.type === "FunctionDeclaration" ||
                decl.type === "ClassDeclaration") &&
              decl.id
            ) {
              checkExportedComponent(node, decl.id.name);
            }
            if (decl.type === "VariableDeclaration") {
              for (const d of decl.declarations) {
                if (
                  d.id &&
                  d.id.type === "Identifier" &&
                  d.init &&
                  (d.init.type === "ArrowFunctionExpression" ||
                    d.init.type === "FunctionExpression")
                ) {
                  checkExportedComponent(node, d.id.name);
                }
              }
            }
          },
        };
      },
    },

    /**
     * Rule: no-dev-auth-in-prod
     *
     * Prevents any file from importing or re-exporting `devAuth.ts` /
     * `devAuthBypass` outside of files that are already guarded by a
     * `process.env.NODE_ENV !== 'production'` or `ENABLE_DEV_AUTH_BYPASS`
     * check.  This is a defence-in-depth ESLint guard complementing the
     * runtime throw already present in devAuth.ts itself.
     */
    "no-dev-auth-in-prod": {
      meta: {
        type: "error",
        docs: {
          description:
            "Disallow imports of devAuth bypass middleware outside explicitly guarded call sites",
          category: "Security",
          recommended: true,
        },
        messages: {
          devAuthImport:
            "🚨 Importing devAuth is not allowed here. " +
            "devAuthBypass is a development-only middleware — " +
            "guard the import site with `if (process.env.NODE_ENV !== 'production')` before enabling.",
        },
        schema: [],
      },

      create(context) {
        const DEV_AUTH_PATTERNS = [/devAuth/, /dev-auth/];

        return {
          ImportDeclaration(node) {
            const importPath = String(node.source.value);
            if (DEV_AUTH_PATTERNS.some((p) => p.test(importPath))) {
              // Allow within the devAuth file itself
              const filename = context.getFilename();
              if (DEV_AUTH_PATTERNS.some((p) => p.test(filename))) {
                return;
              }
              context.report({ node, messageId: "devAuthImport" });
            }
          },
        };
      },
    },

    /**
     * Rule: no-stale-file-patterns
     *
     * Prevents files with stale/archival naming patterns (*.old.*, *New.tsx)
     * from being committed. These patterns indicate temporary or duplicate files.
     */
    "no-stale-file-patterns": {
      meta: {
        type: "error",
        docs: {
          description: "Disallow files with stale/archival naming patterns",
          category: "Cleanup",
          recommended: true,
        },
        messages: {
          staleFile:
            "🗑️ File '{{filename}}' has a stale/archival pattern. " +
            "Delete or rename this file. Patterns like *.old.* and *New.tsx indicate temporary or duplicate code.",
        },
        schema: [],
      },

      create(context) {
        const filename = context.getFilename();

        // Skip node_modules and build directories
        if (
          filename.includes("node_modules") ||
          filename.includes("dist") ||
          filename.includes("build")
        ) {
          return {};
        }

        const STALE_PATTERNS = [
          /\.old\./, // RestructurePage.old.tsx
          /New\.tsx$/, // DeviceManagementNew.tsx
          /New\.ts$/, // ComponentNew.ts
          /_old\./, // index_old.ts
          /_backup\./, // config_backup.ts
          /\.backup\./, // file.backup.ts
          /\.tmp\./, // file.tmp.ts
        ];

        for (const pattern of STALE_PATTERNS) {
          if (pattern.test(filename)) {
            context.report({
              loc: { line: 0, column: 0 },
              messageId: "staleFile",
              data: { filename },
            });
            break;
          }
        }

        return {};
      },
    },

    /**
     * Rule: prefer-design-system-primitives
     *
     * Errors when raw HTML form elements and buttons are used instead of
     * @ghatana/design-system components. Enforces consistent UX,
     * accessibility, and styling across all product surfaces.
     *
     * Excluded:
     *   - Files inside @ghatana/design-system (they implement the primitives)
     */
    "prefer-design-system-primitives": {
      meta: {
        type: "suggestion",
        docs: {
          description:
            "Prefer @ghatana/design-system components over raw HTML elements",
          category: "Best Practices",
          recommended: true,
        },
        schema: [],
        messages: {
          rawPrimitive:
            "⚠️ Raw <{{tag}}> element detected. " +
            "Consider using '{{dsComponent}}' from '@ghatana/design-system' for consistent accessibility and styling.",
        },
      },

      create(context) {
        const currentFilePath = context.getFilename();
        // Skip design-system package itself
        if (currentFilePath.includes("/platform/typescript/design-system/")) {
          return {};
        }

        // Check if the file already imports from design-system
        const DS_PRIMITIVES = new Map([
          ["button", "Button"],
          ["input", "Input (or TextField)"],
          ["select", "Select"],
          ["textarea", "Textarea (or TextField)"],
          ["label", "Label"],
          ["progress", "Progress"],
          ["input[type=\"checkbox\"]", "Checkbox"],
          ["input[type=\"radio\"]", "RadioGroup"],
        ]);

        return {
          JSXOpeningElement(node) {
            const tag = node.name.name;
            if (!DS_PRIMITIVES.has(tag)) return;

            const dsComponent = DS_PRIMITIVES.get(tag);
            context.report({
              node,
              messageId: "rawPrimitive",
              data: { tag, dsComponent },
            });
          },
        };
      },
    },

    /**
     * Rule: no-platform-datagrid-duplicate
     *
     * Prevents direct imports from platform/typescript/data-grid when
     * @ghatana/design-system should be used instead. The design-system
     * DataGrid wraps the platform version with additional features.
     */
    "no-platform-datagrid-duplicate": {
      meta: {
        type: "error",
        docs: {
          description:
            "Disallow direct imports from @ghatana/data-grid - use @ghatana/design-system instead",
          category: "Architecture",
          recommended: true,
        },
        messages: {
          dataGridImport:
            "📦 Direct import from '@ghatana/data-grid' detected. " +
            "Use '@ghatana/design-system' DataGrid instead, which wraps the platform version " +
            "with additional features (stats cards, CRUD config, multiple display modes).",
        },
        schema: [],
      },

      create(context) {
        return {
          ImportDeclaration(node) {
            const importPath = String(node.source.value);

            // Check for direct data-grid imports
            if (
              importPath === "@ghatana/data-grid" ||
              importPath.startsWith("@ghatana/data-grid/")
            ) {
              context.report({
                node,
                messageId: "dataGridImport",
              });
            }
          },
        };
      },
    },

    /**
     * Rule: no-rest-for-graphql-domains (SIMP-Y9)
     *
     * Prevents raw fetch() calls targeting REST paths that are GraphQL-owned.
     * Callers must use the GraphQL client instead.
     * See docs/API_SURFACE_CANONICALIZATION.md for the full surface split.
     *
     * GraphQL-owned path prefixes (any fetch to these must use GraphQL):
     *  /api/workflows, /api/requirements, /api/approvals, /api/ai-agents,
     *  /api/versioning, /api/devsecops, /api/rate-limit (admin operations)
     */
    "no-rest-for-graphql-domains": {
      meta: {
        type: "error",
        docs: {
          description:
            "Disallow raw fetch() calls to GraphQL-owned REST path prefixes",
          category: "Architecture",
          recommended: true,
        },
        schema: [],
        messages: {
          graphqlDomainFetch:
            "🚫 '{{path}}' is GraphQL-owned. Use the GraphQL client instead of fetch(). " +
            "See docs/API_SURFACE_CANONICALIZATION.md.",
        },
      },

      create(context) {
        const GRAPHQL_OWNED_PREFIXES = [
          "/api/workflows",
          "/api/requirements",
          "/api/approvals",
          "/api/ai-agents",
          "/api/versioning",
          "/api/devsecops",
        ];

        function isGraphqlOwnedPath(value) {
          return (
            typeof value === "string" &&
            GRAPHQL_OWNED_PREFIXES.some((prefix) => value.startsWith(prefix))
          );
        }

        return {
          CallExpression(node) {
            // Match: fetch('...') or fetch(`...`)
            if (
              node.callee.type === "Identifier" &&
              node.callee.name === "fetch" &&
              node.arguments.length >= 1
            ) {
              const firstArg = node.arguments[0];
              if (
                firstArg.type === "Literal" &&
                isGraphqlOwnedPath(firstArg.value)
              ) {
                context.report({
                  node,
                  messageId: "graphqlDomainFetch",
                  data: { path: firstArg.value },
                });
              }
              // Also catch template literals with static prefix
              if (firstArg.type === "TemplateLiteral" && firstArg.quasis.length > 0) {
                const head = firstArg.quasis[0].value.raw;
                if (isGraphqlOwnedPath(head)) {
                  context.report({
                    node,
                    messageId: "graphqlDomainFetch",
                    data: { path: head + "..." },
                  });
                }
              }
            }
          },
        };
      },
    },

    /**
     * Rule: no-raw-rest-fetch (SIMP-Y19)
     *
     * In YAPPC web frontend code, raw fetch('/api/...') calls must be replaced
     * by the typed REST API client at @/lib/api (yappcApi.*).
     * This rule flags any direct fetch() call to /api/* in product source files.
     *
     * Exceptions:
     *  - Files under src/lib/api/ (the client implementation itself)
     *  - Test files (*.test.ts, *.test.tsx, __tests__/*)
     */
    "no-raw-rest-fetch": {
      meta: {
        type: "suggestion",
        docs: {
          description:
            "In YAPPC web, prefer the typed yappcApi.* client over raw fetch('/api/...')",
          category: "Migration",
          recommended: false,
        },
        schema: [],
        messages: {
          rawRestFetch:
            "⚠️ (SIMP-Y19) Use yappcApi.* from '@/lib/api' instead of raw fetch('{{path}}'). " +
            "See products/yappc/frontend/web/src/lib/api/client.ts.",
        },
      },

      create(context) {
        const filePath = context.getFilename();
        // Skip the client implementation and test files
        if (
          filePath.includes("/lib/api/") ||
          filePath.includes("__tests__") ||
          filePath.endsWith(".test.ts") ||
          filePath.endsWith(".test.tsx")
        ) {
          return {};
        }
        // Only apply inside the YAPPC web frontend
        if (!filePath.includes("/products/yappc/frontend/web/src/")) {
          return {};
        }

        return {
          CallExpression(node) {
            if (
              node.callee.type === "Identifier" &&
              node.callee.name === "fetch" &&
              node.arguments.length >= 1
            ) {
              const firstArg = node.arguments[0];
              const pathValue =
                firstArg.type === "Literal" && typeof firstArg.value === "string"
                  ? firstArg.value
                  : firstArg.type === "TemplateLiteral" && firstArg.quasis.length > 0
                  ? firstArg.quasis[0].value.raw
                  : null;

              if (pathValue && pathValue.startsWith("/api/")) {
                context.report({
                  node,
                  messageId: "rawRestFetch",
                  data: { path: pathValue },
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
