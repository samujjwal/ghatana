/**
 * KernelArtifactEvent - event contract for artifact production events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for artifact production operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import { z } from "zod";
import type { KernelEventMetadata } from "./KernelLifecycleEvent.js";
import { KernelEventMetadataSchema } from "./KernelLifecycleEvent.js";

/**
 * Artifact event payload.
 */
export interface ArtifactEventPayload {
  /**
   * Artifact identifier.
   */
  readonly artifactId: string;

  /**
   * Artifact name.
   */
  readonly artifactName: string;

  /**
   * Artifact version.
   */
  readonly version: string;

  /**
   * Artifact type (e.g., "docker-image", "jar", "npm-package").
   */
  readonly type: string;

  /**
   * Artifact size in bytes.
   */
  readonly size: number;

  /**
   * Artifact checksum.
   */
  readonly checksum: string;

  /**
   * Surface that produced the artifact.
   */
  readonly surfaceId: string;

  /**
   * Index signature for additional properties.
   */
  readonly [key: string]: unknown;
}

export const ArtifactEventPayloadSchema = z
  .object({
    artifactId: z.string().trim().min(1),
    artifactName: z.string().trim().min(1),
    version: z.string().trim().min(1),
    type: z.string().trim().min(1),
    size: z.number().int().nonnegative(),
    checksum: z.string().trim().min(1),
    surfaceId: z.string().trim().min(1),
  })
  .catchall(z.unknown());

/**
 * Artifact production event.
 */
export interface KernelArtifactEvent {
  /**
   * Event metadata.
   */
  readonly metadata: KernelEventMetadata;

  /**
   * Artifact-specific payload.
   */
  readonly payload: ArtifactEventPayload;
}

export const KernelArtifactEventSchema = z
  .object({
    metadata: KernelEventMetadataSchema,
    payload: ArtifactEventPayloadSchema,
  })
  .strict();

export function validateArtifactEventPayload(
  value: unknown
): value is ArtifactEventPayload {
  return ArtifactEventPayloadSchema.safeParse(value).success;
}

export function validateKernelArtifactEvent(
  value: unknown
): value is KernelArtifactEvent {
  return KernelArtifactEventSchema.safeParse(value).success;
}
