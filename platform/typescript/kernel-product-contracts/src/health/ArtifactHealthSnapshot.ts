/**
 * ArtifactHealthSnapshot - health snapshot for artifacts.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for artifact operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import { z } from "zod";
import { HealthStatusSchema, type HealthStatus } from "./HealthStatus.js";

/**
 * Artifact health status.
 */
export interface ArtifactHealthStatus {
  readonly artifactId: string;
  readonly artifactName: string;
  readonly version: string;
  readonly status: HealthStatus;
  readonly message: string;
  readonly createdAt: string;
}

export const ArtifactHealthStatusSchema = z
  .object({
    artifactId: z.string().trim().min(1),
    artifactName: z.string().trim().min(1),
    version: z.string().trim().min(1),
    status: HealthStatusSchema,
    message: z.string().trim().min(1),
    createdAt: z.string().datetime({ offset: true }),
  })
  .strict();

/**
 * Artifact health snapshot.
 */
export interface ArtifactHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Lifecycle run identifier.
   */
  readonly runId: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Artifact health statuses.
   */
  readonly artifacts: readonly ArtifactHealthStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}

export const ArtifactHealthSnapshotSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    status: HealthStatusSchema,
    artifacts: z.array(ArtifactHealthStatusSchema),
    snapshotAt: z.string().datetime({ offset: true }),
  })
  .strict();

export function validateArtifactHealthStatus(
  value: unknown
): value is ArtifactHealthStatus {
  return ArtifactHealthStatusSchema.safeParse(value).success;
}

export function validateArtifactHealthSnapshot(
  value: unknown
): value is ArtifactHealthSnapshot {
  return ArtifactHealthSnapshotSchema.safeParse(value).success;
}
