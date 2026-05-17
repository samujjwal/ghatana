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
 * Artifact trust state — indicates the verification status of a produced artifact.
 */
export type ArtifactTrustState =
  | 'unverified'
  | 'verified'
  | 'signed'
  | 'attested'
  | 'policy-compliant'
  | 'policy-rejected';

/**
 * Artifact signature — a cryptographic signature or signed reference for an artifact.
 */
export interface ArtifactSignature {
  readonly algorithm: 'cosign' | 'gpg' | 'sigstore' | 'custom';
  readonly keyId?: string;
  readonly signedAt: string;
  readonly sigRef?: string;
  readonly verifiedAt?: string;
}

/**
 * Reference to a Software Bill of Materials (SBOM) associated with an artifact.
 */
export interface ArtifactSbomRef {
  readonly format: 'cyclonedx' | 'spdx' | 'custom';
  readonly version: string;
  readonly ref: string;
  readonly generatedAt: string;
}

/**
 * Artifact attestation — an in-toto or custom attestation produced for an artifact.
 */
export interface ArtifactAttestation {
  readonly predicateType: string;
  readonly ref: string;
  readonly attestedAt: string;
  readonly attestedBy?: string;
}

/**
 * Artifact retention policy — how long this artifact must be retained and where.
 */
export interface ArtifactRetentionPolicy {
  readonly retainUntil?: string;
  readonly storageClass?: 'hot' | 'cold' | 'archive';
  readonly deleteAfterDays?: number;
  readonly reason?: string;
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
  trustState?: ArtifactTrustState;
  signature?: ArtifactSignature;
  sbomRef?: ArtifactSbomRef;
  attestation?: ArtifactAttestation;
  retention?: ArtifactRetentionPolicy;
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
  policyCompliant?: boolean;
  policyViolations?: string[];
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
  provenanceRef?: string;
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

const ArtifactSignatureSchema = z.object({
  algorithm: z.enum(['cosign', 'gpg', 'sigstore', 'custom']),
  keyId: z.string().min(1).optional(),
  signedAt: z.string().datetime({ offset: true }),
  sigRef: z.string().min(1).optional(),
  verifiedAt: z.string().datetime({ offset: true }).optional(),
});

const ArtifactSbomRefSchema = z.object({
  format: z.enum(['cyclonedx', 'spdx', 'custom']),
  version: z.string().min(1),
  ref: z.string().min(1),
  generatedAt: z.string().datetime({ offset: true }),
});

const ArtifactAttestationSchema = z.object({
  predicateType: z.string().min(1),
  ref: z.string().min(1),
  attestedAt: z.string().datetime({ offset: true }),
  attestedBy: z.string().min(1).optional(),
});

const ArtifactRetentionPolicySchema = z.object({
  retainUntil: z.string().datetime({ offset: true }).optional(),
  storageClass: z.enum(['hot', 'cold', 'archive']).optional(),
  deleteAfterDays: z.number().int().positive().optional(),
  reason: z.string().min(1).optional(),
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
  provenanceRef: z.string().min(1).optional(),
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
        trustState: z.enum(['unverified', 'verified', 'signed', 'attested', 'policy-compliant', 'policy-rejected']).optional(),
        signature: ArtifactSignatureSchema.optional(),
        sbomRef: ArtifactSbomRefSchema.optional(),
        attestation: ArtifactAttestationSchema.optional(),
        retention: ArtifactRetentionPolicySchema.optional(),
      }),
      fingerprint: z.object({
        algorithm: z.enum(['sha256', 'sha512']),
        hash: z.string().min(1),
      }),
      expected: z.boolean(),
      found: z.boolean(),
      policyCompliant: z.boolean().optional(),
      policyViolations: z.array(z.string().min(1)).optional(),
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
    provenanceRef?: string;
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
      ...(params.provenanceRef !== undefined ? { provenanceRef: params.provenanceRef } : {}),
      ...(params.generatedBy !== undefined ? { generatedBy: params.generatedBy } : {}),
      timestamp: new Date().toISOString(),
      artifacts: params.artifacts.map((artifact) => ({
        ...artifact,
        metadata: {
          ...artifact.metadata,
          buildNumber: artifact.metadata.buildNumber || '0',
        },
        found: false,
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
