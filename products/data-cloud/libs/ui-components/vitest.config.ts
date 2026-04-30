import { defineConfig } from "vitest/config";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  test: {
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
  },
  resolve: {
    alias: {
      "@ghatana/design-system": fileURLToPath(new URL("./src/test/mocks/design-system.ts", import.meta.url)),
      "@ghatana/platform-utils": fileURLToPath(new URL("./src/test/mocks/platform-utils.ts", import.meta.url)),
    },
  },
});
