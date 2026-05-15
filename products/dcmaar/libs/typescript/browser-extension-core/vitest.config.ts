import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  resolve: {
    alias: {
      "@dcmaar/connectors": resolve(__dirname, "../connectors/src/index.ts"),
      "@dcmaar/plugin-abstractions": resolve(__dirname, "../plugin-abstractions/src/index.ts"),
      "@dcmaar/types": resolve(__dirname, "../types/src/index.ts"),
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./__tests__/setup.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "json", "html"],
      exclude: ["node_modules/**", "__tests__/**", "dist/**", "*.config.*"],
    },
  },
});
