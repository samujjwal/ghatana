import { spawnSync } from "child_process";
import { readFileSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

const node = process.execPath;
const script = path.resolve(process.cwd(), "scripts/sbom-summary.js");

function run(args: string[]) {
  const r = spawnSync(node, [script, ...args], { encoding: "utf8" });
  return r;
}

describe("sbom-summary script", () => {
  it("prints a text summary for small SBOM", () => {
    const sbom = path.join("scripts", "test", "fixtures", "sbom-small.json");
    const r = run([sbom, "5", "--format=text"]);
    expect(r.status).toBe(0);
    expect(r.stdout).toContain("Component count: 3");
  });

  it("detects absolute increase and warns", () => {
    const sbom = path.join("scripts", "test", "fixtures", "sbom-small.json");
    const prev = path.join("scripts", "test", "fixtures", "sbom-prev.json");
    const r = run([
      sbom,
      "5",
      "--format=text",
      `--previous=${prev}`,
      "--increase-threshold=1",
    ]);
    // Should exit 0 but print a warning annotation
    expect(r.status).toBe(0);
    expect(r.stdout).toMatch(
      /Previous component count: 1; current: 3; increase: 2/
    );
    expect(r.stdout).toMatch(
      /::warning::SBOM component count increased by 2 which exceeds increase-threshold 1/
    );
  });

  it("fails when percent increase exceeds threshold", () => {
    const sbom = path.join("scripts", "test", "fixtures", "sbom-small.json");
    const prev = path.join("scripts", "test", "fixtures", "sbom-prev.json");
    const r = run([
      sbom,
      "5",
      "--format=text",
      `--previous=${prev}`,
      "--increase-percent-threshold=50",
      "--fail-if-increase-percent-above",
    ]);
    // Previous count 1 -> new 3 is 200% increase so should fail
    expect(r.status).not.toBe(0);
    expect(r.stderr).toMatch(/SBOM component count increased by/);
  });
});
