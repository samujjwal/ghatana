/// <reference types="vitest" />
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    preserveSymlinks: true,
    alias: {
      "@ghatana/platform-utils": fileURLToPath(
        new URL("../platform-utils/src/index.ts", import.meta.url),
      ),
      "@ghatana/tokens": fileURLToPath(
        new URL("../tokens/src/index.ts", import.meta.url),
      ),
      "@ghatana/theme": fileURLToPath(
        new URL("../theme/src/index.ts", import.meta.url),
      ),
      "react-router": fileURLToPath(
        new URL("./node_modules/react-router/dist/development/index.mjs", import.meta.url),
      ),
      "react-router-dom": fileURLToPath(
        new URL("./node_modules/react-router/dist/development/index.mjs", import.meta.url),
      ),
      clsx: new URL("./node_modules/clsx", import.meta.url).pathname,
      "tailwind-merge": new URL(
        "./node_modules/tailwind-merge",
        import.meta.url,
      ).pathname,
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    dangerouslyIgnoreUnhandledErrors: true,
    coverage: {
      provider: "v8",
      reporter: ["text", "json", "html", "lcov"],
      include: ["src/**/*.ts", "src/**/*.tsx"],
      exclude: [
        "src/**/*.test.ts",
        "src/**/*.test.tsx",
        "src/**/*.stories.tsx",
        "src/**/*.d.ts",
        "src/test/**",
      ],
      thresholds: {
        lines: 70,
        functions: 70,
        branches: 65,
        statements: 70,
      },
    },
  },
});
