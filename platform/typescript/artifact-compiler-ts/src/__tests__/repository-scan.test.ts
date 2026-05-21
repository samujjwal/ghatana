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

  it('records resolved and unresolved import counts in repository graph metadata', () => {
    const scan = scanRepositorySources(
      [
        {
          relativePath: 'src/App.tsx',
          content: `import { Button } from './components/Button'; import { Missing } from './missing'; export function App() { return <Button />; }`,
        },
        {
          relativePath: 'src/components/Button.tsx',
          content: 'export function Button() { return <button>Ok</button>; }',
        },
      ],
      {
        scanJobId: 'scan-4',
        modelId: 'model-4',
        label: 'Graph Repo',
      },
    );

    expect(scan.model.metadata.repositoryGraph).toEqual({
      resolvedImportCount: 1,
      unresolvedImportCount: 1,
      routeDeclarationCount: 0,
      componentUsageCount: 1,
      apiCallCount: 0,
      designTokenReferenceCount: 0,
      routeConfigObjectCount: 0,
      routeConfigMaxDepth: 0,
      workspacePackageCount: 0,
      workspaceDependencyCount: 0,
      workspaceScriptCount: 0,
    });
    expect(scan.model.edges).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          fromId: 'src/App.tsx',
          toId: 'src/components/Button.tsx',
          kind: 'import',
        }),
      ]),
    );
  });

  it('captures route, api, and design-token intelligence counters', () => {
    const scan = scanRepositorySources(
      [
        {
          relativePath: 'src/App.tsx',
          content: `
            export function App() {
              fetch('/api/data');
              const style = 'var(--color-primary-token)';
              return (
                <Routes>
                  <Route path="/" element={<HomePage />} />
                </Routes>
              );
            }
          `,
        },
      ],
      {
        scanJobId: 'scan-5',
        modelId: 'model-5',
        label: 'Intelligence Repo',
      },
    );

    expect(scan.model.metadata.repositoryGraph).toEqual({
      resolvedImportCount: 0,
      unresolvedImportCount: 0,
      routeDeclarationCount: 1,
      componentUsageCount: 3,
      apiCallCount: 1,
      designTokenReferenceCount: 1,
      routeConfigObjectCount: 0,
      routeConfigMaxDepth: 0,
      workspacePackageCount: 0,
      workspaceDependencyCount: 0,
      workspaceScriptCount: 0,
    });
  });

  it('captures package-manifest and route-config graph intelligence counters', () => {
    const scan = scanRepositorySources(
      [
        {
          relativePath: 'package.json',
          content: JSON.stringify({
            name: 'fixture',
            scripts: {
              build: 'tsc -p tsconfig.json',
              test: 'vitest run',
            },
            dependencies: {
              react: '^19.0.0',
            },
            devDependencies: {
              vitest: '^3.0.0',
            },
          }),
        },
        {
          relativePath: 'src/router.tsx',
          content: `
            import { createBrowserRouter } from 'react-router-dom';
            export const routesConfig = [{
              path: '/',
              children: [{ path: 'settings', children: [{ path: 'advanced' }] }],
            }];
            export const router = createBrowserRouter(routesConfig);
          `,
        },
      ],
      {
        scanJobId: 'scan-6',
        modelId: 'model-6',
        label: 'Manifest + Route Graph Repo',
      },
    );

    expect(scan.model.metadata.repositoryGraph).toEqual({
      resolvedImportCount: 0,
      unresolvedImportCount: 1,
      routeDeclarationCount: 0,
      componentUsageCount: 0,
      apiCallCount: 0,
      designTokenReferenceCount: 0,
      routeConfigObjectCount: 1,
      routeConfigMaxDepth: 6,
      workspacePackageCount: 1,
      workspaceDependencyCount: 2,
      workspaceScriptCount: 2,
    });
  });
});
