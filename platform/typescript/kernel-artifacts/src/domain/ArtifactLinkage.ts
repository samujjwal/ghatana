/**
 * ArtifactLinkage - unified trust/source/deployment linkage for artifacts.
 *
 * This module provides contracts for tracking the complete lineage of artifacts
 * from source code through build to deployment, enabling traceability and
 * verification across the entire software supply chain.
 *
 * @doc.type module
 * @doc.purpose Unified artifact trust/source/deployment linkage
 * @doc.layer kernel-artifacts
 * @doc.pattern Contract
 */

import { z } from 'zod';

// ---------------------------------------------------------------------------
// Source Linkage
// ---------------------------------------------------------------------------

/**
 * Source linkage - connects an artifact to its source code origin.
 */
export interface ArtifactSourceLinkage {
  /**
   * Git commit hash that produced this artifact.
   */
  readonly gitCommit: string;

  /**
   * Git branch or tag.
   */
  readonly gitBranch: string;

  /**
   * Git repository URL.
   */
  readonly gitRepository: string;

  /**
   * Source manifest reference (if available).
   */
  readonly sourceManifestRef?: string;

  /**
   * Source code fingerprint at build time.
   */
  readonly sourceFingerprint?: {
    readonly algorithm: 'sha256' | 'sha512';
    readonly hash: string;
  };

  /**
   * Timestamp when source was committed.
   */
  readonly committedAt: string;

  /**
   * Author of the commit.
   */
  readonly author?: string;

  /**
   * Commit message.
   */
  readonly message?: string;
}

/**
 * Zod schema for artifact source linkage.
 */
export const ArtifactSourceLinkageSchema = z.object({
  gitCommit: z.string().min(1).regex(/^[a-f0-9]{40}$/i, 'Invalid git commit format'),
  gitBranch: z.string().min(1),
  gitRepository: z.string().url(),
  sourceManifestRef: z.string().min(1).optional(),
  sourceFingerprint: z.object({
    algorithm: z.enum(['sha256', 'sha512']),
    hash: z.string().min(1).regex(/^[a-f0-9]+$/i, 'Invalid fingerprint hash'),
  }).optional(),
  committedAt: z.string().datetime(),
  author: z.string().min(1).optional(),
  message: z.string().min(1).optional(),
}).strict();

// ---------------------------------------------------------------------------
// Deployment Linkage
// ---------------------------------------------------------------------------

/**
 * Deployment linkage - connects an artifact to its deployment history.
 */
export interface ArtifactDeploymentLinkage {
  /**
   * Deployment ID.
   */
  readonly deploymentId: string;

  /**
   * Environment where artifact was deployed.
   */
  readonly environment: string;

  /**
   * Deployment manifest reference.
   */
  readonly deploymentManifestRef?: string;

  /**
   * Artifact reference used for deployment.
   */
  readonly artifactRef: string;

  /**
   * Previous artifact reference (for rollbacks).
   */
  readonly previousArtifactRef?: string;

  /**
   * Deployment status.
   */
  readonly status: 'pending' | 'deployed' | 'failed' | 'rolled-back';

  /**
   * Timestamp when deployment was initiated.
   */
  readonly deployedAt: string;

  /**
   * Timestamp when deployment completed (if applicable).
   */
  readonly completedAt?: string;

  /**
   * Deployed by (user or system).
   */
  readonly deployedBy?: string;

  /**
   * Deployment metadata.
   */
  readonly metadata?: Record<string, unknown>;
}

/**
 * Zod schema for artifact deployment linkage.
 */
export const ArtifactDeploymentLinkageSchema = z.object({
  deploymentId: z.string().min(1),
  environment: z.string().min(1),
  deploymentManifestRef: z.string().min(1).optional(),
  artifactRef: z.string().min(1),
  previousArtifactRef: z.string().min(1).optional(),
  status: z.enum(['pending', 'deployed', 'failed', 'rolled-back']),
  deployedAt: z.string().datetime(),
  completedAt: z.string().datetime().optional(),
  deployedBy: z.string().min(1).optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
}).strict();

// ---------------------------------------------------------------------------
// Trust Chain
// ---------------------------------------------------------------------------

/**
 * Trust chain - verifies the complete lineage from source to deployment.
 */
export interface ArtifactTrustChain {
  /**
   * Source linkage.
   */
  readonly source: ArtifactSourceLinkage;

  /**
   * Artifact reference.
   */
  readonly artifactRef: string;

  /**
   * Artifact fingerprint.
   */
  readonly artifactFingerprint: {
    readonly algorithm: 'sha256' | 'sha512';
    readonly hash: string;
  };

  /**
   * Trust state.
   */
  readonly trustState: 'unverified' | 'verified' | 'signed' | 'attested' | 'policy-compliant' | 'policy-rejected';

  /**
   * Signature (if signed).
   */
  readonly signature?: {
    readonly algorithm: 'cosign' | 'gpg' | 'sigstore' | 'custom';
    readonly keyId?: string;
    readonly signedAt: string;
    readonly verifiedAt?: string;
  };

  /**
   * Attestation (if attested).
   */
  readonly attestation?: {
    readonly predicateType: string;
    readonly ref: string;
    readonly attestedAt: string;
    readonly attestedBy?: string;
  };

  /**
   * Deployment linkage (if deployed).
   */
  readonly deployment?: ArtifactDeploymentLinkage;
}

/**
 * Zod schema for artifact trust chain.
 */
export const ArtifactTrustChainSchema = z.object({
  source: ArtifactSourceLinkageSchema,
  artifactRef: z.string().min(1),
  artifactFingerprint: z.object({
    algorithm: z.enum(['sha256', 'sha512']),
    hash: z.string().min(1),
  }),
  trustState: z.enum(['unverified', 'verified', 'signed', 'attested', 'policy-compliant', 'policy-rejected']),
  signature: z.object({
    algorithm: z.enum(['cosign', 'gpg', 'sigstore', 'custom']),
    keyId: z.string().min(1).optional(),
    signedAt: z.string().datetime(),
    verifiedAt: z.string().datetime().optional(),
  }).optional(),
  attestation: z.object({
    predicateType: z.string().min(1),
    ref: z.string().min(1),
    attestedAt: z.string().datetime(),
    attestedBy: z.string().min(1).optional(),
  }).optional(),
  deployment: ArtifactDeploymentLinkageSchema.optional(),
}).strict();

// ---------------------------------------------------------------------------
// Linkage Verification
// ---------------------------------------------------------------------------

/**
 * Linkage verification result.
 */
export interface LinkageVerificationResult {
  /**
   * Whether the linkage is valid.
   */
  readonly valid: boolean;

  /**
   * Verification errors.
   */
  readonly errors: readonly string[];

  /**
   * Verification warnings.
   */
  readonly warnings: readonly string[];

  /**
   * Verified chain steps.
   */
  readonly verifiedSteps: readonly string[];
}

/**
 * Linkage verifier - verifies artifact trust/source/deployment linkage.
 */
export class ArtifactLinkageVerifier {
  /**
   * Verify source linkage.
   */
  verifySourceLinkage(linkage: ArtifactSourceLinkage): LinkageVerificationResult {
    const errors: string[] = [];
    const warnings: string[] = [];
    const verifiedSteps: string[] = [];

    // Verify git commit format
    if (!/^[a-f0-9]{40}$/i.test(linkage.gitCommit)) {
      errors.push(`Invalid git commit format: ${linkage.gitCommit}`);
    } else {
      verifiedSteps.push('git-commit-format');
    }

    // Verify git repository URL
    try {
      new URL(linkage.gitRepository);
      verifiedSteps.push('git-repository-url');
    } catch {
      errors.push(`Invalid git repository URL: ${linkage.gitRepository}`);
    }

    // Verify committedAt timestamp
    const committedDate = new Date(linkage.committedAt);
    if (isNaN(committedDate.getTime())) {
      errors.push(`Invalid committedAt timestamp: ${linkage.committedAt}`);
    } else {
      verifiedSteps.push('committed-at-timestamp');
    }

    // Verify source fingerprint if present
    if (linkage.sourceFingerprint) {
      if (linkage.sourceFingerprint.algorithm !== 'sha256' && linkage.sourceFingerprint.algorithm !== 'sha512') {
        errors.push(`Invalid fingerprint algorithm: ${linkage.sourceFingerprint.algorithm}`);
      } else if (!/^[a-f0-9]+$/.test(linkage.sourceFingerprint.hash)) {
        errors.push(`Invalid fingerprint hash: ${linkage.sourceFingerprint.hash}`);
      } else {
        verifiedSteps.push('source-fingerprint');
      }
    }

    return {
      valid: errors.length === 0,
      errors,
      warnings,
      verifiedSteps,
    };
  }

  /**
   * Verify deployment linkage.
   */
  verifyDeploymentLinkage(linkage: ArtifactDeploymentLinkage): LinkageVerificationResult {
    const errors: string[] = [];
    const warnings: string[] = [];
    const verifiedSteps: string[] = [];

    // Verify deploymentId
    if (!linkage.deploymentId || linkage.deploymentId.length === 0) {
      errors.push('Deployment ID is required');
    } else {
      verifiedSteps.push('deployment-id');
    }

    // Verify environment
    if (!linkage.environment || linkage.environment.length === 0) {
      errors.push('Environment is required');
    } else {
      verifiedSteps.push('environment');
    }

    // Verify artifactRef
    if (!linkage.artifactRef || linkage.artifactRef.length === 0) {
      errors.push('Artifact reference is required');
    } else {
      verifiedSteps.push('artifact-ref');
    }

    // Verify deployedAt timestamp
    const deployedDate = new Date(linkage.deployedAt);
    if (isNaN(deployedDate.getTime())) {
      errors.push(`Invalid deployedAt timestamp: ${linkage.deployedAt}`);
    } else {
      verifiedSteps.push('deployed-at-timestamp');
    }

    // Verify completedAt timestamp if present
    if (linkage.completedAt) {
      const completed = new Date(linkage.completedAt);
      const deployed = new Date(linkage.deployedAt);
      if (isNaN(completed.getTime())) {
        errors.push(`Invalid completedAt timestamp: ${linkage.completedAt}`);
      } else if (completed < deployed) {
        errors.push('completedAt must be after deployedAt');
      } else {
        verifiedSteps.push('completed-at-timestamp');
      }
    }

    // Verify previous artifact ref if present
    if (linkage.previousArtifactRef && linkage.previousArtifactRef === linkage.artifactRef) {
      errors.push('previousArtifactRef cannot be the same as artifactRef');
    }

    return {
      valid: errors.length === 0,
      errors,
      warnings,
      verifiedSteps,
    };
  }

  /**
   * Verify trust chain.
   */
  verifyTrustChain(chain: ArtifactTrustChain): LinkageVerificationResult {
    const errors: string[] = [];
    const warnings: string[] = [];
    const verifiedSteps: string[] = [];

    // Verify source linkage
    const sourceResult = this.verifySourceLinkage(chain.source);
    if (!sourceResult.valid) {
      errors.push(...sourceResult.errors);
    }
    warnings.push(...sourceResult.warnings);
    verifiedSteps.push(...sourceResult.verifiedSteps);

    // Verify artifact fingerprint
    if (chain.artifactFingerprint.algorithm !== 'sha256' && chain.artifactFingerprint.algorithm !== 'sha512') {
      errors.push(`Invalid artifact fingerprint algorithm: ${chain.artifactFingerprint.algorithm}`);
    } else if (!/^[a-f0-9]+$/.test(chain.artifactFingerprint.hash)) {
      errors.push(`Invalid artifact fingerprint hash: ${chain.artifactFingerprint.hash}`);
    } else {
      verifiedSteps.push('artifact-fingerprint');
    }

    // Verify trust state
    const validTrustStates = ['unverified', 'verified', 'signed', 'attested', 'policy-compliant', 'policy-rejected'];
    if (!validTrustStates.includes(chain.trustState)) {
      errors.push(`Invalid trust state: ${chain.trustState}`);
    } else {
      verifiedSteps.push('trust-state');
    }

    // Verify signature if present
    if (chain.signature) {
      if (chain.trustState !== 'signed' && chain.trustState !== 'attested' && chain.trustState !== 'policy-compliant') {
        warnings.push('Signature present but trust state is not signed/attested/policy-compliant');
      }
      verifiedSteps.push('signature');
    }

    // Verify attestation if present
    if (chain.attestation) {
      if (chain.trustState !== 'attested' && chain.trustState !== 'policy-compliant') {
        warnings.push('Attestation present but trust state is not attested/policy-compliant');
      }
      verifiedSteps.push('attestation');
    }

    // Verify deployment linkage if present
    if (chain.deployment) {
      const deploymentResult = this.verifyDeploymentLinkage(chain.deployment);
      if (!deploymentResult.valid) {
        errors.push(...deploymentResult.errors);
      }
      warnings.push(...deploymentResult.warnings);
      verifiedSteps.push(...deploymentResult.verifiedSteps);
    }

    return {
      valid: errors.length === 0,
      errors,
      warnings,
      verifiedSteps,
    };
  }

  /**
   * Build trust chain from artifact manifest and deployment linkage.
   */
  buildTrustChain(params: {
    sourceLinkage: ArtifactSourceLinkage;
    artifactRef: string;
    artifactFingerprint: { algorithm: 'sha256' | 'sha512'; hash: string };
    trustState: ArtifactTrustChain['trustState'];
    signature?: ArtifactTrustChain['signature'];
    attestation?: ArtifactTrustChain['attestation'];
    deploymentLinkage?: ArtifactDeploymentLinkage;
  }): ArtifactTrustChain {
    const base: Omit<ArtifactTrustChain, 'signature' | 'attestation' | 'deployment'> = {
      source: params.sourceLinkage,
      artifactRef: params.artifactRef,
      artifactFingerprint: params.artifactFingerprint,
      trustState: params.trustState,
    };

    return {
      ...base,
      ...(params.signature !== undefined ? { signature: params.signature } : {}),
      ...(params.attestation !== undefined ? { attestation: params.attestation } : {}),
      ...(params.deploymentLinkage !== undefined ? { deployment: params.deploymentLinkage } : {}),
    };
  }
}

/**
 * Type for artifact trust state.
 */
export type ArtifactTrustState = ArtifactTrustChain['trustState'];
