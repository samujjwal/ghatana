import { z } from 'zod';

/**
 * Artifact types
 */
export type ArtifactType =
  | 'jvm-service'
  | 'jvm-library'
  | 'node-service'
  | 'static-web-bundle'
  | 'container-image'
  | 'mobile-bundle'
  | 'sdk-package'
  | 'domain-pack'
  | 'test-report'
  | 'coverage-report'
  | 'source-map'
  | 'documentation';

/**
 * Artifact packaging
 */
export type ArtifactPackaging =
  | 'jar'
  | 'distribution'
  | 'static-files'
  | 'container'
  | 'npm'
  | 'maven'
  | 'apk'
  | 'aab'
  | 'ipa'
  | 'json'
  | 'xml';

/**
 * Artifact fingerprint
 */
export interface ArtifactFingerprint {
  algorithm: 'sha256' | 'sha512';
  hash: string;
}

/**
 * Artifact metadata
 */
export interface ArtifactMetadata {
  type: ArtifactType;
  packaging: ArtifactPackaging;
  version: string;
  buildNumber: string;
  gitCommit: string | undefined;
  gitBranch: string | undefined;
  timestamp: string;
  sizeBytes: number;
  artifactRef?: string;
  deploymentRefs?: ArtifactDeploymentLink[];
}

export interface ArtifactDeploymentLink {
  deploymentId?: string;
  deploymentManifestRef?: string;
  environment?: string;
  promotedFromArtifactRef?: string;
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
  runId?: string;
  correlationId?: string;
  productId: string;
  productUnitId?: string;
  providerMode?: 'bootstrap' | 'platform';
  phase: string;
  surface?: string;
  sourceRef?: string;
  generatedBy?: ArtifactManifestGeneratedBy;
  timestamp: string;
  artifacts: ArtifactEntry[];
}

export interface ArtifactManifestGeneratedBy {
  providerId?: string;
  adapterId?: string;
  toolchainId?: string;
  version?: string;
}

/**
 * Zod schema for artifact manifest validation
 */
const ArtifactDeploymentLinkSchema = z.object({
  deploymentId: z.string().min(1).optional(),
  deploymentManifestRef: z.string().min(1).optional(),
  environment: z.string().min(1).optional(),
  promotedFromArtifactRef: z.string().min(1).optional(),
});

export const ArtifactManifestSchema = z.object({
  schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
  runId: z.string().min(1).optional(),
  correlationId: z.string().min(1).optional(),
  productId: z.string().min(1),
  productUnitId: z.string().min(1).optional(),
  providerMode: z.enum(['bootstrap', 'platform']).optional(),
  phase: z.string().min(1),
  surface: z.string().min(1).optional(),
  sourceRef: z.string().min(1).optional(),
  generatedBy: z.object({
    providerId: z.string().min(1).optional(),
    adapterId: z.string().min(1).optional(),
    toolchainId: z.string().min(1).optional(),
    version: z.string().min(1).optional(),
  }).optional(),
  timestamp: z.string().datetime(),
  artifacts: z.array(
    z.object({
      id: z.string().min(1),
      path: z.string().min(1),
      metadata: z.object({
        type: z.enum(['jvm-service', 'jvm-library', 'node-service', 'static-web-bundle', 'container-image', 'mobile-bundle', 'sdk-package', 'domain-pack', 'test-report', 'coverage-report', 'source-map', 'documentation']),
        packaging: z.enum(['jar', 'distribution', 'static-files', 'container', 'npm', 'maven', 'apk', 'aab', 'ipa', 'json', 'xml']),
        version: z.string().min(1),
        buildNumber: z.string(),
        gitCommit: z.string().optional(),
        gitBranch: z.string().optional(),
        timestamp: z.string().datetime(),
        sizeBytes: z.number().int().nonnegative(),
        artifactRef: z.string().min(1).optional(),
        deploymentRefs: z.array(ArtifactDeploymentLinkSchema).optional(),
      }),
      fingerprint: z.object({
        algorithm: z.enum(['sha256', 'sha512']),
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
    runId?: string;
    correlationId?: string;
    productId: string;
    productUnitId?: string;
    providerMode?: 'bootstrap' | 'platform';
    phase: string;
    surface?: string;
    sourceRef?: string;
    generatedBy?: ArtifactManifestGeneratedBy;
    artifacts: ArtifactEntryInput[];
  }): ArtifactManifest {
    return {
      schemaVersion: '1.0.0',
      ...(params.runId !== undefined ? { runId: params.runId } : {}),
      ...(params.correlationId !== undefined ? { correlationId: params.correlationId } : {}),
      productId: params.productId,
      ...(params.productUnitId !== undefined ? { productUnitId: params.productUnitId } : {}),
      ...(params.providerMode !== undefined ? { providerMode: params.providerMode } : {}),
      phase: params.phase,
      ...(params.surface !== undefined ? { surface: params.surface } : {}),
      ...(params.sourceRef !== undefined ? { sourceRef: params.sourceRef } : {}),
      ...(params.generatedBy !== undefined ? { generatedBy: params.generatedBy } : {}),
      timestamp: new Date().toISOString(),
      artifacts: params.artifacts.map((artifact) => ({
        ...artifact,
        metadata: {
          ...artifact.metadata,
          buildNumber: artifact.metadata.buildNumber || '0',
        },
        found: true,
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
