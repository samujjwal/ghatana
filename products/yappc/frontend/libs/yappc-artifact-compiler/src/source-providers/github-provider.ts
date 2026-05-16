/**
 * @fileoverview GitHub source provider.
 *
 * Resolves GitHub repos into a RepositorySnapshot using the GitHub Contents API.
 * Materializes files into a temp directory. Generates stable deterministic SnapshotRef
 * including the resolved commit SHA.
 *
 * Locator formats supported:
 *   - "owner/repo"                (default branch HEAD)
 *   - "owner/repo@branch"
 *   - "owner/repo@sha"
 *   - "https://github.com/owner/repo"
 *   - "https://github.com/owner/repo/tree/branch"
 */

import { mkdir, writeFile } from 'fs/promises';
import { join } from 'path';
import { tmpdir } from 'os';
import { randomUUID } from 'crypto';
import type { SourceProvider, SourceProviderOptions, SnapshotFile, RepositorySnapshot } from './types';
import { SourceProviderError } from './types';
import type { SnapshotRef } from '../graph/types';

// ============================================================================
// Locator parsing
// ============================================================================

interface ParsedGitHubLocator {
  readonly owner: string;
  readonly repo: string;
  readonly ref: string;
}

const GITHUB_URL_RE = /github\.com\/([^/]+)\/([^/?#]+)(?:\/tree\/([^/?#]+))?/;
const SLUG_RE = /^([\w.-]+)\/([\w.-]+)(?:@(.+))?$/;

function parseLocator(locator: string): ParsedGitHubLocator | null {
  // Full URL
  const urlMatch = GITHUB_URL_RE.exec(locator);
  if (urlMatch) {
    return {
      owner: urlMatch[1]!,
      repo: urlMatch[2]!.replace(/\.git$/, ''),
      ref: urlMatch[3] ?? 'HEAD',
    };
  }
  // Slug
  const slugMatch = SLUG_RE.exec(locator);
  if (slugMatch) {
    return {
      owner: slugMatch[1]!,
      repo: slugMatch[2]!,
      ref: slugMatch[3] ?? 'HEAD',
    };
  }
  return null;
}

// ============================================================================
// GitHub API types (minimal)
// ============================================================================

interface GitHubCommitResponse {
  readonly sha: string;
}

interface GitHubTreeEntry {
  readonly path: string;
  readonly type: 'blob' | 'tree';
  readonly size?: number;
  readonly sha: string;
  readonly url: string;
}

interface GitHubTreeResponse {
  readonly sha: string;
  readonly tree: readonly GitHubTreeEntry[];
  readonly truncated: boolean;
}

interface GitHubBlobResponse {
  readonly content: string;
  readonly encoding: 'base64' | 'utf-8';
  readonly size: number;
}

// ============================================================================
// API helpers
// ============================================================================

async function githubApiFetch<T>(
  path: string,
  token: string | undefined,
  timeoutMs: number,
): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const headers: Record<string, string> = {
      Accept: 'application/vnd.github.v3+json',
    };
    if (token) headers['Authorization'] = `token ${token}`;

    const response = await fetch(`https://api.github.com${path}`, {
      headers,
      signal: controller.signal,
    });

    if (!response.ok) {
      const text = await response.text().catch(() => '');
      throw new Error(`GitHub API ${response.status}: ${text.slice(0, 200)}`);
    }

    return (await response.json()) as T;
  } finally {
    clearTimeout(timer);
  }
}

// ============================================================================
// GitHub Provider
// ============================================================================

export class GitHubProvider implements SourceProvider {
  readonly providerId = 'github' as const;

  canHandle(locator: string): boolean {
    return (
      locator.includes('github.com') ||
      locator.startsWith('github:') ||
      SLUG_RE.test(locator)
    );
  }

  async resolve(locator: string, options?: SourceProviderOptions): Promise<RepositorySnapshot> {
    // Normalize github: prefix
    const normalizedLocator = locator.startsWith('github:') ? locator.slice('github:'.length) : locator;
    const parsed = parseLocator(normalizedLocator);
    if (!parsed) {
      throw new SourceProviderError(this.providerId, locator, 'Cannot parse GitHub locator');
    }

    try {
      return await this.doResolve(parsed, locator, options);
    } catch (err) {
      if (err instanceof SourceProviderError) throw err;
      const msg = err instanceof Error ? err.message : String(err);
      throw new SourceProviderError(this.providerId, locator, msg, err);
    }
  }

  private async doResolve(
    parsed: ParsedGitHubLocator,
    _locator: string,
    options?: SourceProviderOptions,
  ): Promise<RepositorySnapshot> {

    const token = options?.credentials?.token;
    const timeoutMs = options?.requestTimeoutMs ?? 30_000;
    const maxFileSizeBytes = options?.maxFileSizeBytes ?? 1 * 1024 * 1024; // 1MB default
    const maxFiles = options?.maxFiles ?? 10_000;

    // Resolve the commit SHA
    const commitRef = await githubApiFetch<GitHubCommitResponse>(
      `/repos/${parsed.owner}/${parsed.repo}/commits/${parsed.ref}`,
      token,
      timeoutMs,
    );
    const commitSha = commitRef.sha;

    // Fetch the recursive tree
    const treeResponse = await githubApiFetch<GitHubTreeResponse>(
      `/repos/${parsed.owner}/${parsed.repo}/git/trees/${commitSha}?recursive=1`,
      token,
      timeoutMs,
    );

    if (treeResponse.truncated) {
      // Warn but continue — large repos may have truncated trees
      console.warn(`[github-provider] Tree truncated for ${parsed.owner}/${parsed.repo}@${commitSha}. Some files may be missing.`);
    }

    // Materialize to a temp directory
    const tempRoot = join(
      options?.tempDir ?? tmpdir(),
      `yappc-github-${parsed.owner}-${parsed.repo}-${commitSha.slice(0, 8)}-${randomUUID().slice(0, 8)}`,
    );
    await mkdir(tempRoot, { recursive: true });

    const snapshotRef: SnapshotRef = {
      provider: 'github',
      repoId: `github.com/${parsed.owner}/${parsed.repo}`,
      commitSha,
      branch: parsed.ref !== 'HEAD' ? parsed.ref : undefined,
    };

    const files: SnapshotFile[] = [];
    let fileCount = 0;

    for (const entry of treeResponse.tree) {
      if (fileCount >= maxFiles) break;
      if (entry.type !== 'blob') continue;
      if (entry.size !== undefined && entry.size > maxFileSizeBytes) continue;

      // Materialize the blob
      try {
        const blob = await githubApiFetch<GitHubBlobResponse>(
          `/repos/${parsed.owner}/${parsed.repo}/git/blobs/${entry.sha}`,
          token,
          timeoutMs,
        );
        const content = blob.encoding === 'base64'
          ? Buffer.from(blob.content.replace(/\n/g, ''), 'base64')
          : Buffer.from(blob.content, 'utf-8');

        const absolutePath = join(tempRoot, entry.path.replace(/\//g, '/'));
        const dir = absolutePath.slice(0, absolutePath.lastIndexOf('/'));
        await mkdir(dir, { recursive: true });
        await writeFile(absolutePath, content);

        files.push({
          relativePath: entry.path,
          absolutePath,
          materialized: true,
          sizeBytes: content.byteLength,
          lastModifiedAt: new Date().toISOString(),
        });
        fileCount++;
      } catch {
        // Non-fatal: record without materialization
        files.push({
          relativePath: entry.path,
          materialized: false,
          sizeBytes: entry.size ?? 0,
          lastModifiedAt: new Date().toISOString(),
        });
      }
    }

    return {
      snapshotRef,
      localRootPath: tempRoot,
      files,
      snapshotAt: new Date().toISOString(),
      shallow: false,
    };
  }
}

