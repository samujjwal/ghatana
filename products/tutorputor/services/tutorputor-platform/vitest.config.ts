import { defineConfig } from "vitest/config";
import path from "path";

export default defineConfig({
  resolve: {
    alias: [
      {
        find: "@ghatana/tutorputor-db",
        replacement: path.resolve(
          __dirname,
          "src/__tests__/__mocks__/tutorputor-db.ts",
        ),
      },
      {
        find: "@ghatana/design-system",
        replacement: path.resolve(
          __dirname,
          "../../../../platform/typescript/design-system/src/index.ts",
        ),
      },
      {
        find: "@ghatana/theme",
        replacement: path.resolve(
          __dirname,
          "../../../../platform/typescript/theme/src/index.ts",
        ),
      },
      {
        find: "@ghatana/tokens",
        replacement: path.resolve(
          __dirname,
          "../../../../platform/typescript/tokens/src/index.ts",
        ),
      },
      {
        find: "@ghatana/platform-utils",
        replacement: path.resolve(
          __dirname,
          "../../../../platform/typescript/platform-utils/src/index.ts",
        ),
      },
      {
        find: /^@tutorputor\/core\/(.+)$/,
        replacement: path.resolve(
          __dirname,
          "../../libs/tutorputor-core/src/$1",
        ),
      },
      {
        find: /^@tutorputor\/simulation\/(.+)$/,
        replacement: path.resolve(
          __dirname,
          "../../libs/tutorputor-simulation/src/$1",
        ),
      },
      {
        find: /^@tutorputor\/ui\/(.+)$/,
        replacement: path.resolve(__dirname, "../../libs/tutorputor-ui/src/$1"),
      },
      {
        find: /^@tutorputor\/contracts\/(.+)$/,
        replacement: path.resolve(__dirname, "../../contracts/$1"),
      },
      {
        find: "@tutorputor/contracts",
        replacement: path.resolve(__dirname, "../../contracts/v1/index.ts"),
      },
      {
        find: "@tutorputor/core",
        replacement: path.resolve(
          __dirname,
          "../../libs/tutorputor-core/src/index.ts",
        ),
      },
      {
        find: "@tutorputor/simulation",
        replacement: path.resolve(
          __dirname,
          "../../libs/tutorputor-simulation/src/index.ts",
        ),
      },
      {
        find: "@tutorputor/ui",
        replacement: path.resolve(
          __dirname,
          "../../libs/tutorputor-ui/src/index.ts",
        ),
      },
    ],
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
