/**
 * ArtifactProvider - interface for storing and retrieving build artifacts.
 *
 * @doc.type interface
 * @doc.purpose Artifact provider interface for artifact storage
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

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
