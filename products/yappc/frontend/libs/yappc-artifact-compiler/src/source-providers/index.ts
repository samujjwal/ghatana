/**
 * @fileoverview Source provider barrel exports.
 *
 * P1-1: Exports CredentialResolver and NoOpCredentialResolver for secure credential resolution.
 */

export { 
  type SourceProvider, 
  SourceProviderRegistry, 
  SourceProviderError, 
  type SourceProviderLocator, 
  sourceLocatorToString, 
  hasRawProviderCredentials, 
  validateCredentialPolicy, 
  type CredentialResolver, 
  NoOpCredentialResolver, 
  type SourceScopeContext, 
  type SourceProviderOptions, 
  type ProviderCredentials, 
  type SourceLocator, 
  parseSourceLocator, 
  type RepositorySnapshot, 
  type SnapshotFile, 
  type ProviderDiagnostic 
} from './types';

export { LocalFolderProvider } from './local-folder-provider';

export { GitHubProvider } from './github-provider';

export { GitLabProvider } from './gitlab-provider';

export { ZipProvider } from './zip-provider';

export { ArchiveProvider } from './archive-provider';

// ============================================================================
// Default pre-wired registry (all built-in providers registered)
// ============================================================================

import { SourceProviderRegistry } from './types';

import { LocalFolderProvider } from './local-folder-provider';

import { GitHubProvider } from './github-provider';

import { GitLabProvider } from './gitlab-provider';

import { ZipProvider } from './zip-provider';

import { ArchiveProvider } from './archive-provider';

/**
 * Creates a new registry with all built-in providers pre-registered.
 * Order matters: local-folder is tried first (most specific match),
 * then archive, zip, github, then gitlab.
 */
export function createDefaultProviderRegistry(): SourceProviderRegistry {
  const registry = new SourceProviderRegistry();
  registry.register(new LocalFolderProvider());
  registry.register(new ArchiveProvider());
  registry.register(new ZipProvider());
  registry.register(new GitHubProvider());
  registry.register(new GitLabProvider());
  return registry;
}
