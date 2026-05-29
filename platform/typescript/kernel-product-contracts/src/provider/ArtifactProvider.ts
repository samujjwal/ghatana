/**
 * ArtifactProvider - interface for storing and retrieving build artifacts.
 *
 * @doc.type interface
 * @doc.purpose Artifact provider interface for artifact storage
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import { z } from "zod";
import type { KernelProvider } from "./KernelProvider.js";

/**
 * Artifact metadata.
 */
export interface ArtifactMetadata {
  readonly artifactId: string;
  readonly name: string;
  readonly version: string;
  readonly type: string;
  readonly size: number;
  readonly checksum: string;
  readonly createdAt: string;
  readonly tags: readonly string[];
}

export const ArtifactMetadataSchema = z
  .object({
    artifactId: z.string().trim().min(1),
    name: z.string().trim().min(1),
    version: z.string().trim().min(1),
    type: z.string().trim().min(1),
    size: z.number().int().nonnegative(),
    checksum: z.string().trim().min(1),
    createdAt: z.string().datetime({ offset: true }),
    tags: z.array(z.string().trim().min(1)),
  })
  .strict();

/**
 * Artifact provider for storing and retrieving build artifacts.
 */
export interface ArtifactProvider extends KernelProvider {
  /**
   * Stores an artifact.
   */
  storeArtifact(
    artifactId: string,
    content: Buffer | Uint8Array,
    metadata: ArtifactMetadata
  ): Promise<void>;

  /**
   * Retrieves an artifact.
   */
  retrieveArtifact(artifactId: string): Promise<Buffer | Uint8Array | null>;

  /**
   * Gets artifact metadata.
   */
  getArtifactMetadata(artifactId: string): Promise<ArtifactMetadata | null>;

  /**
   * Lists artifacts by tags.
   */
  listArtifactsByTags(tags: readonly string[]): Promise<readonly ArtifactMetadata[]>;
}

export const ArtifactProviderSchema = z.custom<ArtifactProvider>(
  (value) => {
    if (typeof value !== "object" || value === null) {
      return false;
    }
    const provider = value as Record<string, unknown>;
    return (
      typeof provider.storeArtifact === "function" &&
      typeof provider.retrieveArtifact === "function" &&
      typeof provider.getArtifactMetadata === "function" &&
      typeof provider.listArtifactsByTags === "function"
    );
  },
  "ArtifactProvider requires artifact storage functions"
);

export function validateArtifactMetadata(
  value: unknown
): value is ArtifactMetadata {
  return ArtifactMetadataSchema.safeParse(value).success;
}

export function validateArtifactProvider(
  value: unknown
): value is ArtifactProvider {
  return ArtifactProviderSchema.safeParse(value).success;
}
