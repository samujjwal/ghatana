import { defineConfig } from "vitest/config";
import path from "path";

export default defineConfig({
  resolve: {
    alias: {
      "@ghatana/tutorputor-db": path.resolve(
        __dirname,
        "src/__tests__/__mocks__/tutorputor-db.ts",
      ),
    },
  },
  test: {
    globals: true,
    environment: "node",
    include: ["src/**/*.test.ts", "tests/**/*.test.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "lcov"],
      include: ["src/**/*.ts"],
      exclude: [
        "src/**/*.test.ts",
        "src/**/*.spec.ts",
        "src/**/__tests__/**",
        "src/**/types.ts",
        "src/**/index.ts", // Re-exports
        "src/**/*.d.ts",
      ],
      thresholds: {
        statements: 90,
        branches: 85,
        functions: 90,
        lines: 90,
      },
    },
    testTimeout: 10000,
    hookTimeout: 10000,
    setupFiles: ["./vitest.setup.ts"],
  },
});
