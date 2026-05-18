import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const TEST_DIR = dirname(fileURLToPath(import.meta.url));
const CONTRACTS_SRC = join(TEST_DIR, "..", "..");

function sourceFiles(directory: string): readonly string[] {
  return readdirSync(directory).flatMap((entry) => {
    const absolute = join(directory, entry);
    if (statSync(absolute).isDirectory()) {
      return sourceFiles(absolute);
    }
    return absolute.endsWith(".ts") ? [absolute] : [];
  });
}

describe("kernel product contract import boundaries", () => {
  it("does not import Data Cloud internals from public Kernel contracts", () => {
    const offenders = sourceFiles(CONTRACTS_SRC).filter((file) => {
      const contents = readFileSync(file, "utf8");
      return (
        /from\s+["'](?:\.\.\/)*products\/data-cloud\//.test(contents) ||
        /from\s+["']products\/data-cloud\//.test(contents) ||
        /import\s*\(\s*["']products\/data-cloud\//.test(contents)
      );
    });

    expect(offenders).toEqual([]);
  });
});
