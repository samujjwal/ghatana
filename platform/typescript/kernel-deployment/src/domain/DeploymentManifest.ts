import { z } from 'zod';

/**
 * Deployment environment types
 */
export type DeploymentEnvironment = 'local' | 'dev' | 'staging' | 'prod';

/**
 * Deployment status
 */
export type DeploymentStatus = 'pending' | 'in-progress' | 'deployed' | 'failed' | 'rolled-back';

/**
 * Deployment target types
 */
export type DeploymentTargetType = 'compose-local' | 'kubernetes' | 'helm' | 'terraform';

/**
 * Deployment surface status
 */
export interface DeploymentSurfaceStatus {
  surface: string;
  status: DeploymentStatus;
  artifactId: string;
  deploymentTarget: DeploymentTargetType;
  deployedAt: string | null;
  healthCheckPassed: boolean;
}

/**
 * Health check verification entry recorded in the manifest.
 */
export interface ManifestHealthCheckEntry {
  url: string;
  status: 'passed' | 'failed' | 'skipped';
  latencyMs: number | null;
  error: string | null;
  checkedAt: string;
}

/**
 * Verifier result recorded in the manifest.
 */
export interface ManifestVerifierResult {
  valid: boolean;
  checkedAt: string;
  errors: string[];
}

/**
 * Deployment manifest
 */
export interface DeploymentManifest {
  schemaVersion: string;
  productId: string;
  version: string;
  environment: DeploymentEnvironment;
  deploymentId: string;
  surfaces: DeploymentSurfaceStatus[];
  deployedAt: string;
  rollbackPlan: RollbackPlan;
  /** The deployment target adapter type (e.g. compose-local, kubernetes). */
  target?: DeploymentTargetType;
  /** Path to the artifact manifest that was deployed. */
  artifactManifestRef?: string;
  /** Per-service status map keyed by service name. */
  services?: Record<string, { status: string; healthCheckPassed: boolean }>;
  /** Live health check results written by the verifier. */
  healthChecks?: ManifestHealthCheckEntry[];
  /** Overall deployment status after verification. */
  overallStatus?: DeploymentStatus;
  /** Summary of the verifier run recorded during the verify phase. */
  verifierResult?: ManifestVerifierResult;
}

/**
 * Rollback plan
 */
export interface RollbackPlan {
  strategy: 'previous-artifact' | 'blue-green' | 'canary';
  targetVersion: string;
  reason: string;
  steps: string[];
}

const ManifestHealthCheckEntrySchema = z.object({
  url: z.string().min(1),
  status: z.enum(['passed', 'failed', 'skipped']),
  latencyMs: z.number().nullable(),
  error: z.string().nullable(),
  checkedAt: z.string().datetime(),
});

const ManifestVerifierResultSchema = z.object({
  valid: z.boolean(),
  checkedAt: z.string().datetime(),
  errors: z.array(z.string()),
});

/**
 * Zod schema for deployment manifest validation
 */
export const DeploymentManifestSchema = z.object({
  schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
  productId: z.string().min(1),
  version: z.string().min(1),
  environment: z.enum(['local', 'dev', 'staging', 'prod']),
  deploymentId: z.string().min(1),
  surfaces: z.array(
    z.object({
      surface: z.string().min(1),
      status: z.enum(['pending', 'in-progress', 'deployed', 'failed', 'rolled-back']),
      artifactId: z.string().min(1),
      deploymentTarget: z.enum(['compose-local', 'kubernetes', 'helm', 'terraform']),
      deployedAt: z.string().datetime().nullable(),
      healthCheckPassed: z.boolean(),
    }),
  ),
  deployedAt: z.string().datetime(),
  rollbackPlan: z.object({
    strategy: z.enum(['previous-artifact', 'blue-green', 'canary']),
    targetVersion: z.string().min(1),
    reason: z.string().min(1),
    steps: z.array(z.string()),
  }),
  // Optional extended fields
  target: z.enum(['compose-local', 'kubernetes', 'helm', 'terraform']).optional(),
  artifactManifestRef: z.string().optional(),
  services: z.record(z.string(), z.object({ status: z.string(), healthCheckPassed: z.boolean() })).optional(),
  healthChecks: z.array(ManifestHealthCheckEntrySchema).optional(),
  overallStatus: z.enum(['pending', 'in-progress', 'deployed', 'failed', 'rolled-back']).optional(),
  verifierResult: ManifestVerifierResultSchema.optional(),
});

export type DeploymentManifestInput = z.infer<typeof DeploymentManifestSchema>;

/**
 * Deployment manifest generator
 */
export class DeploymentManifestGenerator {
  /**
   * Create a new deployment manifest
   */
  createManifest(params: {
    productId: string;
    version: string;
    environment: DeploymentEnvironment;
    surfaces: Omit<DeploymentSurfaceStatus, 'deployedAt' | 'healthCheckPassed'>[];
    rollbackPlan: RollbackPlan;
    target?: DeploymentTargetType;
    artifactManifestRef?: string;
  }): DeploymentManifest {
    return {
      schemaVersion: '1.0.0',
      productId: params.productId,
      version: params.version,
      environment: params.environment,
      deploymentId: `deploy-${Date.now()}`,
      surfaces: params.surfaces.map((surface) => ({
        ...surface,
        deployedAt: null,
        healthCheckPassed: false,
      })),
      deployedAt: new Date().toISOString(),
      rollbackPlan: params.rollbackPlan,
      ...(params.target !== undefined ? { target: params.target } : {}),
      ...(params.artifactManifestRef !== undefined ? { artifactManifestRef: params.artifactManifestRef } : {}),
    };
  }

  /**
   * Validate a deployment manifest
   */
  validateManifest(manifest: unknown): DeploymentManifestInput {
    return DeploymentManifestSchema.parse(manifest);
  }
}
