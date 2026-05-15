import { defineConfig } from "vitest/config";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  test: {
    environment: "jsdom",
    globals: false,
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    setupFiles: ["src/tests/setup.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "json", "html", "lcov"],
      reportsDirectory: "./coverage",
      include: ["src/**/*.ts", "src/**/*.tsx"],
      exclude: [
        "src/**/*.test.ts",
        "src/**/*.test.tsx",
        "src/**/*.d.ts",
        "src/tests/**",
      ],
      thresholds: {
        lines: 70,
        functions: 70,
        branches: 65,
        statements: 70,
      },
    },
  },
  resolve: {
    alias: {
      "@testing-library/react": resolve(
        __dirname,
        "../../../node_modules/.pnpm/@testing-library+react@16.3.2_@testing-library+dom@10.4.1_@types+react-dom@19.2.3_@type_893f466751a7d66081fd06e9edb9241a/node_modules/@testing-library/react",
      ),
      "@testing-library/jest-dom": resolve(
        __dirname,
        "../../../node_modules/.pnpm/@testing-library+jest-dom@6.9.1/node_modules/@testing-library/jest-dom",
      ),
    },
  },
});
