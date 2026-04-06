/// <reference types="vitest" />
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    preserveSymlinks: true,
    alias: {
      "@ghatana/platform-utils": fileURLToPath(
        new URL("../foundation/platform-utils/src/index.ts", import.meta.url),
      ),
      "@ghatana/tokens": fileURLToPath(
        new URL("../tokens/src/index.ts", import.meta.url),
      ),
      "@ghatana/theme": fileURLToPath(
        new URL("../theme/src/index.ts", import.meta.url),
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
    coverage: {
      provider: "v8",
      reporter: ["text", "json", "html"],
      include: ["src/**/*.ts", "src/**/*.tsx"],
      exclude: [
        "src/**/*.test.ts",
        "src/**/*.test.tsx",
        "src/**/*.stories.tsx",
        "src/**/*.d.ts",
        "src/test/**",
      ],
    },
  },
});
