import { z } from 'zod';

/**
 * Artifact types
 */
export type ArtifactType =
  | 'jar'
  | 'war'
  | 'static-web-bundle'
  | 'docker-image'
  | 'npm-package'
  | 'test-report'
  | 'coverage-report'
  | 'source-map'
  | 'documentation';

/**
 * Artifact fingerprint
 */
export interface ArtifactFingerprint {
  algorithm: 'sha256' | 'sha512' | 'md5';
  hash: string;
}

/**
 * Artifact metadata
 */
export interface ArtifactMetadata {
  type: ArtifactType;
  version: string;
  buildNumber: string;
  gitCommit: string | undefined;
  gitBranch: string | undefined;
  timestamp: string;
  sizeBytes: number;
}

/**
 * Artifact entry
 */
export interface ArtifactEntry {
  id: string;
  path: string;
  metadata: ArtifactMetadata;
  fingerprint: ArtifactFingerprint;
  expected: boolean;
  found: boolean;
}

/**
 * Artifact entry input (for creation, buildNumber optional)
 */
export interface ArtifactEntryInput {
  id: string;
  path: string;
  metadata: Omit<ArtifactMetadata, 'buildNumber'> & { buildNumber?: string };
  fingerprint: ArtifactFingerprint;
  expected: boolean;
}

/**
 * Artifact manifest
 */
export interface ArtifactManifest {
  schemaVersion: string;
  productId: string;
  phase: string;
  surface: string;
  timestamp: string;
  artifacts: ArtifactEntry[];
}

/**
 * Zod schema for artifact manifest validation
 */
export const ArtifactManifestSchema = z.object({
  schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
  productId: z.string().min(1),
  phase: z.string().min(1),
  surface: z.string().min(1),
  timestamp: z.string().datetime(),
  artifacts: z.array(
    z.object({
      id: z.string().min(1),
      path: z.string().min(1),
      metadata: z.object({
        type: z.enum(['jar', 'war', 'static-web-bundle', 'docker-image', 'npm-package', 'test-report', 'coverage-report', 'source-map', 'documentation']),
        version: z.string().min(1),
        buildNumber: z.string(),
        gitCommit: z.string().optional(),
        gitBranch: z.string().optional(),
        timestamp: z.string().datetime(),
        sizeBytes: z.number().int().nonnegative(),
      }),
      fingerprint: z.object({
        algorithm: z.enum(['sha256', 'sha512', 'md5']),
        hash: z.string().min(1),
      }),
      expected: z.boolean(),
      found: z.boolean(),
    }),
  ),
});

export type ArtifactManifestInput = z.infer<typeof ArtifactManifestSchema>;

/**
 * Artifact manifest generator
 */
export class ArtifactManifestGenerator {
  /**
   * Create a new artifact manifest
   */
  createManifest(params: {
    productId: string;
    phase: string;
    surface: string;
    artifacts: ArtifactEntryInput[];
  }): ArtifactManifest {
    return {
      schemaVersion: '1.0.0',
      productId: params.productId,
      phase: params.phase,
      surface: params.surface,
      timestamp: new Date().toISOString(),
      artifacts: params.artifacts.map((artifact) => ({
        ...artifact,
        metadata: {
          ...artifact.metadata,
          buildNumber: artifact.metadata.buildNumber || '0',
        },
        found: false, // Will be updated after validation
      })),
    };
  }

  /**
   * Validate an artifact manifest
   */
  validateManifest(manifest: unknown): ArtifactManifestInput {
    return ArtifactManifestSchema.parse(manifest);
  }

  /**
   * Check if all expected artifacts are found
   */
  validateArtifacts(manifest: ArtifactManifestInput): {
    valid: boolean;
    missing: ArtifactEntry[];
    unexpected: ArtifactEntry[];
  } {
    const missing = manifest.artifacts.filter((a) => a.expected && !a.found);
    const unexpected = manifest.artifacts.filter((a) => !a.expected && a.found);

    return {
      valid: missing.length === 0 && unexpected.length === 0,
      missing: missing as ArtifactEntry[],
      unexpected: unexpected as ArtifactEntry[],
    };
  }
}
