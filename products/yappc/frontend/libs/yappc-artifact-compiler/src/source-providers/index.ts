/**
 * @fileoverview Source Providers barrel export.
 */

export {
  SourceProviderRegistry,
  SourceProviderError,
  SnapshotFileSchema,
  RepositorySnapshotSchema,
} from './types';

export type {
  SourceProvider,
  SourceProviderOptions,
  ProviderCredentials,
  SnapshotFile,
  RepositorySnapshot,
} from './types';

export { LocalFolderProvider } from './local-folder-provider';
export { GitHubProvider } from './github-provider';
export { GitLabProvider } from './gitlab-provider';
export { ZipProvider } from './zip-provider';

// ============================================================================
// Default pre-wired registry (all built-in providers registered)
// ============================================================================

import { SourceProviderRegistry } from './types';
import { LocalFolderProvider } from './local-folder-provider';
import { GitHubProvider } from './github-provider';
import { GitLabProvider } from './gitlab-provider';
import { ZipProvider } from './zip-provider';

/**
 * Creates a new registry with all built-in providers pre-registered.
 * Order matters: local-folder is tried first (most specific match),
 * then zip, then github, then gitlab.
 */
export function createDefaultProviderRegistry(): SourceProviderRegistry {
  const registry = new SourceProviderRegistry();
  registry.register(new LocalFolderProvider());
  registry.register(new ZipProvider());
  registry.register(new GitHubProvider());
  registry.register(new GitLabProvider());
  return registry;
}
