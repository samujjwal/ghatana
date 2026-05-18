import { describe, expect, it } from "vitest";
import packageJson from "../../package.json";

const sourceModules = import.meta.glob("../**/*.{ts,tsx}", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

describe("@ghatana/ghatana-studio package metadata", () => {
  it("describes Studio as the unified product development experience", () => {
    expect(packageJson.description).toBe(
      "Unified Ghatana Studio experience for ideation, blueprinting, development, lifecycle execution, deployment, health, learning, and evolution.",
    );
  });

  it("declares only directly imported Studio platform dependencies", () => {
    expect(packageJson.dependencies).toMatchObject({
      "@ghatana/api": "workspace:*",
      "@ghatana/kernel-artifacts": "workspace:*",
      "@ghatana/kernel-deployment": "workspace:*",
      "@ghatana/kernel-product-contracts": "workspace:*",
      "@ghatana/kernel-release": "workspace:*",
    });
    expect(packageJson.dependencies).not.toHaveProperty("zustand");
  });

  it("keeps local typecheck convention and adds RTL-level accessibility checks", () => {
    expect(packageJson.scripts).toMatchObject({
      "type-check": "tsc --noEmit",
      "test:a11y": "vitest run src/__tests__/navigation.test.tsx",
    });
  });

  it("does not import products/* internals from Studio source", () => {
    const offenders = Object.entries(sourceModules).flatMap(
      ([file, contents]) => {
        return /from\s+['"](?:\.\.\/)*products\//.test(contents) ||
          /from\s+['"]products\//.test(contents) ||
          /import\s*\(\s*['"]products\//.test(contents)
          ? [file]
          : [];
      },
    );

    expect(offenders).toEqual([]);
  });
});
