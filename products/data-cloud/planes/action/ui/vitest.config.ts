import { defineConfig } from "vitest/config";
import { resolve } from "path";

const workspaceAliases = {
  "@ghatana/design-system": resolve(
    __dirname,
    "../../../../platform/typescript/design-system/src/index.ts",
  ),
  "@ghatana/canvas": resolve(
    __dirname,
    "../../../../platform/typescript/canvas/src/index.ts",
  ),
  "@ghatana/canvas/flow": resolve(
    __dirname,
    "../../../../platform/typescript/canvas/src/flow/index.ts",
  ),
  "@ghatana/theme": resolve(
    __dirname,
    "../../../../platform/typescript/theme/src/index.ts",
  ),
  "@ghatana/tokens": resolve(
    __dirname,
    "../../../../platform/typescript/tokens/src/index.ts",
  ),
  "@ghatana/platform-utils": resolve(
    __dirname,
    "../../../../platform/typescript/platform-utils/src/index.ts"
  ),
  "@ghatana/domain-components/privacy": resolve(
    __dirname,
    "../../../../platform/typescript/domain-components/src/privacy/index.ts",
  ),
  "@ghatana/domain-components/security": resolve(
    __dirname,
    "../../../../platform/typescript/domain-components/src/security/index.ts",
  ),
  "@ghatana/domain-components/voice": resolve(
    __dirname,
    "../../../../platform/typescript/domain-components/src/voice/index.ts",
  ),
  "@ghatana/domain-components/nlp": resolve(
    __dirname,
    "../../../../platform/typescript/domain-components/src/nlp/index.ts",
  ),
  "@ghatana/domain-components/selection": resolve(
    __dirname,
    "../../../../platform/typescript/domain-components/src/selection/index.ts",
  ),
  "@ghatana/domain-components": resolve(
    __dirname,
    "../../../../platform/typescript/domain-components/src/index.ts",
  ),
  "@ghatana/audit": resolve(
    __dirname,
    "../../../../platform/typescript/audit/src/index.ts",
  ),
  "@ghatana/realtime": resolve(
    __dirname,
    "../../../../platform/typescript/realtime/src/index.ts",
  ),
  "@ghatana/platform-events": resolve(
    __dirname,
    "../../../../platform/typescript/platform-events/src/index.ts",
  ),
  "@ghatana/security-ui": resolve(
    __dirname,
    "../../../../platform/typescript/security-ui/src/index.ts",
  ),
  "@ghatana/voice-ui": resolve(
    __dirname,
    "../../../../platform/typescript/voice-ui/src/index.ts",
  ),
  "@ghatana/nlp-ui": resolve(
    __dirname,
    "../../../../platform/typescript/nlp-ui/src/index.ts",
  ),
  "@ghatana/audit-ui": resolve(
    __dirname,
    "../../../../platform/typescript/audit-ui/src/index.ts",
  ),
  "@ghatana/selection-ui": resolve(
    __dirname,
    "../../../../platform/typescript/selection-ui/src/index.ts",
  ),
  "@audio-video/ui": resolve(
    __dirname,
    "../../../../products/audio-video/libs/audio-video-ui/src/index.tsx",
  ),
  clsx: resolve(__dirname, "node_modules/clsx"),
  "tailwind-merge": resolve(__dirname, "node_modules/tailwind-merge"),
};

export default defineConfig({
  resolve: {
    alias: {
      ...workspaceAliases,
      "@": resolve(__dirname, "src"),
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["./src/test-setup.ts"],
    alias: workspaceAliases,
  },
});

