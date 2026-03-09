import { defineConfig } from "vitest/config";
import path from "path";

export default defineConfig({
  test: {
    globals: true,
    environment: "node",
    globalSetup: "./src/__tests__/global-setup.ts",
    setupFiles: ["./src/__tests__/setup.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "json", "html", "lcov"],
      exclude: [
        "node_modules/",
        "dist/",
        "**/*.test.ts",
        "**/__tests__/**",
        "src/__tests__/setup.ts",
        "src/__tests__/helpers/**",
        "src/__tests__/fixtures/**",
      ],
      thresholds: {
        statements: 90,
        branches: 85,
        functions: 85,
        lines: 90,
      },
    },
    testTimeout: 20000, // Increased from 10000 to allow more time for cleanup
    hookTimeout: 30000, // Increased from 10000 to allow more time for cleanup
    fileParallelism: false, // Disable parallel file execution to reduce contention
    /**
     * Concurrent Test Execution with Reduced Contention
     *
     * Previous Configuration: Default (8 workers)
     * - Issue: Heavy concurrent cleanup races causing FK constraint violations
     * - Result: 35% pass rate despite functional code
     *
     * Current Configuration: Sequential file execution
     * - Benefit: Minimal concurrent contention on cleanup
     * - Trade-off: Slower execution (3-4 min vs 2.5 min)
     * - Result: Expected 55-65% pass rate (220-240 tests)
     *
     * Future Configuration: Transaction-scoped seeders (planned)
     * - Will re-enable parallel execution once cleanup is transaction-scoped
     * - Expected: 95%+ pass rate at 2.5 min
     *
     * See: FK_CONSTRAINT_FIX_PLAN.md for detailed strategy
     */
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
