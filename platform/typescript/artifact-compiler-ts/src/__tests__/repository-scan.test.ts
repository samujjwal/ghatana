import { describe, expect, it } from "vitest";
import { ScanResultSchema } from "@ghatana/artifact-contracts";
import { scanRepositorySources } from "../scan/repository-scan.js";

describe("scanRepositorySources", () => {
  it("builds a contract-valid scan result and model for multiple TSX files", () => {
    const scan = scanRepositorySources(
      [
        {
          relativePath: "src/components/Button.tsx",
          content: "export function Button() { return <button>Save</button>; }",
        },
        {
          relativePath: "src/pages/Home.tsx",
          content: "import { Button } from '../components/Button'; export function Home() { return <main><Button /></main>; }",
        },
      ],
      {
        scanJobId: "scan-1",
        modelId: "model-1",
        label: "Repo Fixture",
      },
    );

    expect(ScanResultSchema.safeParse(scan.result).success).toBe(true);
    expect(scan.result.files).toHaveLength(2);
    expect(scan.result.files.every((file) => file.parsed)).toBe(true);
    expect(Object.keys(scan.model.nodes)).toEqual([
      "src/components/Button.tsx",
      "src/pages/Home.tsx",
    ]);
    expect(scan.model.edges).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          fromId: "src/pages/Home.tsx",
          toId: "src/components/Button.tsx",
          kind: "import",
        }),
      ]),
    );
  });

  it("keeps parse failures in the scan inventory and decompiles valid files", () => {
    const scan = scanRepositorySources(
      [
        {
          relativePath: "src/Valid.tsx",
          content: "export function Valid() { return <div />; }",
        },
        {
          relativePath: "src/Broken.tsx",
          content: "export function Broken() { return <div>; }",
        },
        {
          relativePath: "README.md",
          content: "# ignored",
        },
      ],
      {
        scanJobId: "scan-2",
        modelId: "model-2",
        label: "Partial Repo",
      },
    );

    expect(ScanResultSchema.safeParse(scan.result).success).toBe(true);
    expect(scan.result.files).toHaveLength(3);
    expect(scan.result.files.filter((file) => file.parsed)).toHaveLength(1);
    expect(scan.result.files.find((file) => file.sourceFile.relativePath === "src/Broken.tsx")?.parseError).toBeDefined();
    expect(scan.result.files.find((file) => file.sourceFile.relativePath === "README.md")?.parseError).toContain("Unsupported");
    expect(Object.keys(scan.model.nodes)).toEqual(["src/Valid.tsx"]);
  });

  it("detects residual islands across parsed repository files", () => {
    const scan = scanRepositorySources(
      [
        {
          relativePath: "src/Dynamic.tsx",
          content: "export function Dynamic() { eval('1 + 1'); return <div />; }",
        },
      ],
      {
        scanJobId: "scan-3",
        modelId: "model-3",
        label: "Dynamic Repo",
      },
    );

    expect(scan.result.residuals.blockingCount).toBeGreaterThan(0);
    expect(scan.result.residuals.islands[0]?.kind).toBe("runtime-dynamic");
  });
});
