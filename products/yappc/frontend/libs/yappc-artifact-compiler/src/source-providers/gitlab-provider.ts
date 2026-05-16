/**
 * @fileoverview GitLab source provider.
 *
 * P1-3: Hardened with nested groups support, credentialRef resolver, retry/backoff, cleanup contract.
 *
 * Resolves GitLab repos into a RepositorySnapshot using the GitLab API.
 * Materializes files into a temp directory. Generates stable deterministic SnapshotRef
 * including the resolved commit SHA.
 *
 * Locator formats supported:
 *   - "owner/repo"                (default branch HEAD)
 *   - "group/subgroup/repo"        (nested groups)
 *   - "owner/repo@branch"
 *   - "owner/repo@sha"
 *   - "https://gitlab.com/owner/repo"
 *   - "https://gitlab.com/owner/repo/-/tree/branch"
 *   - "https://gitlab.com/group/subgroup/repo"
 */

import { mkdir, writeFile, rm } from 'fs/promises';
import { join } from 'path';
import { tmpdir } from 'os';
import type {
  SourceProvider,
  SourceProviderOptions,
  SourceProviderLocator,
  SnapshotFile,
  RepositorySnapshot,
  ProviderDiagnostic,
} from './types';
import { SourceProviderError, sourceLocatorToString, validateCredentialPolicy } from './types';
import type { SnapshotRef } from '../graph/types';

// ============================================================================
// Locator parsing with nested groups support
// ============================================================================

interface ParsedGitLabLocator {
  readonly projectPath: string;  // Full path including nested groups, e.g., "group/subgroup/repo"
  readonly repo: string;         // Repository name (last component)
  readonly ref: string;
}

const GITLAB_URL_RE = /gitlab\.com\/([^/]+(?:\/[^/]+)*)(?:\/-\/tree\/([^/?#]+))?/;
// P1-3: Support nested groups: "group/subgroup/repo" or "group/subgroup/repo@ref"
const SLUG_RE = /^([\w.-]+(?:\/[\w.-]+)*)(?:@(.+))?$/;

function parseLocator(locator: string): ParsedGitLabLocator | null {
  // Full URL
  const urlMatch = GITLAB_URL_RE.exec(locator);
  if (urlMatch) {
    const projectPath = urlMatch[1]!.replace(/\.git$/, '');
    const repo = projectPath.split('/').pop() || projectPath;
    return {
      projectPath,
      repo,
      ref: urlMatch[2] ?? 'HEAD',
    };
  }
  // Slug with nested groups
  const slugMatch = SLUG_RE.exec(locator);
  if (slugMatch) {
    const projectPath = slugMatch[1]!;
    const repo = projectPath.split('/').pop() || projectPath;
    return {
      projectPath,
      repo,
      ref: slugMatch[2] ?? 'HEAD',
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
// API helpers with retry/backoff
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

async function gitlabApiFetch<T>(
  host: string,
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
        Accept: 'application/json',
      };
      if (token) headers['PRIVATE-TOKEN'] = token;

      const response = await fetch(`${host}${path}`, {
        headers,
        signal: controller.signal,
      });

      // P1-3: Handle rate limiting
      const rateLimitRemaining = response.headers.get('ratelimit-remaining');
      const rateLimitReset = response.headers.get('ratelimit-reset');
      
      if (rateLimitRemaining && parseInt(rateLimitRemaining, 10) < 10) {
        diagnostics.push({
          level: 'warning',
          code: 'GITLAB_RATE_LIMIT_LOW',
          message: `GitLab rate limit nearly exhausted: ${rateLimitRemaining} remaining`,
          timestamp: new Date().toISOString(),
          metadata: {
            remaining: rateLimitRemaining,
            reset: rateLimitReset,
          },
        });
      }

      if (response.status === 429) {
        const resetTime = parseInt(response.headers.get('ratelimit-reset') || '0', 10) * 1000;
        const now = Date.now();
        const waitMs = Math.max(0, resetTime - now);
        
        diagnostics.push({
          level: 'warning',
          code: 'GITLAB_RATE_LIMIT_EXCEEDED',
          message: `GitLab rate limit exceeded. Waiting ${waitMs}ms before retry.`,
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
        throw new Error(`GitLab API ${response.status}: ${text.slice(0, 200)}`);
      }

      return (await response.json()) as T;
    } catch (err) {
      lastError = err instanceof Error ? err : new Error(String(err));
      
      // Don't retry on abort (timeout) or 4xx client errors (except 429 handled above)
      if (err instanceof Error && err.name === 'AbortError') {
        throw lastError;
      }
      
      if (lastError.message.includes('GitLab API 4')) {
        throw lastError;
      }
    } finally {
      clearTimeout(timer);
    }
  }
  
  throw lastError || new Error('GitLab API request failed after retries');
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
    if (locator.includes('github.com')) {
      return false;
    }
    return (
      locator.includes('gitlab.com') ||
      locator.startsWith('gitlab:') ||
      SLUG_RE.test(locator)
    );
  }

  async resolve(locator: SourceProviderLocator, options?: SourceProviderOptions): Promise<RepositorySnapshot> {
    validateCredentialPolicy(options?.scope, options?.credentials);
    const normalizedInput = sourceLocatorToString(locator);
    // Normalize gitlab: prefix
    const normalizedLocator = normalizedInput.startsWith('gitlab:') ? normalizedInput.slice('gitlab:'.length) : normalizedInput;
    const parsed = parseLocator(normalizedLocator);
    if (!parsed) {
      throw new SourceProviderError(this.providerId, normalizedInput, 'Cannot parse GitLab locator');
    }

    // P1-3: Resolve credentials via credentialRef resolver
    let token = options?.credentials?.token;
    if (options?.credentialResolver && options.credentials?.credentialRef) {
      const scope = options.scope ?? { tenantId: 'default', principalId: 'system', grantedAt: new Date().toISOString() };
      const resolvedCreds = await options.credentialResolver.resolve(options.credentials.credentialRef, scope);
      token = resolvedCreds?.token;
    }

    try {
      const snapshot = await this.doResolve(parsed, normalizedInput, token, options);
      
      // P1-3: Cleanup temp files unless keepTempFiles is true
      if (!options?.keepTempFiles) {
        setImmediate(async () => {
          try {
            await rm(snapshot.localRootPath, { recursive: true, force: true });
          } catch (err) {
            console.warn(`[GitLabProvider] Failed to cleanup temp directory: ${snapshot.localRootPath}`, err);
          }
        });
      }
      
      return snapshot;
    } catch (err) {
      if (err instanceof SourceProviderError) throw err;
      const msg = err instanceof Error ? err.message : String(err);
      throw new SourceProviderError(this.providerId, normalizedInput, msg, err);
    }
  }

  private async doResolve(
    parsed: ParsedGitLabLocator,
    _locator: string,
    token: string | undefined,
    options?: SourceProviderOptions,
  ): Promise<RepositorySnapshot> {

    const timeoutMs = options?.requestTimeoutMs ?? 30_000;
    const maxFileSizeBytes = options?.maxFileSizeBytes ?? 1 * 1024 * 1024; // 1MB default
    const maxFiles = options?.maxFiles ?? 10_000;
    const diagnostics: ProviderDiagnostic[] = [];

    // P1-3: Encode full project path for nested groups
    const encodedProjectPath = encodeURIComponent(parsed.projectPath);

    // Resolve the commit SHA with retry/backoff
    const commitRef = await gitlabApiFetch<GitLabCommitResponse>(
      this.apiHost,
      `/projects/${encodedProjectPath}/repository/commits/${parsed.ref}`,
      token,
      timeoutMs,
      DEFAULT_RETRY_CONFIG,
      diagnostics,
    );
    const commitSha = commitRef.id;

    // P1-3: Deterministic temp directory path using commit SHA
    const tempRoot = join(
      options?.tempDir ?? tmpdir(),
      `yappc-gitlab-${parsed.projectPath.replace(/\//g, '-')}-${commitSha.slice(0, 8)}`,
    );
    await mkdir(tempRoot, { recursive: true });

    // Get repository tree (paginated) with retry/backoff
    const files: SnapshotFile[] = [];
    let fileCount = 0;
    let page = 1;
    const perPage = 100;

    while (true) {
      if (fileCount >= maxFiles) {
        diagnostics.push({
          level: 'warning',
          code: 'GITLAB_MAX_FILES_REACHED',
          message: `GitLab snapshot stopped after reaching maxFiles=${maxFiles}.`,
          timestamp: new Date().toISOString(),
          metadata: { maxFiles },
        });
        break;
      }

      const treeResponse = await gitlabApiFetch<GitLabTreeEntry[]>(
        this.apiHost,
        `/projects/${encodedProjectPath}/repository/tree?ref=${commitSha}&per_page=${perPage}&page=${page}&recursive=true`,
        token,
        timeoutMs,
        DEFAULT_RETRY_CONFIG,
        diagnostics,
      );

      if (treeResponse.length === 0) break;

      for (const entry of treeResponse) {
        if (fileCount >= maxFiles) break;
        if (entry.type !== 'blob') continue;

        // Get file metadata to check size with retry/backoff
        try {
          const fileMeta = await gitlabApiFetch<GitLabFileResponse>(
            this.apiHost,
            `/projects/${encodedProjectPath}/repository/files/${encodeURIComponent(entry.path)}?ref=${commitSha}`,
            token,
            timeoutMs,
            DEFAULT_RETRY_CONFIG,
            diagnostics,
          );

          if (fileMeta.size > maxFileSizeBytes) {
            files.push({
              relativePath: entry.path,
              materialized: false,
              sizeBytes: fileMeta.size,
              lastModifiedAt: new Date().toISOString(),
            });
            diagnostics.push({
              level: 'warning',
              code: 'GITLAB_FILE_SKIPPED_MAX_SIZE',
              message: `Skipped oversized GitLab file ${entry.path}.`,
              timestamp: new Date().toISOString(),
              resourcePath: entry.path,
              metadata: {
                sizeBytes: fileMeta.size,
                maxFileSizeBytes,
              },
            });
            continue;
          }

          // Materialize the file
          const content = Buffer.from(fileMeta.content, 'base64');

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
        } catch (err) {
          // Non-fatal: record without materialization
          files.push({
            relativePath: entry.path,
            materialized: false,
            sizeBytes: 0,
            lastModifiedAt: new Date().toISOString(),
          });
          diagnostics.push({
            level: 'warning',
            code: 'GITLAB_FILE_MATERIALIZATION_FAILED',
            message: `Failed to materialize GitLab file ${entry.path}; keeping metadata-only snapshot entry.`,
            timestamp: new Date().toISOString(),
            resourcePath: entry.path,
            metadata: {
              error: err instanceof Error ? err.message : String(err),
            },
          });
        }
      }

      page++;
      // GitLab API returns empty array when no more results
      if (treeResponse.length < perPage) break;
    }

    // P1-3: Deterministic snapshot metadata using commit SHA and full project path
    const snapshotRef: SnapshotRef = {
      provider: 'gitlab',
      repoId: `gitlab.com/${parsed.projectPath}`,
      commitSha,
      branch: parsed.ref !== 'HEAD' ? parsed.ref : undefined,
    };

    return {
      snapshotRef,
      localRootPath: tempRoot,
      files,
      snapshotAt: new Date().toISOString(),
      shallow: false,
      diagnostics,
    };
  }
}
