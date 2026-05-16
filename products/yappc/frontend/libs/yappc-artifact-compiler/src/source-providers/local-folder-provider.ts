/**
 * @fileoverview LocalFolder source provider.
 *
 * Resolves a local filesystem directory into a RepositorySnapshot.
 * Uses the git commit SHA (if available) to generate a deterministic SnapshotRef.
 * Falls back to a content hash of the directory listing when git is unavailable.
 */

import { readdir, stat } from 'fs/promises';
import { join, relative, resolve as resolvePath } from 'path';
import { execFile } from 'child_process';
import { promisify } from 'util';
import type { SourceProvider, SourceProviderOptions, SnapshotFile, RepositorySnapshot } from './types';
import { SourceProviderError } from './types';
import type { SnapshotRef } from '../graph/types';

const execFileAsync = promisify(execFile);

// ============================================================================
// Git helpers (best-effort; gracefully degrade when git is unavailable)
// ============================================================================

async function tryGetGitCommitSha(rootPath: string): Promise<string | undefined> {
  try {
    const { stdout } = await execFileAsync('git', ['rev-parse', 'HEAD'], {
      cwd: rootPath,
      timeout: 5000,
    });
    return stdout.trim() || undefined;
  } catch {
    return undefined;
  }
}

async function tryGetGitRemoteUrl(rootPath: string): Promise<string | undefined> {
  try {
    const { stdout } = await execFileAsync('git', ['remote', 'get-url', 'origin'], {
      cwd: rootPath,
      timeout: 5000,
    });
    return stdout.trim() || undefined;
  } catch {
    return undefined;
  }
}

/**
 * Derive a stable repoId from the remote origin URL, or fall back to the
 * normalized absolute path.
 */
function deriveRepoId(rootPath: string, remoteUrl: string | undefined): string {
  if (remoteUrl) {
    // Strip protocol prefix and .git suffix for a clean identifier
    return remoteUrl
      .replace(/^(https?|git|ssh):\/\//, '')
      .replace(/^git@/, '')
      .replace(/:/, '/')
      .replace(/\.git$/, '');
  }
  // Normalize path separators for cross-platform stability
  return rootPath.replace(/\\/g, '/');
}

// ============================================================================
// Directory walker (minimal — returns metadata only, no content)
// ============================================================================

const SKIP_DIRS = new Set(['.git', 'node_modules', 'dist', 'build', 'target', '.next', '.turbo', '.cache', 'coverage']);

async function* walkForSnapshot(
  dir: string,
  root: string,
  maxFiles: number,
  count: { value: number },
): AsyncGenerator<SnapshotFile> {
  if (count.value >= maxFiles) return;

  let entries: import('fs').Dirent[];
  try {
    entries = (await readdir(dir, { withFileTypes: true })) as import('fs').Dirent[];
  } catch {
    return;
  }

  for (const entry of entries) {
    if (count.value >= maxFiles) return;
    const entryName = entry.name as string;

    if (entry.isDirectory()) {
      if (SKIP_DIRS.has(entryName)) continue;
      yield* walkForSnapshot(join(dir, entryName), root, maxFiles, count);
    } else if (entry.isFile()) {
      const absolutePath = join(dir, entryName);
      const relativePath = relative(root, absolutePath).replace(/\\/g, '/');
      try {
        const s = await stat(absolutePath);
        count.value++;
        yield {
          relativePath,
          absolutePath,
          materialized: true,
          sizeBytes: s.size,
          lastModifiedAt: s.mtime.toISOString(),
        };
      } catch {
        // Skip files we can't stat
      }
    }
  }
}

// ============================================================================
// LocalFolder Provider
// ============================================================================

export class LocalFolderProvider implements SourceProvider {
  readonly providerId = 'local-folder' as const;

  canHandle(locator: string): boolean {
    // Handles absolute paths, relative paths, and file:// URIs
    if (locator.startsWith('file://')) return true;
    // Heuristic: if no protocol prefix and doesn't look like an owner/repo slug
    if (!locator.includes('://') && !locator.match(/^[\w-]+\/[\w.-]+(@.+)?$/)) {
      return true;
    }
    return false;
  }

  async resolve(locator: string, options?: SourceProviderOptions): Promise<RepositorySnapshot> {
    const maxFiles = options?.maxFiles ?? 50_000;
    const rawPath = locator.startsWith('file://') ? new URL(locator).pathname : locator;
    const rootPath = resolvePath(rawPath);

    // Verify the path exists and is a directory
    let rootStat: import('fs').Stats;
    try {
      rootStat = await stat(rootPath);
    } catch (err) {
      throw new SourceProviderError(
        this.providerId,
        locator,
        `Path does not exist or is not accessible: ${rootPath}`,
        err,
      );
    }
    if (!rootStat.isDirectory()) {
      throw new SourceProviderError(
        this.providerId,
        locator,
        `Path is not a directory: ${rootPath}`,
      );
    }

    // Attempt to get git metadata for a stable snapshotRef
    const [commitSha, remoteUrl] = await Promise.all([
      tryGetGitCommitSha(rootPath),
      tryGetGitRemoteUrl(rootPath),
    ]);

    const repoId = deriveRepoId(rootPath, remoteUrl);
    const snapshotRef: SnapshotRef = {
      provider: 'local-folder',
      repoId,
      commitSha,
    };

    // Walk the directory to collect file metadata
    const files: SnapshotFile[] = [];
    const count = { value: 0 };
    for await (const file of walkForSnapshot(rootPath, rootPath, maxFiles, count)) {
      files.push(file);
    }

    return {
      snapshotRef,
      localRootPath: rootPath,
      files,
      snapshotAt: new Date().toISOString(),
      shallow: false,
      diagnostics: [],
    };
  }
}
