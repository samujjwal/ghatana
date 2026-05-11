"use strict";

/**
 * Unit tests for ghatana-architecture-rules.js
 *
 * Uses ESLint's built-in RuleTester so no extra test runner is required.
 * Run with: node eslint-rules/ghatana-architecture-rules.test.js
 *
 * Exit 0 = all tests passed, non-zero = failures.
 *
 * @doc.type   tooling
 * @doc.purpose Unit tests for custom ESLint architecture enforcement rules
 * @doc.layer  infrastructure
 */

const { RuleTester } = require("eslint");
const rules = require("./ghatana-architecture-rules").rules;

// ESLint 9 flat-config: RuleTester expects languageOptions, not parserOptions.
const tester = new RuleTester({
  languageOptions: {
    ecmaVersion: 2022,
    sourceType: "module",
  },
});

let passed = 0;
let failed = 0;

/**
 * Runs a RuleTester suite, catching failures so all suites execute.
 * @param {string} suiteName
 * @param {string} ruleName
 * @param {import('eslint').RuleTester.RunTests} tests
 */
function runSuite(suiteName, ruleName, tests) {
  try {
    tester.run(suiteName, rules[ruleName], tests);
    console.log(`  ✅ ${suiteName}`);
    passed++;
  } catch (err) {
    console.error(`  ❌ ${suiteName}`);
    console.error(`     ${err.message.split("\n")[0]}`);
    failed++;
  }
}

console.log("\nghatana-architecture-rules – unit tests\n");

// =============================================================================
// no-cross-product-imports
// =============================================================================
console.log("no-cross-product-imports");

runSuite(
  "allows import within the same product",
  "no-cross-product-imports",
  {
    valid: [
      {
        // yappc file importing from @yappc/* is intra-product → allowed
        filename: "/repo/products/yappc/ui/src/component.ts",
        code: `import { Foo } from "@yappc/ui";`,
      },
      {
        // yappc file importing from @ghatana/platform-utils → platform → allowed
        filename: "/repo/products/yappc/ui/src/component.ts",
        code: `import { cn } from "@ghatana/platform-utils";`,
      },
      {
        // relative import — no product scope, always allowed
        filename: "/repo/products/yappc/ui/src/component.ts",
        code: `import { helper } from "../utils/helper";`,
      },
      {
        // data-cloud importing its own lib → allowed
        filename: "/repo/products/data-cloud/delivery/ui/src/page.ts",
        code: `import { DataTable } from "@data-cloud/ui";`,
      },
    ],
    invalid: [
      {
        // yappc file importing from @flashit/* → cross-product → error
        filename: "/repo/products/yappc/ui/src/component.ts",
        code: `import { Card } from "@flashit/ui";`,
        errors: [{ messageId: "crossProductImport" }],
      },
      {
        // data-cloud file importing from @yappc/* → cross-product → error
        filename: "/repo/products/data-cloud/delivery/ui/src/page.ts",
        code: `import { Widget } from "@yappc/canvas";`,
        errors: [{ messageId: "crossProductImport" }],
      },
      {
        // aep file importing from @audio-video/* → cross-product → error
        filename: "/repo/products/data-cloud/planes/action/src/handler.ts",
        code: `import { AudioStream } from "@audio-video/sdk";`,
        errors: [{ messageId: "crossProductImport" }],
      },
    ],
  }
);

// =============================================================================
// no-banned-libraries
// =============================================================================
console.log("\nno-banned-libraries");

runSuite("bans lodash", "no-banned-libraries", {
  valid: [
    { code: `import { cn } from "@ghatana/platform-utils";` },
    { code: `import fetch from "node-fetch";` },
  ],
  invalid: [
    {
      code: `import _ from "lodash";`,
      errors: [{ messageId: "bannedLibrary" }],
    },
    {
      code: `import get from "lodash/get";`,
      errors: [{ messageId: "bannedLibrary" }],
    },
    {
      code: `import axios from "axios";`,
      errors: [{ messageId: "bannedLibrary" }],
    },
    {
      code: `import moment from "moment";`,
      errors: [{ messageId: "bannedLibrary" }],
    },
    {
      code: `import $ from "jquery";`,
      errors: [{ messageId: "bannedLibrary" }],
    },
    {
      code: `import { v4 } from "uuid";`,
      errors: [{ messageId: "bannedLibrary" }],
    },
    {
      code: `import cx from "classnames";`,
      errors: [{ messageId: "bannedLibrary" }],
    },
  ],
});

// =============================================================================
// no-platform-to-product-imports
// =============================================================================
console.log("\nno-platform-to-product-imports");

runSuite(
  "prevents platform importing from products",
  "no-platform-to-product-imports",
  {
    valid: [
      {
        // platform file importing another platform package → OK
        filename: "/repo/platform/typescript/design-system/src/Button.tsx",
        code: `import { tokens } from "@ghatana/tokens";`,
      },
      {
        // product file importing from @yappc/* → product code, not platform → not checked
        filename: "/repo/products/yappc/ui/src/page.tsx",
        code: `import { Widget } from "@yappc/ui";`,
      },
    ],
    invalid: [
      {
        // platform file importing @yappc/ → violates platform→product rule
        filename: "/repo/platform/typescript/design-system/src/Button.tsx",
        code: `import { Widget } from "@yappc/canvas";`,
        errors: [{ messageId: "platformImportsProduct" }],
      },
      {
        // platform file importing from products/ via relative path
        filename: "/repo/platform/typescript/utils/src/helper.ts",
        code: `import { x } from "../../products/yappc/ui/src/thing";`,
        errors: [{ messageId: "platformImportsProduct" }],
      },
      {
        // platform file importing @data-cloud/ scoped package
        filename: "/repo/platform/typescript/realtime/src/client.ts",
        code: `import { DataCloudClient } from "@data-cloud/api";`,
        errors: [{ messageId: "platformImportsProduct" }],
      },
    ],
  }
);

// =============================================================================
// no-deprecated-ghatana-ui
// =============================================================================
console.log("\nno-deprecated-ghatana-ui");

runSuite("bans @ghatana/ui", "no-deprecated-ghatana-ui", {
  valid: [
    { code: `import { Button } from "@ghatana/design-system";` },
    { code: `import { cn } from "@ghatana/platform-utils";` },
  ],
  invalid: [
    {
      code: `import { Button } from "@ghatana/ui";`,
      errors: [{ messageId: "deprecatedPackage" }],
    },
    {
      code: `import { Input } from "@ghatana/ui/components/Input";`,
      errors: [{ messageId: "deprecatedPackage" }],
    },
  ],
});

// =============================================================================
// no-deleted-v41-packages
// =============================================================================
console.log("\nno-deleted-v41-packages");

runSuite("bans deleted V4.1 paths", "no-deleted-v41-packages", {
  valid: [
    { code: `import { CommandPalette } from "@ghatana/design-system";` },
    { code: `import { DataGrid } from "@ghatana/design-system";` },
  ],
  invalid: [
    {
      code: `import { CommandPalette } from "@ghatana/design-system/src/components/CommandPalette";`,
      errors: [{ messageId: "deletedPath" }],
    },
    {
      code: `import Canvas from "@yappc/canvas";`,
      errors: [{ messageId: "deletedPath" }],
    },
    {
      code: `import { AuditLog } from "@ghatana/audit-ui";`,
      errors: [{ messageId: "deletedPath" }],
    },
    {
      code: `import { PrivacyPanel } from "@ghatana/privacy-ui";`,
      errors: [{ messageId: "deletedPath" }],
    },
    {
      code: `import { AgentType } from "@dcmaar/agent-types";`,
      errors: [{ messageId: "deletedPath" }],
    },
  ],
});

// =============================================================================
// no-design-system-internal-reimplementation
// =============================================================================
console.log("\nno-design-system-internal-reimplementation");

runSuite(
  "bans deep design-system subpath imports",
  "no-design-system-internal-reimplementation",
  {
    valid: [
      { code: `import { Button } from "@ghatana/design-system";` },
      { code: `import { tokens } from "@ghatana/tokens";` },
    ],
    invalid: [
      {
        code: `import { Button } from "@ghatana/design-system/src/components/Button";`,
        errors: [{ messageId: "internalSubpath" }],
      },
      {
        code: `import { List } from "@ghatana/design-system/dist/typography/List";`,
        errors: [{ messageId: "internalSubpath" }],
      },
      {
        code: `import { Modal } from "@ghatana/design-system/dist/molecules/Modal";`,
        errors: [{ messageId: "internalSubpath" }],
      },
    ],
  }
);

// =============================================================================
// no-platform-datagrid-duplicate
// =============================================================================
console.log("\nno-platform-datagrid-duplicate");

runSuite("bans direct @ghatana/data-grid imports", "no-platform-datagrid-duplicate", {
  valid: [
    { code: `import { DataGrid } from "@ghatana/design-system";` },
  ],
  invalid: [
    {
      code: `import { DataGrid } from "@ghatana/data-grid";`,
      errors: [{ messageId: "dataGridImport" }],
    },
    {
      code: `import { Column } from "@ghatana/data-grid/Column";`,
      errors: [{ messageId: "dataGridImport" }],
    },
  ],
});

// =============================================================================
// no-dev-auth-in-prod
// =============================================================================
console.log("\nno-dev-auth-in-prod");

runSuite("bans devAuth imports outside guard", "no-dev-auth-in-prod", {
  valid: [
    {
      // Regular auth — allowed
      filename: "/repo/products/yappc/src/auth.ts",
      code: `import { verifyToken } from "./authService";`,
    },
  ],
  invalid: [
    {
      filename: "/repo/products/yappc/src/middleware.ts",
      code: `import { devAuthBypass } from "./devAuth";`,
      errors: [{ messageId: "devAuthImport" }],
    },
    {
      filename: "/repo/products/data-cloud/planes/action/src/server.ts",
      code: `import bypass from "../utils/dev-auth";`,
      errors: [{ messageId: "devAuthImport" }],
    },
  ],
});

// =============================================================================
// prefer-design-system-primitives
// =============================================================================
console.log("\nprefer-design-system-primitives");

const jsxTester = new RuleTester({
  languageOptions: {
    ecmaVersion: 2022,
    sourceType: "module",
    parserOptions: {
      ecmaFeatures: { jsx: true },
    },
  },
});

function runJsxSuite(suiteName, ruleName, tests) {
  try {
    jsxTester.run(suiteName, rules[ruleName], tests);
    console.log(`  ✅ ${suiteName}`);
    passed++;
  } catch (err) {
    console.error(`  ❌ ${suiteName}`);
    console.error(`     ${err.message.split("\n")[0]}`);
    failed++;
  }
}

runJsxSuite(
  "flags raw HTML interactive controls regardless of design-system imports",
  "prefer-design-system-primitives",
  {
    valid: [
      {
        // design-system file implementing Button — excluded by path
        filename: "/repo/platform/typescript/design-system/src/atoms/Button.tsx",
        code: `export const Button = () => <button type="button">click</button>;`,
      },
      {
        // Using design-system Button — no raw control
        filename: "/repo/products/data-cloud/planes/action/ui/src/components/MyWidget.tsx",
        code: `
          import { Button } from "@ghatana/design-system";
          export const MyWidget = () => <Button onClick={() => {}}>Go</Button>;
        `,
      },
    ],
    invalid: [
      {
        // Raw button even when design-system is imported
        filename: "/repo/products/data-cloud/planes/action/ui/src/components/MyWidget.tsx",
        code: `
          import { Button } from "@ghatana/design-system";
          export const Mixed = () => <button type="button">raw</button>;
        `,
        errors: [{ messageId: "rawPrimitive" }],
      },
      {
        // Raw input — no design-system import
        filename: "/repo/products/data-cloud/planes/action/ui/src/components/SearchBox.tsx",
        code: `export const SearchBox = () => <input type="text" />;`,
        errors: [{ messageId: "rawPrimitive" }],
      },
      {
        // Raw select — no design-system import
        filename: "/repo/products/data-cloud/planes/action/ui/src/components/LevelPicker.tsx",
        code: `export const LevelPicker = () => <select><option value="a">A</option></select>;`,
        errors: [{ messageId: "rawPrimitive" }],
      },
      {
        // Raw textarea — with design-system import (mixed file scenario)
        filename: "/repo/products/data-cloud/planes/action/ui/src/components/NoteField.tsx",
        code: `
          import { TextField } from "@ghatana/design-system";
          export const NoteField = () => <textarea rows={3} />;
        `,
        errors: [{ messageId: "rawPrimitive" }],
      },
    ],
  }
);

// =============================================================================
// no-yappc-direct-platform-imports (task 3.1.2)
// =============================================================================
console.log("\nno-yappc-direct-platform-imports");

runSuite(
  "prevents YAPPC from importing platform modules that should use contract facades",
  "no-yappc-direct-platform-imports",
  {
    valid: [
      {
        // yappc file importing from @data-cloud/api (allowed facade, task 3.1.5) → allowed
        filename: "/repo/products/yappc/ui/src/component.ts",
        code: `import { AgentClient } from "@data-cloud/api";`,
      },
      {
        // yappc file importing from @aep/api (allowed facade, task 3.1.5) → allowed
        filename: "/repo/products/yappc/ui/src/component.ts",
        code: `import { WorkflowClient } from "@aep/api";`,
      },
      {
        // yappc file importing from @ghatana/platform-utils (not in contract facade list) → allowed
        filename: "/repo/products/yappc/ui/src/component.ts",
        code: `import { cn } from "@ghatana/platform-utils";`,
      },
      {
        // yappc file importing from @ghatana/design-system (not in contract facade list) → allowed
        filename: "/repo/products/yappc/ui/src/component.ts",
        code: `import { Button } from "@ghatana/design-system";`,
      },
      {
        // non-yappc file importing platform modules → not checked
        filename: "/repo/products/data-cloud/ui/src/page.ts",
        code: `import { AgentCore } from "@ghatana/agent-core";`,
      },
    ],
    invalid: [
      {
        // yappc file importing @ghatana/agent-core directly → should use @data-cloud/api
        filename: "/repo/products/yappc/ui/src/component.ts",
        code: `import { AgentCore } from "@ghatana/agent-core";`,
        errors: [{ messageId: "directPlatformImport" }],
      },
      {
        // yappc file importing @ghatana/ai-integration directly → should use @data-cloud/api
        filename: "/repo/products/yappc/services/agent.ts",
        code: `import { AIIntegration } from "@ghatana/ai-integration";`,
        errors: [{ messageId: "directPlatformImport" }],
      },
      {
        // yappc file importing @ghatana/workflow directly → should use @aep/api
        filename: "/repo/products/yappc/services/workflow.ts",
        code: `import { WorkflowEngine } from "@ghatana/workflow";`,
        errors: [{ messageId: "directPlatformImport" }],
      },
      {
        // yappc file importing @ghatana/vector directly → should use @data-cloud/api
        filename: "/repo/products/yappc/services/search.ts",
        code: `import { VectorStore } from "@ghatana/vector";`,
        errors: [{ messageId: "directPlatformImport" }],
      },
      {
        // yappc file importing @ghatana/event-cloud directly → should use @data-cloud/api
        filename: "/repo/products/yappc/services/events.ts",
        code: `import { EventCloud } from "@ghatana/event-cloud";`,
        errors: [{ messageId: "directPlatformImport" }],
      },
    ],
  }
);

// =============================================================================
// Summary
// =============================================================================
console.log(`\n─────────────────────────────────────────`);
console.log(`Suites: ${passed + failed}  ✅ passed: ${passed}  ❌ failed: ${failed}`);

if (failed > 0) {
  process.exit(1);
}
