/**
 * @fileoverview Source Provider abstraction for resolving repository snapshots.
 *
 * The Source Acquisition Layer decouples the scanner/extractor pipeline from the
 * specific mechanism used to obtain source files. Providers must be able to:
 *  - Resolve a locator into a RepositorySnapshot (tree of readable files).
 *  - Report a SnapshotRef stable enough for deterministic ID generation.
 *  - Stream file content without full materialization when possible.
 *
 * Concrete providers: LocalFolder, GitHub, GitLab, ZipArchive, ArtifactRegistry.
 */

import { z } from 'zod';
import type { SnapshotRef } from '../graph/types';
import { SnapshotRefSchema } from '../graph/types';

// ============================================================================
// Provider Diagnostic
// ============================================================================

/**
 * Provider diagnostic event for observability and debugging.
 * Includes structured information without exposing secrets.
 */
export const ProviderDiagnosticSchema = z.object({
  level: z.enum(['info', 'warning', 'error']),
  code: z.string().optional(),
  message: z.string(),
  /** Timestamp of the diagnostic event */
  timestamp: z.string().datetime(),
  /** Related file or resource path (sanitized) */
  resourcePath: z.string().optional(),
  /** Provider-specific metadata (never includes credentials) */
  metadata: z.record(z.string(), z.unknown()).optional(),
});

export type ProviderDiagnostic = z.infer<typeof ProviderDiagnosticSchema>;

// ============================================================================
// Repository Snapshot — virtual file tree returned by a provider
// ============================================================================

export const SnapshotFileSchema = z.object({
  /** Normalized relative path (forward slashes, no leading ./). */
  relativePath: z.string().min(1),
  /** Absolute path on the local filesystem — available after materialization. */
  absolutePath: z.string().optional(),
  /** True when the file has been materialized to disk (absolutePath is valid). */
  materialized: z.boolean().default(false),
  sizeBytes: z.number().int().nonnegative(),
  /** ISO-8601 last-modified timestamp. */
  lastModifiedAt: z.string().datetime(),
});

export type SnapshotFile = z.infer<typeof SnapshotFileSchema>;

export const RepositorySnapshotSchema = z.object({
  snapshotRef: SnapshotRefSchema,
  /** Absolute local path where files have been materialized. */
  localRootPath: z.string().min(1),
  files: z.array(SnapshotFileSchema),
  snapshotAt: z.string().datetime(),
  /** True when the snapshot is a shallow clone (commit history may be absent). */
  shallow: z.boolean().default(false),
  /**
   * Provider-specific diagnostics (warnings, errors, skipped files, etc.).
   * Useful for observability and debugging.
   */
  diagnostics: z.array(ProviderDiagnosticSchema).default([]),
});

export type RepositorySnapshot = z.infer<typeof RepositorySnapshotSchema>;

// ============================================================================
// Provider Options
// ============================================================================

export interface ProviderCredentials {
  readonly token?: string;
  readonly username?: string;
  readonly password?: string;
  /**
   * Reference to stored credential (e.g., from a secrets manager).
   * When present, the provider should resolve the actual credentials from this reference.
   */
  readonly credentialRef?: string;
}

// ============================================================================
// Source Locator Schema
// ============================================================================

/**
 * Typed source locator for governed source acquisition.
 * Replaces raw string locators with a validated, typed structure.
 */
export const SourceLocatorSchema = z.object({
  /** Provider type (must match SnapshotRef.provider) */
  provider: z.enum(['local-folder', 'github', 'gitlab', 'zip', 'artifact-registry']),
  /** Repository or archive identifier */
  repoId: z.string().min(1),
  /** Commit SHA, branch, or tag reference */
  ref: z.string().optional(),
  /** Specific path within the repo/archive (optional) */
  path: z.string().optional(),
  /** Reference to stored credential (never raw token) */
  credentialRef: z.string().optional(),
});

export type SourceLocator = z.infer<typeof SourceLocatorSchema>;

/**
 * Parse a string locator into a typed SourceLocator.
 * Supports formats:
 *   - "owner/repo" -> { provider: 'github', repoId: 'owner/repo' }
 *   - "owner/repo@branch" -> { provider: 'github', repoId: 'owner/repo', ref: 'branch' }
 *   - "/absolute/path" -> { provider: 'local-folder', repoId: '/absolute/path' }
 *   - "https://github.com/owner/repo" -> { provider: 'github', repoId: 'owner/repo' }
 */
export function parseSourceLocator(locator: string): SourceLocator {
  // GitHub URL
  const githubMatch = /github\.com\/([^/]+)\/([^/?#]+)(?:\/tree\/([^/?#]+))?/.exec(locator);
  if (githubMatch) {
    return {
      provider: 'github',
      repoId: `${githubMatch[1]}/${githubMatch[2]!.replace(/\.git$/, '')}`,
      ref: githubMatch[3],
    };
  }

  // GitLab URL
  const gitlabMatch = /gitlab\.com\/([^/]+)\/([^/?#]+)(?:\/-\/tree\/([^/?#]+))?/.exec(locator);
  if (gitlabMatch) {
    return {
      provider: 'gitlab',
      repoId: `${gitlabMatch[1]}/${gitlabMatch[2]!.replace(/\.git$/, '')}`,
      ref: gitlabMatch[3],
    };
  }

  // Slug format: "owner/repo" or "owner/repo@ref"
  const slugMatch = /^([\w.-]+)\/([\w.-]+)(?:@(.+))?$/.exec(locator);
  if (slugMatch) {
    // Default to github for slug format (can be overridden)
    return {
      provider: 'github',
      repoId: `${slugMatch[1]}/${slugMatch[2]}`,
      ref: slugMatch[3],
    };
  }

  // Absolute/relative path
  if (locator.startsWith('/') || locator.startsWith('./') || locator.startsWith('../')) {
    return {
      provider: 'local-folder',
      repoId: locator,
    };
  }

  // ZIP file
  if (locator.toLowerCase().endsWith('.zip')) {
    return {
      provider: 'zip',
      repoId: locator,
    };
  }

  // Fallback: treat as local folder
  return {
    provider: 'local-folder',
    repoId: locator,
  };
}

// ============================================================================
// Source Scope Context
// ============================================================================

/**
 * Scope context for multi-tenant source acquisition.
 * Ensures source operations are properly scoped and authorized.
 */
export interface SourceScopeContext {
  /** Tenant identifier */
  tenantId: string;
  /** Workspace identifier (optional) */
  workspaceId?: string;
  /** Project identifier (optional) */
  projectId?: string;
  /** User principal who initiated the operation */
  principalId: string;
  /** Timestamp when the scope was granted */
  grantedAt: string;
}

export interface SourceProviderOptions {
  /**
   * Maximum number of files to materialize. Providers skip files beyond this
   * limit and record them as skipped in the snapshot summary.
   */
  readonly maxFiles?: number;
  /**
   * Maximum individual file size in bytes to materialize.
   * Larger files are recorded but not downloaded.
   */
  readonly maxFileSizeBytes?: number;
  readonly credentials?: ProviderCredentials;
  /**
   * Directory to use for temporary materialization.
   * Providers clean up temp files after the scan completes unless keepTempFiles is true.
   */
  readonly tempDir?: string;
  readonly keepTempFiles?: boolean;
  /**
   * Timeout in milliseconds for remote requests (e.g. GitHub API calls).
   */
  readonly requestTimeoutMs?: number;
}

// ============================================================================
// Source Provider — the core abstraction
// ============================================================================

/**
 * A SourceProvider knows how to turn a locator string into a RepositorySnapshot.
 *
 * Implementations must be stateless: the same locator + options must always
 * resolve to a snapshot with the same snapshotRef (content-addressable if possible).
 */
export interface SourceProvider {
  /**
   * Human-readable provider ID (e.g. "local-folder", "github", "gitlab", "zip").
   * Must match the SnapshotRef.provider enum.
   */
  readonly providerId: SnapshotRef['provider'];

  /**
   * Resolve and materialize a repository snapshot from the given locator.
   *
   * @param locator - Provider-specific locator:
   *   - local-folder: absolute or relative filesystem path
   *   - github: "owner/repo[@ref]" or a full GitHub URL
   *   - gitlab: "owner/repo[@ref]" or a full GitLab URL
   *   - zip: absolute path or URL to a .zip archive
   *   - artifact-registry: "urn:<registry>/<artifact>@<version>"
   */
  resolve(locator: string, options?: SourceProviderOptions): Promise<RepositorySnapshot>;

  /**
   * Quickly determine whether this provider can handle a given locator
   * without fetching any remote resources.
   */
  canHandle(locator: string): boolean;
}

// ============================================================================
// Provider Resolution Error
// ============================================================================

export class SourceProviderError extends Error {
  constructor(
    public readonly providerId: string,
    public readonly locator: string,
    message: string,
    public readonly cause?: unknown,
  ) {
    super(`[${providerId}] Failed to resolve "${locator}": ${message}`);
    this.name = 'SourceProviderError';
  }
}

// ============================================================================
// Provider Registry
// ============================================================================

/**
 * Registry that maps provider IDs to their implementations.
 * Resolves a locator by trying each registered provider in order.
 */
export class SourceProviderRegistry {
  private readonly providers = new Map<string, SourceProvider>();

  register(provider: SourceProvider): void {
    this.providers.set(provider.providerId, provider);
  }

  get(providerId: string): SourceProvider | undefined {
    return this.providers.get(providerId);
  }

  /**
   * Resolve a locator by trying each registered provider in insertion order.
   * Returns the first snapshot from a provider that accepts the locator.
   * Throws SourceProviderError if no provider can handle the locator.
   */
  async resolve(locator: string, options?: SourceProviderOptions): Promise<RepositorySnapshot> {
    for (const provider of this.providers.values()) {
      if (provider.canHandle(locator)) {
        return provider.resolve(locator, options);
      }
    }
    throw new SourceProviderError(
      'registry',
      locator,
      `No registered provider can handle this locator. Registered: [${[...this.providers.keys()].join(', ')}]`,
    );
  }

  listProviders(): readonly SnapshotRef['provider'][] {
    return [...this.providers.keys()] as SnapshotRef['provider'][];
  }
}
