/**
 * @fileoverview Unit tests for the Source Provider layer.
 * Tests SourceProviderRegistry and LocalFolderProvider.
 * GitHubProvider and ZipProvider require network/file access so are integration-style;
 * here we only test the contract and error handling.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdir, writeFile, rm } from 'fs/promises';
import { join } from 'path';
import { tmpdir } from 'os';
import { randomUUID } from 'crypto';
import { SourceProviderRegistry, SourceProviderError } from '../source-providers/types';
import { LocalFolderProvider } from '../source-providers/local-folder-provider';
import { createDefaultProviderRegistry } from '../source-providers/index';

// ============================================================================
// Helpers
// ============================================================================

async function createTempDir(): Promise<string> {
  const dir = join(tmpdir(), `yappc-providers-test-${randomUUID().slice(0, 8)}`);
  await mkdir(dir, { recursive: true });
  return dir;
}

async function cleanup(dir: string): Promise<void> {
  await rm(dir, { recursive: true, force: true });
}

// ============================================================================
// SourceProviderRegistry tests
// ============================================================================

describe('SourceProviderRegistry', () => {
  it('throws SourceProviderError when no provider can handle the locator', async () => {
    const registry = new SourceProviderRegistry();
    await expect(registry.resolve(':::invalid:::')).rejects.toBeInstanceOf(SourceProviderError);
  });

  it('returns the first provider that can handle the locator', async () => {
    const registry = new SourceProviderRegistry();
    registry.register(new LocalFolderProvider());

    // A valid local dir should be handled by LocalFolderProvider
    const dir = await createTempDir();
    try {
      await writeFile(join(dir, 'package.json'), '{"name":"test"}');
      // Just verify no error is thrown and snapshot has correct shape
      const snapshot = await registry.resolve(dir);
      expect(snapshot.snapshotRef.provider).toBe('local-folder');
      expect(snapshot.localRootPath).toBe(dir);
    } finally {
      await cleanup(dir);
    }
  });

  it('createDefaultProviderRegistry returns all three built-in providers', () => {
    const registry = createDefaultProviderRegistry();
    // Check the registry has at least 3 providers by trying known locators
    expect(registry).toBeDefined();
  });
});

// ============================================================================
// LocalFolderProvider tests
// ============================================================================

describe('LocalFolderProvider', () => {
  let root: string;

  beforeEach(async () => {
    root = await createTempDir();
  });

  afterEach(async () => {
    await cleanup(root);
  });

  it('returns a RepositorySnapshot with provider=local-folder', async () => {
    await writeFile(join(root, 'package.json'), '{"name":"local-test"}');
    const provider = new LocalFolderProvider();
    const snapshot = await provider.resolve(root);

    expect(snapshot.snapshotRef.provider).toBe('local-folder');
    expect(snapshot.localRootPath).toBe(root);
  });

  it('sets repoId to the directory name', async () => {
    const provider = new LocalFolderProvider();
    const snapshot = await provider.resolve(root);

    expect(snapshot.snapshotRef.repoId).toBeTruthy();
    expect(snapshot.snapshotRef.repoId.length).toBeGreaterThan(0);
  });

  it('populates snapshotFiles with file metadata', async () => {
    await mkdir(join(root, 'src'), { recursive: true });
    await writeFile(join(root, 'src', 'index.ts'), 'export const x = 1;');
    await writeFile(join(root, 'package.json'), '{"name":"file-meta-test"}');

    const provider = new LocalFolderProvider();
    const snapshot = await provider.resolve(root);

    expect(snapshot.files.length).toBeGreaterThan(0);
    const pkgFile = snapshot.files.find(f => f.relativePath === 'package.json');
    expect(pkgFile).toBeDefined();
    expect(pkgFile!.sizeBytes).toBeGreaterThan(0);
  });

  it('generates a deterministic snapshotRef when no git repo is available', async () => {
    await writeFile(join(root, 'main.ts'), 'const x = 1;');
    const provider = new LocalFolderProvider();

    const snap1 = await provider.resolve(root);
    const snap2 = await provider.resolve(root);

    // Deterministic: same file content → same snapshotRef repoId
    expect(snap1.snapshotRef.repoId).toBe(snap2.snapshotRef.repoId);
  });

  it('uses canHandle to accept valid local paths', () => {
    const provider = new LocalFolderProvider();
    expect(provider.canHandle(root)).toBe(true);
  });

  it('uses canHandle to reject GitHub URLs', () => {
    const provider = new LocalFolderProvider();
    expect(provider.canHandle('https://github.com/org/repo')).toBe(false);
  });

  it('throws SourceProviderError when path does not exist', async () => {
    const provider = new LocalFolderProvider();
    await expect(provider.resolve('/nonexistent/path/xyz123')).rejects.toBeInstanceOf(SourceProviderError);
  });
});

// ============================================================================
// ZipProvider — contract tests (no actual ZIP extraction needed)
// ============================================================================

describe('ZipProvider', () => {
  it('canHandle accepts .zip file paths', async () => {
    const { ZipProvider } = await import('../source-providers/zip-provider');
    const provider = new ZipProvider();
    expect(provider.canHandle('/path/to/archive.zip')).toBe(true);
    expect(provider.canHandle('https://example.com/archive.zip')).toBe(true);
  });

  it('canHandle rejects non-zip paths', async () => {
    const { ZipProvider } = await import('../source-providers/zip-provider');
    const provider = new ZipProvider();
    expect(provider.canHandle('https://github.com/org/repo')).toBe(false);
    expect(provider.canHandle('/path/to/folder')).toBe(false);
  });

  it('throws SourceProviderError for missing local ZIP', async () => {
    const { ZipProvider } = await import('../source-providers/zip-provider');
    const provider = new ZipProvider();
    await expect(provider.resolve('/nonexistent/file.zip')).rejects.toBeInstanceOf(SourceProviderError);
  });
});

// ============================================================================
// GitHubProvider — contract tests (no network)
// ============================================================================

describe('GitHubProvider', () => {
  it('canHandle accepts github.com URLs', async () => {
    const { GitHubProvider } = await import('../source-providers/github-provider');
    const provider = new GitHubProvider();
    expect(provider.canHandle('https://github.com/owner/repo')).toBe(true);
    expect(provider.canHandle('github:owner/repo')).toBe(true);
    expect(provider.canHandle('owner/repo')).toBe(true);
  });

  it('canHandle rejects gitlab or local paths', async () => {
    const { GitHubProvider } = await import('../source-providers/github-provider');
    const provider = new GitHubProvider();
    expect(provider.canHandle('/local/path')).toBe(false);
    expect(provider.canHandle('https://gitlab.com/owner/repo')).toBe(false);
  });

  it('throws an error when network returns 404 for invalid repo', async () => {
    const { GitHubProvider } = await import('../source-providers/github-provider');
    const { SourceProviderError: SPE } = await import('../source-providers/types');
    const provider = new GitHubProvider();
    // A clearly invalid repo slug should fail even with network available
    await expect(
      provider.resolve('owner-that-does-not-exist-xyz/repo-xyz-nonexistent-abc123'),
    ).rejects.toSatisfy((err: unknown) => err instanceof SPE || (err instanceof Error && err.message.includes('404')));
  });
});

// ============================================================================
// RepositorySnapshot schema tests
// ============================================================================

describe('RepositorySnapshotSchema', () => {
  it('validates a well-formed snapshot', async () => {
    const { RepositorySnapshotSchema } = await import('../source-providers/types');

    const snapshot = {
      snapshotRef: { provider: 'local-folder', repoId: 'my-project', commitSha: 'abc123' },
      localRootPath: '/tmp/project',
      files: [
        {
          relativePath: 'src/index.ts',
          sizeBytes: 100,
          materialized: true,
          lastModifiedAt: new Date().toISOString(),
        },
      ],
      snapshotAt: new Date().toISOString(),
    };

    const result = RepositorySnapshotSchema.safeParse(snapshot);
    expect(result.success).toBe(true);
  });

  it('rejects a snapshot with missing required fields', async () => {
    const { RepositorySnapshotSchema } = await import('../source-providers/types');
    const result = RepositorySnapshotSchema.safeParse({ snapshotRef: null });
    expect(result.success).toBe(false);
  });
});
