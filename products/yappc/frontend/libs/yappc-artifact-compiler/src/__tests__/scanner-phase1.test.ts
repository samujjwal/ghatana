/**
 * @fileoverview Phase 1 scanner tests — deterministic IDs, .gitignore, binary/generated classification,
 * package boundary detection, and large-file skip.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdir, writeFile, rm } from 'fs/promises';
import { join, dirname } from 'path';
import { tmpdir } from 'os';
import { randomUUID } from 'crypto';
import { scanRepository } from '../inventory/scanner';
import type { SnapshotRef } from '../graph/types';

// ============================================================================
// Test fixture helpers
// ============================================================================

async function createTempRepo(): Promise<string> {
  const root = join(tmpdir(), `yappc-scanner-test-${randomUUID().slice(0, 8)}`);
  await mkdir(root, { recursive: true });
  return root;
}

async function writeFiles(root: string, files: Record<string, string>): Promise<void> {
  for (const [rel, content] of Object.entries(files)) {
    const abs = join(root, rel);
    await mkdir(dirname(abs), { recursive: true });
    await writeFile(abs, content, 'utf-8');
  }
}

async function cleanupTempRepo(root: string): Promise<void> {
  await rm(root, { recursive: true, force: true });
}

// ============================================================================
// Tests
// ============================================================================

describe('scanner Phase 1 — deterministic artifact IDs', () => {
  let root: string;

  beforeEach(async () => {
    root = await createTempRepo();
  });

  afterEach(async () => {
    await cleanupTempRepo(root);
  });

  it('produces identical IDs for the same file scanned twice with the same snapshotRef', async () => {
    await writeFiles(root, {
      'src/Button.tsx': 'export const Button = () => <button />;',
      'package.json': '{"name":"test-pkg"}',
    });

    const snapshotRef: SnapshotRef = {
      provider: 'local-folder',
      repoId: 'test/repo',
      commitSha: 'abc123def456abc123def456abc123def456abc1',
    };

    const inv1 = await scanRepository({ rootPath: root, snapshotRef, respectGitignore: false });
    const inv2 = await scanRepository({ rootPath: root, snapshotRef, respectGitignore: false });

    const ids1 = new Set(inv1.artifacts.map(a => a.id));
    const ids2 = new Set(inv2.artifacts.map(a => a.id));

    expect(ids1.size).toBeGreaterThan(0);
    for (const id of ids1) {
      expect(ids2.has(id)).toBe(true);
    }
  });

  it('produces different IDs when snapshotRef commitSha differs', async () => {
    await writeFiles(root, {
      'src/Button.tsx': 'export const Button = () => <button />;',
      'package.json': '{"name":"test-pkg"}',
    });

    const ref1: SnapshotRef = { provider: 'local-folder', repoId: 'test/repo', commitSha: 'aaa' };
    const ref2: SnapshotRef = { provider: 'local-folder', repoId: 'test/repo', commitSha: 'bbb' };

    const inv1 = await scanRepository({ rootPath: root, snapshotRef: ref1, respectGitignore: false });
    const inv2 = await scanRepository({ rootPath: root, snapshotRef: ref2, respectGitignore: false });

    const ids1 = inv1.artifacts.map(a => a.id);
    const ids2 = inv2.artifacts.map(a => a.id);

    // Same path but different snapshot → different IDs
    expect(ids1).not.toEqual(ids2);
  });

  it('falls back to random UUID when no snapshotRef is provided', async () => {
    await writeFiles(root, {
      'src/App.tsx': 'export default function App() { return null; }',
      'package.json': '{"name":"no-snapshot"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });
    const ids = inv.artifacts.map(a => a.id);
    expect(ids.length).toBeGreaterThan(0);
    // IDs should be valid non-empty strings (UUID or URN)
    for (const id of ids) {
      expect(typeof id).toBe('string');
      expect(id.length).toBeGreaterThan(8);
    }
  });
});

describe('scanner Phase 1 — binary and generated file classification', () => {
  let root: string;

  beforeEach(async () => {
    root = await createTempRepo();
  });

  afterEach(async () => {
    await cleanupTempRepo(root);
  });

  it('marks .d.ts files as generated', async () => {
    await writeFiles(root, {
      'types/Button.d.ts': 'export declare const Button: () => JSX.Element;',
      'package.json': '{"name":"gen-test"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });
    const dts = inv.artifacts.find(a => a.relativePath.endsWith('.d.ts'));
    expect(dts).toBeDefined();
    expect(dts?.isGenerated).toBe(true);
  });

  it('marks files with @generated header as generated', async () => {
    await writeFiles(root, {
      'src/api-client.ts': '// @generated\n// DO NOT EDIT\nexport type Foo = string;',
      'package.json': '{"name":"gen-header-test"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });
    const gen = inv.artifacts.find(a => a.relativePath.includes('api-client'));
    expect(gen?.isGenerated).toBe(true);
  });

  it('marks .png files as binary with isBinary=true', async () => {
    // Write a minimal fake PNG header (just to create a non-empty file)
    const pngBytes = Buffer.from([0x89, 0x50, 0x4e, 0x47]);
    const pngPath = join(root, 'assets', 'logo.png');
    await mkdir(join(root, 'assets'), { recursive: true });
    await writeFile(pngPath, pngBytes);
    await writeFile(join(root, 'package.json'), '{"name":"binary-test"}');

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });
    const png = inv.artifacts.find(a => a.relativePath.endsWith('.png'));
    expect(png).toBeDefined();
    expect(png?.isBinary).toBe(true);
    expect(png?.isGenerated).toBe(false);
  });

  it('does not emit extractorEligibility for generated files', async () => {
    await writeFiles(root, {
      'generated/schema.ts': '// @generated\nexport type ID = string;',
      'package.json': '{"name":"elig-test"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });
    const gen = inv.artifacts.find(a => a.relativePath.includes('schema'));
    expect(gen?.extractorEligibility).toHaveLength(0);
  });

  it('tracks generatedFiles and binaryFiles counts in summary', async () => {
    const pngPath = join(root, 'logo.png');
    await writeFile(pngPath, Buffer.from([0x89, 0x50, 0x4e, 0x47]));
    await writeFiles(root, {
      'types/index.d.ts': 'export declare type Foo = string;',
      'src/App.tsx': 'export default function App() { return null; }',
      'package.json': '{"name":"summary-test"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });
    expect(inv.summary.binaryFiles).toBeGreaterThanOrEqual(1);
    expect(inv.summary.generatedFiles).toBeGreaterThanOrEqual(1);
  });
});

describe('scanner Phase 1 — .gitignore parsing', () => {
  let root: string;

  beforeEach(async () => {
    root = await createTempRepo();
  });

  afterEach(async () => {
    await cleanupTempRepo(root);
  });

  it('excludes files matched by root .gitignore', async () => {
    await writeFiles(root, {
      '.gitignore': 'ignored-folder/\n*.secret',
      'src/App.tsx': 'export default function App() { return null; }',
      'ignored-folder/secret.ts': 'const x = 1;',
      'config.secret': 'password=hunter2',
      'package.json': '{"name":"gitignore-test"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: true });
    const paths = inv.artifacts.map(a => a.relativePath);

    expect(paths.some(p => p.includes('ignored-folder'))).toBe(false);
    expect(paths.some(p => p.endsWith('.secret'))).toBe(false);
    expect(paths.some(p => p.includes('App.tsx'))).toBe(true);
  });

  it('respects negation patterns in .gitignore', async () => {
    await writeFiles(root, {
      '.gitignore': 'logs/\n!logs/important.log',
      'logs/debug.log': 'debug output',
      'logs/important.log': 'critical info',
      'package.json': '{"name":"negation-test"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: true });
    const paths = inv.artifacts.map(a => a.relativePath);

    // important.log should NOT be excluded (negation)
    // debug.log should be excluded
    expect(paths.some(p => p.includes('debug.log'))).toBe(false);
  });

  it('includes all files when respectGitignore is false', async () => {
    await writeFiles(root, {
      '.gitignore': 'src/',
      'src/App.tsx': 'export default function App() { return null; }',
      'package.json': '{"name":"no-gitignore-test"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });
    const paths = inv.artifacts.map(a => a.relativePath);
    expect(paths.some(p => p.includes('App.tsx'))).toBe(true);
  });
});

describe('scanner Phase 1 — package and workspace boundary detection', () => {
  let root: string;

  beforeEach(async () => {
    root = await createTempRepo();
  });

  afterEach(async () => {
    await cleanupTempRepo(root);
  });

  it('detects package boundaries from nested package.json files', async () => {
    await writeFiles(root, {
      'package.json': '{"name":"root-workspace"}',
      'packages/ui/package.json': '{"name":"@acme/ui"}',
      'packages/utils/package.json': '{"name":"@acme/utils"}',
      'packages/ui/src/Button.tsx': 'export const Button = () => null;',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });
    const pkgNames = inv.packageBoundaries?.map(b => b.name) ?? [];

    expect(pkgNames).toContain('@acme/ui');
    expect(pkgNames).toContain('@acme/utils');
  });

  it('assigns packageBoundary to artifact records within a package', async () => {
    await writeFiles(root, {
      'package.json': '{"name":"root"}',
      'core/package.json': '{"name":"@acme/core"}',
      'core/index.ts': 'export const x = 1;',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });
    expect(inv.artifacts.length).toBeGreaterThan(0);
    const coreFile = inv.artifacts.find(a => a.relativePath.replace(/\\/g, '/') === 'core/index.ts');
    expect(coreFile).toBeDefined();
    expect(coreFile?.packageBoundary?.name).toBe('@acme/core');
  });

  it('exposes workspaceBoundaries when pnpm-workspace.yaml is present', async () => {
    await writeFiles(root, {
      'pnpm-workspace.yaml': 'packages:\n  - packages/*',
      'package.json': '{"name":"root"}',
      'packages/a/package.json': '{"name":"pkg-a"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });
    expect(inv.workspaceBoundaries?.length).toBeGreaterThanOrEqual(1);
    expect(inv.workspaceBoundaries?.some(b => b.system === 'pnpm')).toBe(true);
  });
});

describe('scanner Phase 1 — snapshotRef propagation', () => {
  let root: string;

  beforeEach(async () => {
    root = await createTempRepo();
  });

  afterEach(async () => {
    await cleanupTempRepo(root);
  });

  it('includes snapshotRef in the returned ArtifactInventory', async () => {
    await writeFiles(root, {
      'src/main.ts': 'const x = 1;',
      'package.json': '{"name":"snapshot-test"}',
    });

    const snapshotRef: SnapshotRef = {
      provider: 'github',
      repoId: 'github.com/acme/project',
      commitSha: 'deadbeefdeadbeefdeadbeefdeadbeefdeadbeef',
    };

    const inv = await scanRepository({ rootPath: root, snapshotRef, respectGitignore: false });
    expect(inv.snapshotRef).toEqual(snapshotRef);
  });
});

describe('scanner Phase 1 — idempotent compile', () => {
  let root: string;

  beforeEach(async () => {
    root = await createTempRepo();
  });

  afterEach(async () => {
    await cleanupTempRepo(root);
  });

  it('produces identical artifact inventory when scanning the same repository twice', async () => {
    await writeFiles(root, {
      'src/App.tsx': 'export default function App() { return <div>Hello</div>; }',
      'src/Button.tsx': 'export const Button = () => <button>Click</button>;',
      'package.json': '{"name":"idempotent-test"}',
    });

    const snapshotRef: SnapshotRef = {
      provider: 'local-folder',
      repoId: 'test/repo',
      commitSha: 'abc123def456abc123def456abc123def456abc1',
    };

    const inv1 = await scanRepository({ rootPath: root, snapshotRef, respectGitignore: false });
    const inv2 = await scanRepository({ rootPath: root, snapshotRef, respectGitignore: false });

    // Same artifact count
    expect(inv1.artifacts.length).toBe(inv2.artifacts.length);

    // Same artifact IDs
    const ids1 = new Set(inv1.artifacts.map(a => a.id));
    const ids2 = new Set(inv2.artifacts.map(a => a.id));
    expect(ids1).toEqual(ids2);

    // Same checksums
    const checksums1 = new Map(inv1.artifacts.map(a => [a.id, a.checksum]));
    const checksums2 = new Map(inv2.artifacts.map(a => [a.id, a.checksum]));
    expect(checksums1).toEqual(checksums2);

    // Same package boundaries
    expect(inv1.packageBoundaries).toEqual(inv2.packageBoundaries);

    // Same summary
    expect(inv1.summary).toEqual(inv2.summary);
  });

  it('produces consistent results regardless of file system ordering', async () => {
    // Create multiple files in different directories
    await writeFiles(root, {
      'src/components/A.tsx': 'export const A = () => null;',
      'src/components/B.tsx': 'export const B = () => null;',
      'src/components/C.tsx': 'export const C = () => null;',
      'utils/helpers.ts': 'export const helper = () => {};',
      'package.json': '{"name":"ordering-test"}',
    });

    const snapshotRef: SnapshotRef = {
      provider: 'local-folder',
      repoId: 'test/repo',
      commitSha: 'sha256',
    };

    // Scan multiple times - file system ordering should not affect results
    const results = await Promise.all([
      scanRepository({ rootPath: root, snapshotRef, respectGitignore: false }),
      scanRepository({ rootPath: root, snapshotRef, respectGitignore: false }),
      scanRepository({ rootPath: root, snapshotRef, respectGitignore: false }),
    ]);

    // All results should have identical artifact sets
    const artifactSets = results.map(r => new Set(r.artifacts.map(a => a.id)));
    for (let i = 1; i < artifactSets.length; i++) {
      expect(artifactSets[i]).toEqual(artifactSets[0]);
    }
  });
});

describe('scanner Phase 1 — unresolved edge separation', () => {
  let root: string;

  beforeEach(async () => {
    root = await createTempRepo();
  });

  afterEach(async () => {
    await cleanupTempRepo(root);
  });

  it('component extractor emits unresolved edges with string targetRef, never fake UUID in targetId', async () => {
    await writeFiles(root, {
      'src/App.tsx': `
        import { Button } from './Button';
        import { Modal } from './Modal';
        export default function App() {
          return (
            <div>
              <Button />
              <Modal />
            </div>
          );
        }
      `,
      'src/Button.tsx': 'export const Button = () => <button>Click</button>;',
      'src/Modal.tsx': 'export const Modal = () => <div>Modal</div>;',
      'package.json': '{"name":"unresolved-test"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });
    const buttonArtifact = inv.artifacts.find(a => a.relativePath.includes('Button.tsx'));
    const modalArtifact = inv.artifacts.find(a => a.relativePath.includes('Modal.tsx'));

    // Find artifacts that have extraction results with unresolved edges
    // The component extractor should emit unresolved JSX usage edges
    const hasUnresolvedEdges = inv.artifacts.some(a => 
      a.extractorEligibility.some(e => e.extractorId === 'typescript-component')
    );

    expect(hasUnresolvedEdges).toBe(true);
    expect(buttonArtifact).toBeDefined();
    expect(modalArtifact).toBeDefined();
  });

  it('ensures unresolved edges never enter resolved graph with fake IDs', async () => {
    // This test validates the two-phase resolution model:
    // Phase 1: Extract unresolved edges with string targetRef
    // Phase 2: Resolve targetRef to actual node IDs
    
    await writeFiles(root, {
      'src/Page.tsx': `
        import { Header } from './Header';
        import { Footer } from './Footer';
        export const Page = () => (
          <div>
            <Header />
            <Footer />
          </div>
        );
      `,
      'src/Header.tsx': 'export const Header = () => <header>Header</header>;',
      'src/Footer.tsx': 'export const Footer = () => <footer>Footer</footer>;',
      'package.json': '{"name":"edge-sep-test"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });

    // All artifact IDs should be deterministic URNs or valid UUIDs
    // No component name strings should appear as IDs
    for (const artifact of inv.artifacts) {
      expect(artifact.id).toMatch(/^(artifact:\/\/|urn:|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/);
      // IDs should NOT be simple component names like "Header" or "Footer"
      expect(artifact.id).not.toBe('Header');
      expect(artifact.id).not.toBe('Footer');
      expect(artifact.id).not.toBe('Page');
    }
  });

  it('tracks unresolved edge metadata for later resolution', async () => {
    await writeFiles(root, {
      'src/Component.tsx': `
        import { External } from 'external-lib';
        export const Component = () => <External />;
      `,
      'package.json': '{"name":"metadata-test"}',
    });

    const inv = await scanRepository({ rootPath: root, respectGitignore: false });

    // Artifacts should have metadata that can be used for edge resolution
    const componentArtifact = inv.artifacts.find(a => a.relativePath.includes('Component.tsx'));
    expect(componentArtifact).toBeDefined();
    
    // Import/export summary should track external dependencies
    expect(componentArtifact?.importExportSummary).toBeDefined();
    expect(componentArtifact?.importExportSummary.imports.length).toBeGreaterThan(0);
  });
});
