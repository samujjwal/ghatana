/**
 * K-010: Generated artifact cleanup mode.
 * Generators delete stale route/page/API artifacts instead of retaining legacy files.
 */

import { z } from "zod";

export const CleanupArtifactTypeSchema = z.enum(['route', 'page', 'api', 'test', 'i18n', 'a11y']);
export const ArtifactCleanupModeSchema = z.enum(['preserve-legacy', 'delete-stale', 'delete-all']);

export const CleanupArtifactMetadataSchema = z
  .object({
    path: z.string().trim().min(1),
    type: CleanupArtifactTypeSchema,
    generatedAt: z.string().trim().datetime(),
    version: z.string().trim().min(1),
    sourceContract: z.string().trim().min(1),
  })
  .strict();

export const ArtifactCleanupPolicySchema = z
  .object({
    mode: ArtifactCleanupModeSchema,
    staleThresholdDays: z.number().int().nonnegative(),
    protectedPaths: z.array(z.string().trim().min(1)),
    backupBeforeDelete: z.boolean(),
    dryRun: z.boolean(),
  })
  .strict();

const ArtifactCleanupErrorSchema = z
  .object({
    path: z.string().trim().min(1),
    reason: z.string().trim().min(1),
  })
  .strict();

export const ArtifactCleanupResultSchema = z
  .object({
    deleted: z.array(CleanupArtifactMetadataSchema),
    preserved: z.array(CleanupArtifactMetadataSchema),
    errors: z.array(ArtifactCleanupErrorSchema),
    summary: z
      .object({
        totalProcessed: z.number().int().nonnegative(),
        totalDeleted: z.number().int().nonnegative(),
        totalPreserved: z.number().int().nonnegative(),
        totalErrors: z.number().int().nonnegative(),
      })
      .strict(),
  })
  .strict();

export type CleanupArtifactType = z.infer<typeof CleanupArtifactTypeSchema>;
export type ArtifactCleanupMode = z.infer<typeof ArtifactCleanupModeSchema>;
export type CleanupArtifactMetadata = z.infer<typeof CleanupArtifactMetadataSchema>;
export type ArtifactCleanupPolicy = z.infer<typeof ArtifactCleanupPolicySchema>;
export type ArtifactCleanupResult = z.infer<typeof ArtifactCleanupResultSchema>;

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

export function validateCleanupArtifactType(value: unknown): value is CleanupArtifactType {
  return CleanupArtifactTypeSchema.safeParse(value).success;
}

export function validateArtifactCleanupMode(value: unknown): value is ArtifactCleanupMode {
  return ArtifactCleanupModeSchema.safeParse(value).success;
}

export function validateCleanupArtifactMetadata(value: unknown): value is CleanupArtifactMetadata {
  return CleanupArtifactMetadataSchema.safeParse(value).success;
}

export function validateArtifactCleanupPolicy(value: unknown): value is ArtifactCleanupPolicy {
  return ArtifactCleanupPolicySchema.safeParse(value).success;
}

export function validateArtifactCleanupResult(value: unknown): value is ArtifactCleanupResult {
  return ArtifactCleanupResultSchema.safeParse(value).success;
}
