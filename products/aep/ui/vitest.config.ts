import { defineConfig } from "vitest/config";
import { resolve } from "path";

const workspaceAliases = {
  "@ghatana/design-system": resolve(
    __dirname,
    "../../../platform/typescript/design-system/src/index.ts",
  ),
  "@ghatana/canvas-core": resolve(
    __dirname,
    "../../../platform/typescript/canvas/src/index.ts",
  ),
  "@ghatana/theme": resolve(
    __dirname,
    "../../../platform/typescript/theme/src/index.ts",
  ),
  "@ghatana/tokens": resolve(
    __dirname,
    "../../../platform/typescript/tokens/src/index.ts",
  ),
  "@ghatana/platform-utils": resolve(
    __dirname,
    "../../../platform/typescript/foundation/platform-utils/src/index.ts",
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
