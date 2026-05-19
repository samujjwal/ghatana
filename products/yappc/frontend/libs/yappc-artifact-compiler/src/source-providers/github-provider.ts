/**
 * @fileoverview GitHub source provider.
 *
 * P1-2: Hardened with credentialRef resolver, retry/backoff, rate-limit diagnostics, cleanup contract.
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
import { createHash } from 'crypto';
import type {
  SourceProvider,
  SourceProviderOptions,
  SourceProviderLocator,
  SnapshotFile,
  RepositorySnapshot,
  ProviderDiagnostic,
} from './types';
import {
  assertTsSourceProviderWorkerOnly,
  SourceProviderError,
  sourceLocatorToString,
  validateCredentialPolicy,
} from './types';
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

function sha256(content: Buffer | string): string {
  return createHash('sha256').update(content).digest('hex');
}

function buildSnapshotContentHash(files: readonly SnapshotFile[]): string {
  const hash = createHash('sha256');
  for (const file of [...files].sort((left, right) => left.relativePath.localeCompare(right.relativePath))) {
    hash.update(file.relativePath);
    hash.update('\0');
    hash.update(file.checksum);
    hash.update('\0');
  }
  return hash.digest('hex');
}

function resolveSnapshotScope(options: SourceProviderOptions | undefined): {
  tenantId: string;
  workspaceId: string;
  projectId: string;
} {
  return {
    tenantId: options?.scope?.tenantId ?? 'worker-local-tenant',
    workspaceId: options?.scope?.workspaceId ?? 'worker-local-workspace',
    projectId: options?.scope?.projectId ?? 'worker-local-project',
  };
}

// ============================================================================
// API helpers with retry/backoff and rate-limit handling
// ============================================================================

interface RetryConfig {
  maxRetries: number;
  baseDelayMs: number;
  maxDelayMs: number;
}

const DEFAULT_RETRY_CONFIG: RetryConfig = {
  maxRetries: 3,
  baseDelayMs: 1000,
  maxDelayMs: 10000,
};

async function githubApiFetch<T>(
  path: string,
  token: string | undefined,
  timeoutMs: number,
  retryConfig: RetryConfig = DEFAULT_RETRY_CONFIG,
  diagnostics: ProviderDiagnostic[] = [],
): Promise<T> {
  let lastError: Error | null = null;
  
  for (let attempt = 0; attempt <= retryConfig.maxRetries; attempt++) {
    if (attempt > 0) {
      const delayMs = Math.min(
        retryConfig.baseDelayMs * Math.pow(2, attempt - 1),
        retryConfig.maxDelayMs
      );
      await new Promise(resolve => setTimeout(resolve, delayMs));
    }
    
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

      // P1-2: Handle rate limiting
      const rateLimitRemaining = response.headers.get('x-ratelimit-remaining');
      const rateLimitReset = response.headers.get('x-ratelimit-reset');
      
      if (rateLimitRemaining && parseInt(rateLimitRemaining, 10) < 10) {
        diagnostics.push({
          level: 'warning',
          code: 'GITHUB_RATE_LIMIT_LOW',
          message: `GitHub rate limit nearly exhausted: ${rateLimitRemaining} remaining`,
          timestamp: new Date().toISOString(),
          metadata: {
            remaining: rateLimitRemaining,
            reset: rateLimitReset,
          },
        });
      }

      if (response.status === 429) {
        const resetTime = parseInt(response.headers.get('x-ratelimit-reset') || '0', 10) * 1000;
        const now = Date.now();
        const waitMs = Math.max(0, resetTime - now);
        
        diagnostics.push({
          level: 'warning',
          code: 'GITHUB_RATE_LIMIT_EXCEEDED',
          message: `GitHub rate limit exceeded. Waiting ${waitMs}ms before retry.`,
          timestamp: new Date().toISOString(),
          metadata: {
            resetTime: new Date(resetTime).toISOString(),
            waitMs,
          },
        });
        
        if (attempt < retryConfig.maxRetries) {
          await new Promise(resolve => setTimeout(resolve, waitMs));
          continue;
        }
      }

      if (!response.ok) {
        const text = await response.text().catch(() => '');
        throw new Error(`GitHub API ${response.status}: ${text.slice(0, 200)}`);
      }

      return (await response.json()) as T;
    } catch (err) {
      lastError = err instanceof Error ? err : new Error(String(err));
      
      // Don't retry on abort (timeout) or 4xx client errors (except 429 handled above)
      if (err instanceof Error && err.name === 'AbortError') {
        throw lastError;
      }
      
      if (lastError.message.includes('GitHub API 4')) {
        throw lastError;
      }
    } finally {
      clearTimeout(timer);
    }
  }
  
  throw lastError || new Error('GitHub API request failed after retries');
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

  async resolve(locator: SourceProviderLocator, options?: SourceProviderOptions): Promise<RepositorySnapshot> {
    validateCredentialPolicy(options?.scope, options?.credentials);
    assertTsSourceProviderWorkerOnly(this.providerId, options);
    const normalizedInput = sourceLocatorToString(locator);
    // Normalize github: prefix
    const normalizedLocator = normalizedInput.startsWith('github:') ? normalizedInput.slice('github:'.length) : normalizedInput;
    const parsed = parseLocator(normalizedLocator);
    if (!parsed) {
      throw new SourceProviderError(this.providerId, normalizedInput, 'Cannot parse GitHub locator');
    }

    // P1-2: Resolve credentials via credentialRef resolver
    let token = options?.credentials?.token;
    if (options?.credentialResolver && options.credentials?.credentialRef) {
      const scope = options.scope ?? { tenantId: 'default', principalId: 'system', grantedAt: new Date().toISOString() };
      const resolvedCreds = await options.credentialResolver.resolve(options.credentials.credentialRef, scope);
      token = resolvedCreds?.token;
    }

    try {
      const snapshot = await this.doResolve(parsed, normalizedInput, token, options);
      return snapshot;
    } catch (err) {
      if (err instanceof SourceProviderError) throw err;
      const msg = err instanceof Error ? err.message : String(err);
      throw new SourceProviderError(this.providerId, normalizedInput, msg, err);
    }
  }

  private async doResolve(
    parsed: ParsedGitHubLocator,
    _locator: string,
    token: string | undefined,
    options?: SourceProviderOptions,
  ): Promise<RepositorySnapshot> {

    const timeoutMs = options?.requestTimeoutMs ?? 30_000;
    const maxFileSizeBytes = options?.maxFileSizeBytes ?? 1 * 1024 * 1024; // 1MB default
    const maxFiles = options?.maxFiles ?? 10_000;
    const diagnostics: ProviderDiagnostic[] = [];

    // Resolve the commit SHA with retry/backoff
    const commitRef = await githubApiFetch<GitHubCommitResponse>(
      `/repos/${parsed.owner}/${parsed.repo}/commits/${parsed.ref}`,
      token,
      timeoutMs,
      DEFAULT_RETRY_CONFIG,
      diagnostics,
    );
    const commitSha = commitRef.sha;

    // Fetch the recursive tree with retry/backoff
    const treeResponse = await githubApiFetch<GitHubTreeResponse>(
      `/repos/${parsed.owner}/${parsed.repo}/git/trees/${commitSha}?recursive=1`,
      token,
      timeoutMs,
      DEFAULT_RETRY_CONFIG,
      diagnostics,
    );

    if (treeResponse.truncated) {
      // Fail closed: GitHub tree truncation means we cannot guarantee a complete snapshot
      throw new SourceProviderError(
        this.providerId,
        `${parsed.owner}/${parsed.repo}@${commitSha}`,
        `GitHub tree API returned truncated results. The repository is too large for the GitHub tree API (limit: 500k entries). Use an alternative method (e.g., git clone) for this repository.`,
      );
    }

    // P1-2: Deterministic snapshot metadata using commit SHA
    const snapshotRef: SnapshotRef = {
      provider: 'github',
      repoId: `github.com/${parsed.owner}/${parsed.repo}`,
      commitSha,
      branch: parsed.ref !== 'HEAD' ? parsed.ref : undefined,
    };

    // Materialize to a temp directory (deterministic path for same commit)
    const tempRoot = join(
      options?.tempDir ?? tmpdir(),
      `yappc-github-${parsed.owner}-${parsed.repo}-${commitSha.slice(0, 8)}`,
    );
    await mkdir(tempRoot, { recursive: true });

    const files: SnapshotFile[] = [];
    let fileCount = 0;

    for (const entry of treeResponse.tree) {
      if (fileCount >= maxFiles) {
        diagnostics.push({
          level: 'warning',
          code: 'GITHUB_MAX_FILES_REACHED',
          message: `GitHub snapshot stopped after reaching maxFiles=${maxFiles}.`,
          timestamp: new Date().toISOString(),
          metadata: { maxFiles },
        });
        break;
      }
      if (entry.type !== 'blob') continue;
      if (entry.size !== undefined && entry.size > maxFileSizeBytes) {
        diagnostics.push({
          level: 'warning',
          code: 'GITHUB_FILE_SKIPPED_MAX_SIZE',
          message: `Skipped oversized GitHub blob ${entry.path}.`,
          timestamp: new Date().toISOString(),
          resourcePath: entry.path,
          metadata: {
            sizeBytes: entry.size,
            maxFileSizeBytes,
          },
        });
        continue;
      }

      // Materialize the blob with retry/backoff
      try {
        const blob = await githubApiFetch<GitHubBlobResponse>(
          `/repos/${parsed.owner}/${parsed.repo}/git/blobs/${entry.sha}`,
          token,
          timeoutMs,
          DEFAULT_RETRY_CONFIG,
          diagnostics,
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
          checksum: sha256(content),
        });
        fileCount++;
      } catch (err) {
        // Non-fatal: record without materialization
        files.push({
          relativePath: entry.path,
          materialized: false,
          sizeBytes: entry.size ?? 0,
          lastModifiedAt: new Date().toISOString(),
          checksum: `github-blob:${entry.sha}`,
        });
        diagnostics.push({
          level: 'warning',
          code: 'GITHUB_FILE_MATERIALIZATION_FAILED',
          message: `Failed to materialize GitHub blob ${entry.path}; keeping metadata-only snapshot entry.`,
          timestamp: new Date().toISOString(),
          resourcePath: entry.path,
          metadata: {
            sizeBytes: entry.size ?? 0,
            error: err instanceof Error ? err.message : String(err),
          },
        });
      }
    }
    const contentHash = buildSnapshotContentHash(files);
    const scope = resolveSnapshotScope(options);

    return {
      snapshotId: `github:${parsed.owner}/${parsed.repo}:${commitSha}:${contentHash.slice(0, 32)}`,
      snapshotRef,
      localRootPath: tempRoot,
      files,
      snapshotAt: new Date().toISOString(),
      shallow: false,
      diagnostics,
      contentHash,
      contentChecksum: contentHash,
      ...scope,
    };
  }
}
