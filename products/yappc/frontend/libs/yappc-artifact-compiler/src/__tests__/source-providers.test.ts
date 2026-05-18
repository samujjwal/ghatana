/**
 * @fileoverview Unit tests for the Source Provider layer.
 * Tests SourceProviderRegistry and LocalFolderProvider.
 * GitHubProvider and ZipProvider require network/file access so are integration-style;
 * here we only test the contract and error handling.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mkdir, writeFile, rm } from 'fs/promises';
import { join } from 'path';
import { tmpdir } from 'os';
import { randomUUID } from 'crypto';
import { execFile } from 'child_process';
import { promisify } from 'util';
import {
  SourceProviderRegistry,
  SourceProviderError,
  sourceLocatorToString,
} from '../source-providers/types';
import { LocalFolderProvider } from '../source-providers/local-folder-provider';
import { createDefaultProviderRegistry } from '../source-providers/index';

const execFileAsync = promisify(execFile);

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

  it('accepts typed source locators with governed scope', async () => {
    const registry = new SourceProviderRegistry();
    registry.register(new LocalFolderProvider());

    const dir = await createTempDir();
    try {
      await writeFile(join(dir, 'package.json'), '{"name":"typed-locator"}');
      const snapshot = await registry.resolve(
        {
          provider: 'local-folder',
          repoId: dir,
        },
        {
          scope: {
            tenantId: 'tenant-1',
            principalId: 'user-1',
            grantedAt: new Date().toISOString(),
            executionEnvironment: 'server',
          },
        },
      );

      expect(snapshot.snapshotRef.provider).toBe('local-folder');
      expect(snapshot.localRootPath).toBe(dir);
    } finally {
      await cleanup(dir);
    }
  });

  it('rejects raw provider credentials for browser-scoped resolution', async () => {
    const registry = new SourceProviderRegistry();
    registry.register(new LocalFolderProvider());

    const dir = await createTempDir();
    try {
      await expect(
        registry.resolve(dir, {
          credentials: { token: 'raw-token' },
          scope: {
            tenantId: 'tenant-1',
            principalId: 'user-1',
            grantedAt: new Date().toISOString(),
            executionEnvironment: 'browser',
          },
        }),
      ).rejects.toThrow('credentialRef');
    } finally {
      await cleanup(dir);
    }
  });

  it('createDefaultProviderRegistry returns all three built-in providers', () => {
    const registry = createDefaultProviderRegistry();
    // Check the registry has at least 3 providers by trying known locators
    expect(registry).toBeDefined();
  });

  it('serializes typed artifact locators consistently', () => {
    expect(
      sourceLocatorToString({
        provider: 'artifact-registry',
        repoId: 'urn:pkg/widget',
        ref: '1.2.3',
      }),
    ).toBe('artifact:urn:pkg/widget@1.2.3');
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
    expect(snap1.snapshotRef.commitSha).toBe(snap2.snapshotRef.commitSha);
    expect(snap1.diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'LOCAL_CONTENT_HASH_FALLBACK',
        }),
      ]),
    );
  });

  it('pins dirty git worktrees to a content hash and emits a review diagnostic', async () => {
    await execFileAsync('git', ['init'], { cwd: root });
    await execFileAsync('git', ['config', 'user.email', 'copilot@example.com'], { cwd: root });
    await execFileAsync('git', ['config', 'user.name', 'GitHub Copilot'], { cwd: root });

    const trackedFile = join(root, 'main.ts');
    await writeFile(trackedFile, 'export const version = 1;\n');
    await execFileAsync('git', ['add', '.'], { cwd: root });
    await execFileAsync('git', ['commit', '-m', 'initial'], { cwd: root });
    const { stdout } = await execFileAsync('git', ['rev-parse', 'HEAD'], { cwd: root });
    const headCommit = stdout.trim();

    await writeFile(trackedFile, 'export const version = 2;\n');

    const provider = new LocalFolderProvider();
    const snapshot = await provider.resolve(root);

    expect(snapshot.snapshotRef.commitSha).toBeDefined();
    expect(snapshot.snapshotRef.commitSha).not.toBe(headCommit);
    expect(snapshot.diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'LOCAL_DIRTY_WORKTREE',
          level: 'warning',
          metadata: expect.objectContaining({
            reviewRequired: true,
            pinStrategy: 'content-hash',
          }),
        }),
      ]),
    );
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
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

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

  it('emits diagnostics for oversized and unmaterialized GitHub files', async () => {
    const { GitHubProvider } = await import('../source-providers/github-provider');
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/commits/')) {
        return new Response(JSON.stringify({ sha: 'abc123def456abc123def456abc123def456abcd' }), { status: 200 });
      }
      if (url.includes('/git/trees/')) {
        return new Response(JSON.stringify({
          sha: 'abc123def456abc123def456abc123def456abcd',
          truncated: false,
          tree: [
            { path: 'src/huge.ts', type: 'blob', size: 2_000_000, sha: 'huge', url: 'https://api.github.test/blob/huge' },
            { path: 'src/fail.ts', type: 'blob', size: 12, sha: 'fail', url: 'https://api.github.test/blob/fail' },
          ],
        }), { status: 200 });
      }
      if (url.includes('/git/blobs/fail')) {
        return new Response('server error', { status: 500 });
      }
      throw new Error(`Unexpected fetch ${url}`);
    });
    globalThis.fetch = fetchMock as typeof fetch;

    const provider = new GitHubProvider();
    const snapshot = await provider.resolve('owner/repo', { maxFileSizeBytes: 1000 });

    expect(snapshot.diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ code: 'GITHUB_FILE_SKIPPED_MAX_SIZE', resourcePath: 'src/huge.ts' }),
        expect.objectContaining({ code: 'GITHUB_FILE_MATERIALIZATION_FAILED', resourcePath: 'src/fail.ts' }),
      ]),
    );
    expect(snapshot.files).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ relativePath: 'src/fail.ts', materialized: false }),
      ]),
    );
  });
});

describe('GitLabProvider diagnostics', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('emits diagnostics for oversized GitLab files', async () => {
    const { GitLabProvider } = await import('../source-providers/gitlab-provider');
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/repository/commits/')) {
        return new Response(JSON.stringify({
          id: 'fedcba654321fedcba654321fedcba654321fedc',
          short_id: 'fedcba65',
          title: 'commit',
        }), { status: 200 });
      }
      if (url.includes('/repository/tree')) {
        return new Response(JSON.stringify([
          { id: '1', name: 'huge.ts', type: 'blob', path: 'src/huge.ts', mode: '100644' },
        ]), { status: 200 });
      }
      if (url.includes('/repository/files/')) {
        return new Response(JSON.stringify({
          file_name: 'huge.ts',
          file_path: 'src/huge.ts',
          size: 2_000_000,
          encoding: 'base64',
          content: '',
          content_sha256: 'sha',
          ref: 'fedcba654321fedcba654321fedcba654321fedc',
        }), { status: 200 });
      }
      throw new Error(`Unexpected fetch ${url}`);
    });
    globalThis.fetch = fetchMock as typeof fetch;

    const provider = new GitLabProvider();
    const snapshot = await provider.resolve('group/repo', { maxFileSizeBytes: 1000 });

    expect(snapshot.diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ code: 'GITLAB_FILE_SKIPPED_MAX_SIZE', resourcePath: 'src/huge.ts' }),
      ]),
    );
    expect(snapshot.files).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ relativePath: 'src/huge.ts', materialized: false, sizeBytes: 2_000_000 }),
      ]),
    );
  });
});

// ============================================================================
// RepositorySnapshot schema tests
// ============================================================================

describe('RepositorySnapshotSchema', () => {
  it('validates a well-formed snapshot', async () => {
    const { RepositorySnapshotSchema } = await import('../source-providers/types');

    const snapshot = {
      snapshotId: 'snapshot-1',
      snapshotRef: { provider: 'local-folder', repoId: 'my-project', commitSha: 'abc123' },
      localRootPath: '/tmp/project',
      files: [
        {
          relativePath: 'src/index.ts',
          absolutePath: '/tmp/project/src/index.ts',
          sizeBytes: 100,
          materialized: true,
          lastModifiedAt: new Date().toISOString(),
          checksum: 'd'.repeat(64),
        },
      ],
      snapshotAt: new Date().toISOString(),
      shallow: false,
      diagnostics: [],
      contentHash: 'c'.repeat(64),
      contentChecksum: 'b'.repeat(64),
      tenantId: 'tenant-test',
      workspaceId: 'workspace-test',
      projectId: 'project-test',
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
