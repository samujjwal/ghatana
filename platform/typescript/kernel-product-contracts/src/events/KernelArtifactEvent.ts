/**
 * KernelArtifactEvent - event contract for artifact production events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for artifact production operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import type { KernelEventMetadata } from "./KernelLifecycleEvent.js";

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
