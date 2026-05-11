/**
 * @fileoverview Test fixtures for no-yappc-direct-platform-imports lint rule
 * Task 5.F.4: Add lint rule enforcement and fixtures
 */

const rule = require("../ghatana-architecture-rules.js").rules[
  "no-yappc-direct-platform-imports"
];
const RuleTester = require("eslint").RuleTester;

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2020,
    sourceType: "module",
  },
});

/**
 * Valid imports - should pass
 */
const valid = [
  {
    code: 'import { DataCloudArtifactFacade } from "@data-cloud/api";',
    filename: "/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/artifact.ts",
  },
  {
    code: 'import { AepEventFacade } from "@aep/api";',
    filename: "/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/events.ts",
  },
  {
    code: 'import { Button } from "@ghatana/design-system";',
    filename: "/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/components/ui/Button.tsx",
  },
  {
    code: 'import { cn } from "@ghatana/platform-utils";',
    filename: "/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/utils/cn.ts",
  },
];

/**
 * Invalid imports - should fail
 */
const invalid = [
  {
    code: 'import { AgentCore } from "@ghatana/agent-core";',
    filename: "/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/agent.ts",
    errors: [
      {
        messageId: "directPlatformImport",
        data: {
          import: "@ghatana/agent-core",
          suggestion:
            "Use @data-cloud/api agent contract facade instead.",
        },
      },
    ],
  },
  {
    code: 'import { AIIntegration } from "@ghatana/ai-integration";',
    filename: "/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/ai.ts",
    errors: [
      {
        messageId: "directPlatformImport",
        data: {
          import: "@ghatana/ai-integration",
          suggestion:
            "Use @data-cloud/api AI integration facade instead.",
        },
      },
    ],
  },
  {
    code: 'import { WorkflowEngine } from "@ghatana/workflow";',
    filename: "/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/workflow.ts",
    errors: [
      {
        messageId: "directPlatformImport",
        data: {
          import: "@ghatana/workflow",
          suggestion:
            "Use @aep/api workflow contract facade instead.",
        },
      },
    ],
  },
  {
    code: 'import { VectorStore } from "@ghatana/vector";',
    filename: "/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/vector.ts",
    errors: [
      {
        messageId: "directPlatformImport",
        data: {
          import: "@ghatana/vector",
          suggestion:
            "Use @data-cloud/api vector search facade instead.",
        },
      },
    ],
  },
  {
    code: 'import { EventCloud } from "@ghatana/event-cloud";',
    filename: "/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/events.ts",
    errors: [
      {
        messageId: "directPlatformImport",
        data: {
          import: "@ghatana/event-cloud",
          suggestion:
            "Use @data-cloud/api event cloud facade instead.",
        },
      },
    ],
  },
  {
    code: 'export { AgentCore } from "@ghatana/agent-core";',
    filename: "/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/agent.ts",
    errors: [
      {
        messageId: "directPlatformImport",
      },
    ],
  },
  {
    code: 'export * from "@ghatana/workflow";',
    filename: "/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/workflow.ts",
    errors: [
      {
        messageId: "directPlatformImport",
      },
    ],
  },
];

/**
 * Non-YAPPC files - should not trigger the rule
 */
const nonYappcFiles = [
  {
    code: 'import { AgentCore } from "@ghatana/agent-core";',
    filename: "/Users/samujjwal/Development/ghatana/products/data-cloud/frontend/src/services/agent.ts",
  },
  {
    code: 'import { WorkflowEngine } from "@ghatana/workflow";',
    filename: "/Users/samujjwal/Development/ghatana/products/aep/frontend/src/services/workflow.ts",
  },
];

ruleTester.run("no-yappc-direct-platform-imports", rule, {
  valid: [...valid, ...nonYappcFiles],
  invalid,
});

console.log("✓ no-yappc-direct-platform-imports lint rule tests passed");
