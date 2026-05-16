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
});

export type RepositorySnapshot = z.infer<typeof RepositorySnapshotSchema>;

// ============================================================================
// Provider Options
// ============================================================================

export interface ProviderCredentials {
  readonly token?: string;
  readonly username?: string;
  readonly password?: string;
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
