import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";
import { createRequire } from "module";

const require = createRequire(import.meta.url);
const workspaceAliases = {
  // Note: @ghatana/canvas/flow MUST be listed BEFORE @ghatana/canvas (more specific first)
  "@ghatana/canvas/flow": path.resolve(
    __dirname,
    "src/__tests__/stubs/flow-canvas.tsx",
  ),
  "@ghatana/design-system": path.resolve(
    __dirname,
    "../../../platform/typescript/design-system/src/index.ts",
  ),
  "@ghatana/domain-components/privacy": path.resolve(
    __dirname,
    "../../../platform/typescript/domain-components/src/privacy/index.ts",
  ),
  "@ghatana/domain-components/security": path.resolve(
    __dirname,
    "../../../platform/typescript/domain-components/src/security/index.ts",
  ),
  "@ghatana/domain-components/selection": path.resolve(
    __dirname,
    "../../../platform/typescript/domain-components/src/selection/index.ts",
  ),
  "@ghatana/domain-components": path.resolve(
    __dirname,
    "../../../platform/typescript/domain-components/src/index.ts",
  ),
  "@ghatana/theme": path.resolve(
    __dirname,
    "../../../platform/typescript/theme/src/index.ts",
  ),
  "@ghatana/tokens": path.resolve(
    __dirname,
    "../../../platform/typescript/tokens/src/index.ts",
  ),
  "@ghatana/platform-utils": path.resolve(
    __dirname,
    "../../../platform/typescript/platform-utils/src/index.ts",
  ),
  "@ghatana/canvas": path.resolve(
    __dirname,
    "../../../platform/typescript/canvas",
  ),
  "@ghatana/realtime": path.resolve(
    __dirname,
    "../../../platform/typescript/realtime/src/index.ts",
  ),
  "@ghatana/privacy-ui": path.resolve(
    __dirname,
    "../../../platform/typescript/privacy-ui/src/index.ts",
  ),
  "@ghatana/security-ui": path.resolve(
    __dirname,
    "../../../platform/typescript/security-ui/src/index.ts",
  ),
  "@ghatana/selection-ui": path.resolve(
    __dirname,
    "../../../platform/typescript/selection-ui/src/index.ts",
  ),
  "@ghatana/audit-ui": path.resolve(
    __dirname,
    "../../../platform/typescript/audit-ui/src/index.ts",
  ),
  "@ghatana/nlp-ui": path.resolve(
    __dirname,
    "../../../platform/typescript/nlp-ui/src/index.ts",
  ),
  "@ghatana/voice-ui": path.resolve(
    __dirname,
    "../../../platform/typescript/voice-ui/src/index.ts",
  ),
  "@ghatana/wizard": path.resolve(
    __dirname,
    "../../../platform/typescript/wizard/src/index.ts",
  ),
};

/**
 * Vitest configuration for Data Cloud Platform.
 *
 * @doc.type config
 * @doc.purpose Test runner configuration
 */
// Resolve potential package subpath differences for `entities` package used by jsdom/parse5
let entitiesDecodePath: string | undefined;
let entitiesEscapePath: string | undefined;
try {
  entitiesDecodePath = require.resolve("entities/lib/decode.js");
  entitiesEscapePath = require.resolve("entities/lib/escape.js");
} catch (e) {
  // Fallback: leave undefined - Vitest resolver will use normal resolution and fail if unresolved
  // This fallback keeps the config robust if package layout changes.
}

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/__tests__/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}", "tests/**/*.test.{ts,tsx}"],
    testTimeout: 15000,
    hookTimeout: 15000,
    coverage: {
      provider: "v8",
      reporter: ["text", "json", "html"],
      exclude: [
        "node_modules/",
        "src/__tests__/",
        "**/*.stories.tsx",
        "**/*.d.ts",
      ],
      thresholds: {
        lines: 50,
        functions: 50,
        branches: 50,
        statements: 50,
      },
    },
  },
  resolve: {
    dedupe: ["react", "react-dom", "react/jsx-runtime"],
    alias: {
      ...workspaceAliases,
      react: path.resolve(__dirname, "node_modules/react"),
      "react-dom": path.resolve(__dirname, "node_modules/react-dom"),
      "react-router-dom": path.resolve(__dirname, "node_modules/react-router"),
      "@": path.resolve(__dirname, "./src"),
      // Short aliases matching tsconfig.json paths
      "@components": path.resolve(__dirname, "./src/components"),
      "@hooks": path.resolve(__dirname, "./src/hooks"),
      "@stores": path.resolve(__dirname, "./src/stores"),
      "@types": path.resolve(__dirname, "./src/types"),
      "@api": path.resolve(__dirname, "./src/api"),
      "@utils": path.resolve(__dirname, "./src/utils"),
      // Provide explicit aliases for `entities` subpaths used by jsdom/parse5 to avoid ESM exports mismatch
      ...(entitiesDecodePath ? { "entities/decode": entitiesDecodePath } : {}),
      ...(entitiesEscapePath ? { "entities/escape": entitiesEscapePath } : {}),
    },
  },
});
