/**
 * @fileoverview GitLab source provider.
 *
 * Resolves GitLab repos into a RepositorySnapshot using the GitLab API.
 * Materializes files into a temp directory. Generates stable deterministic SnapshotRef
 * including the resolved commit SHA.
 *
 * Locator formats supported:
 *   - "owner/repo"                (default branch HEAD)
 *   - "owner/repo@branch"
 *   - "owner/repo@sha"
 *   - "https://gitlab.com/owner/repo"
 *   - "https://gitlab.com/owner/repo/-/tree/branch"
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

interface ParsedGitLabLocator {
  readonly owner: string;
  readonly repo: string;
  readonly ref: string;
}

const GITLAB_URL_RE = /gitlab\.com\/([^/]+)\/([^/?#]+)(?:\/-\/tree\/([^/?#]+))?/;
const SLUG_RE = /^([\w.-]+)\/([\w.-]+)(?:@(.+))?$/;

function parseLocator(locator: string): ParsedGitLabLocator | null {
  // Full URL
  const urlMatch = GITLAB_URL_RE.exec(locator);
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
// GitLab API types (minimal)
// ============================================================================

interface GitLabCommitResponse {
  readonly id: string;
  readonly short_id: string;
  readonly title: string;
}

interface GitLabTreeEntry {
  readonly id: string;
  readonly name: string;
  readonly type: 'blob' | 'tree';
  readonly path: string;
  readonly mode: string;
}

interface GitLabFileResponse {
  readonly file_name: string;
  readonly file_path: string;
  readonly size: number;
  readonly encoding: string;
  readonly content: string;
  readonly content_sha256: string;
  readonly ref: string;
}

// ============================================================================
// API helpers
// ============================================================================

async function gitlabApiFetch<T>(
  host: string,
  path: string,
  token: string | undefined,
  timeoutMs: number,
): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const headers: Record<string, string> = {
      Accept: 'application/json',
    };
    if (token) headers['PRIVATE-TOKEN'] = token;

    const response = await fetch(`${host}${path}`, {
      headers,
      signal: controller.signal,
    });

    if (!response.ok) {
      const text = await response.text().catch(() => '');
      throw new Error(`GitLab API ${response.status}: ${text.slice(0, 200)}`);
    }

    return (await response.json()) as T;
  } finally {
    clearTimeout(timer);
  }
}

// ============================================================================
// GitLab Provider
// ============================================================================

export class GitLabProvider implements SourceProvider {
  readonly providerId = 'gitlab' as const;

  private readonly apiHost: string;

  constructor(apiHost: string = 'https://gitlab.com/api/v4') {
    this.apiHost = apiHost;
  }

  canHandle(locator: string): boolean {
    return (
      locator.includes('gitlab.com') ||
      locator.startsWith('gitlab:') ||
      SLUG_RE.test(locator)
    );
  }

  async resolve(locator: string, options?: SourceProviderOptions): Promise<RepositorySnapshot> {
    // Normalize gitlab: prefix
    const normalizedLocator = locator.startsWith('gitlab:') ? locator.slice('gitlab:'.length) : locator;
    const parsed = parseLocator(normalizedLocator);
    if (!parsed) {
      throw new SourceProviderError(this.providerId, locator, 'Cannot parse GitLab locator');
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
    parsed: ParsedGitLabLocator,
    _locator: string,
    options?: SourceProviderOptions,
  ): Promise<RepositorySnapshot> {

    const token = options?.credentials?.token;
    const timeoutMs = options?.requestTimeoutMs ?? 30_000;
    const maxFileSizeBytes = options?.maxFileSizeBytes ?? 1 * 1024 * 1024; // 1MB default
    const maxFiles = options?.maxFiles ?? 10_000;

    const encodedOwner = encodeURIComponent(parsed.owner);
    const encodedRepo = encodeURIComponent(parsed.repo);

    // Resolve the commit SHA
    const commitRef = await gitlabApiFetch<GitLabCommitResponse>(
      this.apiHost,
      `/projects/${encodedOwner}%2F${encodedRepo}/repository/commits/${parsed.ref}`,
      token,
      timeoutMs,
    );
    const commitSha = commitRef.id;

    // Get repository tree (paginated)
    const files: SnapshotFile[] = [];
    let fileCount = 0;
    let page = 1;
    const perPage = 100;

    while (true) {
      if (fileCount >= maxFiles) break;

      const treeResponse = await gitlabApiFetch<GitLabTreeEntry[]>(
        this.apiHost,
        `/projects/${encodedOwner}%2F${encodedRepo}/repository/tree?ref=${commitSha}&per_page=${perPage}&page=${page}&recursive=true`,
        token,
        timeoutMs,
      );

      if (treeResponse.length === 0) break;

      for (const entry of treeResponse) {
        if (fileCount >= maxFiles) break;
        if (entry.type !== 'blob') continue;

        // Get file metadata to check size
        try {
          const fileMeta = await gitlabApiFetch<GitLabFileResponse>(
            this.apiHost,
            `/projects/${encodedOwner}%2F${encodedRepo}/repository/files/${encodeURIComponent(entry.path)}?ref=${commitSha}`,
            token,
            timeoutMs,
          );

          if (fileMeta.size > maxFileSizeBytes) {
            files.push({
              relativePath: entry.path,
              materialized: false,
              sizeBytes: fileMeta.size,
              lastModifiedAt: new Date().toISOString(),
            });
            continue;
          }

          // Materialize the file
          const content = Buffer.from(fileMeta.content, 'base64');

          const tempRoot = join(
            options?.tempDir ?? tmpdir(),
            `yappc-gitlab-${parsed.owner}-${parsed.repo}-${commitSha.slice(0, 8)}-${randomUUID().slice(0, 8)}`,
          );
          await mkdir(tempRoot, { recursive: true });

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
            sizeBytes: 0,
            lastModifiedAt: new Date().toISOString(),
          });
        }
      }

      page++;
      // GitLab API returns empty array when no more results
      if (treeResponse.length < perPage) break;
    }

    const snapshotRef: SnapshotRef = {
      provider: 'gitlab',
      repoId: `gitlab.com/${parsed.owner}/${parsed.repo}`,
      commitSha,
      branch: parsed.ref !== 'HEAD' ? parsed.ref : undefined,
    };

    const tempRoot = join(
      options?.tempDir ?? tmpdir(),
      `yappc-gitlab-${parsed.owner}-${parsed.repo}-${commitSha.slice(0, 8)}-${randomUUID().slice(0, 8)}`,
    );
    await mkdir(tempRoot, { recursive: true });

    return {
      snapshotRef,
      localRootPath: tempRoot,
      files,
      snapshotAt: new Date().toISOString(),
      shallow: false,
    };
  }
}
