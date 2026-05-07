import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, it } from "vitest";

const engineRoot = join(import.meta.dirname);
const productionAllowlist = new Set([
  "legacy-migration.ts",
]);

function walkFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = join(directory, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "auto") {
        return [];
      }
      return walkFiles(fullPath);
    }

    return entry.isFile() ? [fullPath] : [];
  });
}

describe("production simulation import boundaries", () => {
  it("keeps retired and compatibility simulation paths out of the production engine barrel", () => {
    const source = readFileSync(join(engineRoot, "index.ts"), "utf8");

    expect(source).not.toContain("auto-retired");
    expect(source).not.toContain("preset-compatibility");
    expect(source).not.toContain("./auto");
  });

  it("only exposes retired auto runtime through the migration barrel or tests", () => {
    const violations = walkFiles(engineRoot)
      .filter((file) => statSync(file).isFile())
      .filter((file) => file.endsWith(".ts"))
      .filter((file) => !file.endsWith(".test.ts"))
      .filter((file) => !productionAllowlist.has(relative(engineRoot, file).replaceAll("\\", "/")))
      .filter((file) => {
        const source = readFileSync(file, "utf8");
        return source.includes("auto-retired") || source.includes("preset-compatibility");
      })
      .map((file) => relative(engineRoot, file).replaceAll("\\", "/"));

    expect(violations).toEqual([]);
  });
});
