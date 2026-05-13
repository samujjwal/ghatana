/**
 * Product Artifact Contract
 * 
 * Defines the contract for product artifacts and their metadata.
 * Consumed by artifact UI components to display artifact information.
 * 
 * @doc.type module
 * @doc.purpose Product artifact contract for UI components
 * @doc.layer platform
 */

/**
 * Artifact types supported by the platform
 */
export type ArtifactType =
  | 'jar'
  | 'static-web-bundle'
  | 'docker-image'
  | 'mobile-ios-app'
  | 'mobile-android-app';

/**
 * Artifact metadata
 */
export interface ProductArtifact {
  readonly id: string;
  readonly productId: string;
  readonly version: string;
  readonly surface: string;
  readonly type: ArtifactType;
  readonly path: string;
  readonly checksum: string;
  readonly checksumAlgorithm: 'sha256' | 'sha512';
  readonly sizeBytes?: number;
  readonly createdAt: string;
  readonly createdBy: string;
}

/**
 * Artifact manifest containing all artifacts for a product version
 */
export interface ProductArtifactManifest {
  readonly schemaVersion: string;
  readonly productId: string;
  readonly version: string;
  readonly generatedAt: string;
  readonly artifacts: readonly ProductArtifact[];
}

/**
 * Artifact validation result
 */
export interface ArtifactValidationResult {
  readonly valid: boolean;
  readonly errors: readonly ArtifactValidationError[];
}

/**
 * Artifact validation error
 */
export interface ArtifactValidationError {
  readonly artifactId: string;
  readonly error: string;
  readonly severity: 'error' | 'warning';
}
