/**
 * K-010: Generated artifact cleanup mode.
 * Generators delete stale route/page/API artifacts instead of retaining legacy files.
 */

export type CleanupArtifactType = 'route' | 'page' | 'api' | 'test' | 'i18n' | 'a11y';

export type ArtifactCleanupMode = 'preserve-legacy' | 'delete-stale' | 'delete-all';

export type CleanupArtifactMetadata = {
  path: string;
  type: CleanupArtifactType;
  generatedAt: string;
  version: string;
  sourceContract: string;
};

export type ArtifactCleanupPolicy = {
  mode: ArtifactCleanupMode;
  staleThresholdDays: number;
  protectedPaths: string[];
  backupBeforeDelete: boolean;
  dryRun: boolean;
};

export type ArtifactCleanupResult = {
  deleted: CleanupArtifactMetadata[];
  preserved: CleanupArtifactMetadata[];
  errors: Array<{ path: string; reason: string }>;
  summary: {
    totalProcessed: number;
    totalDeleted: number;
    totalPreserved: number;
    totalErrors: number;
  };
};

export function createArtifactCleanupPolicy(
  mode: ArtifactCleanupMode = 'delete-stale',
  options?: Partial<Omit<ArtifactCleanupPolicy, 'mode'>>
): ArtifactCleanupPolicy {
  return {
    mode,
    staleThresholdDays: options?.staleThresholdDays ?? 30,
    protectedPaths: options?.protectedPaths ?? [],
    backupBeforeDelete: options?.backupBeforeDelete ?? true,
    dryRun: options?.dryRun ?? false,
  };
}

export function isArtifactStale(
  artifact: CleanupArtifactMetadata,
  policy: ArtifactCleanupPolicy
): boolean {
  const generatedDate = new Date(artifact.generatedAt);
  const staleDate = new Date();
  staleDate.setDate(staleDate.getDate() - policy.staleThresholdDays);
  
  return generatedDate < staleDate;
}

export function isArtifactProtected(
  artifact: CleanupArtifactMetadata,
  policy: ArtifactCleanupPolicy
): boolean {
  return policy.protectedPaths.some(protectedPath => 
    artifact.path.startsWith(protectedPath)
  );
}

export function shouldDeleteArtifact(
  artifact: CleanupArtifactMetadata,
  policy: ArtifactCleanupPolicy
): boolean {
  if (policy.mode === 'preserve-legacy') return false;
  if (policy.mode === 'delete-all') return true;
  
  // delete-stale mode
  if (isArtifactProtected(artifact, policy)) return false;
  return isArtifactStale(artifact, policy);
}

export function createArtifactCleanupResult(
  deleted: CleanupArtifactMetadata[],
  preserved: CleanupArtifactMetadata[],
  errors: Array<{ path: string; reason: string }>
): ArtifactCleanupResult {
  return {
    deleted,
    preserved,
    errors,
    summary: {
      totalProcessed: deleted.length + preserved.length + errors.length,
      totalDeleted: deleted.length,
      totalPreserved: preserved.length,
      totalErrors: errors.length,
    },
  };
}
