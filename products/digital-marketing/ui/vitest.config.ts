import { defineConfig } from "vitest/config";
import { resolve } from "path";

const workspaceAliases = {
  "@ghatana/design-system": resolve(
    __dirname,
    "../../../platform/typescript/design-system/src/index.ts",
  ),
  "@ghatana/platform-utils": resolve(
    __dirname,
    "../../../platform/typescript/platform-utils/src/index.ts",
  ),
  "@ghatana/product-shell": resolve(
    __dirname,
    "../../../platform/typescript/product-shell/src/index.ts",
  ),
  "@ghatana/theme": resolve(
    __dirname,
    "../../../platform/typescript/theme/src/index.ts",
  ),
  "@ghatana/tokens": resolve(
    __dirname,
    "../../../platform/typescript/tokens/src/index.ts",
  ),
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
    testTimeout: 15_000,
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["./src/test-setup.ts"],
    alias: workspaceAliases,
    coverage: {
      provider: "v8",
      reporter: ["text", "json", "html", "lcov"],
      exclude: [
        "src/test-setup.ts",
        "src/**/*.d.ts",
        "src/**/__tests__/**",
        "src/**/*.test.{ts,tsx}",
        "src/**/*.stories.{ts,tsx}",
        "src/main.tsx",
      ],
      thresholds: {
        lines: 80,
        functions: 80,
        branches: 80,
        statements: 80,
      },
    },
  },
});
