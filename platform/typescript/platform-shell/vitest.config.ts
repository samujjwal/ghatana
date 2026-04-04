import { defineConfig } from "vitest/config";

export default defineConfig({
  esbuild: {
    // Use ESNext target; do not load tsconfig (path resolution issue with extends)
    target: "es2022",
  },
  test: {
    environment: "node",
    globals: false,
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    typecheck: {
      enabled: false,
    },
  },
});
