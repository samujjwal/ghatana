import { z } from 'zod';

/**
 * Deployment environment types
 */
export type DeploymentEnvironment = 'local' | 'dev' | 'staging' | 'prod';

/**
 * Deployment status
 */
export type DeploymentStatus = 'pending' | 'in-progress' | 'deployed' | 'failed' | 'rolled-back';

export type DeploymentLifecyclePhase = 'deploy' | 'verify' | 'rollback';

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
  runId?: string;
  correlationId?: string;
  productId: string;
  productUnitId?: string;
  version: string;
  environment: DeploymentEnvironment;
  environmentSafety: DeploymentEnvironment;
  lifecyclePhase?: DeploymentLifecyclePhase;
  deploymentId: string;
  surfaces: DeploymentSurfaceStatus[];
  deployedAt: string;
  rollbackPlan: RollbackPlan;
  lifecycleResultRef?: string;
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
  approvalRef?: DeploymentApprovalRef;
  rollbackVerification?: RollbackVerificationStatus;
  scope?: DeploymentScope;
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

export interface DeploymentApprovalRef {
  approvalId: string;
  status: 'pending' | 'approved' | 'rejected';
  ref: string;
}

export interface RollbackVerificationStatus {
  verified: boolean;
  checkedAt: string;
  details?: string;
}

export interface DeploymentScope {
  tenant?: string;
  workspace?: string;
  project?: string;
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

const DeploymentApprovalRefSchema = z.object({
  approvalId: z.string().min(1),
  status: z.enum(['pending', 'approved', 'rejected']),
  ref: z.string().min(1),
});

const RollbackVerificationStatusSchema = z.object({
  verified: z.boolean(),
  checkedAt: z.string().datetime(),
  details: z.string().min(1).optional(),
});

const DeploymentScopeSchema = z.object({
  tenant: z.string().min(1).optional(),
  workspace: z.string().min(1).optional(),
  project: z.string().min(1).optional(),
});

/**
 * Zod schema for deployment manifest validation
 */
export const DeploymentManifestSchema = z.object({
  schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
  runId: z.string().min(1).optional(),
  correlationId: z.string().min(1).optional(),
  productId: z.string().min(1),
  productUnitId: z.string().min(1).optional(),
  version: z.string().min(1),
  environment: z.enum(['local', 'dev', 'staging', 'prod']),
  environmentSafety: z.enum(['local', 'dev', 'staging', 'prod']).optional(),
  lifecyclePhase: z.enum(['deploy', 'verify', 'rollback']).optional(),
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
  lifecycleResultRef: z.string().min(1).optional(),
  target: z.enum(['compose-local', 'kubernetes', 'helm', 'terraform']).optional(),
  artifactManifestRef: z.string().min(1).optional(),
  services: z.record(z.string(), z.object({ status: z.string(), healthCheckPassed: z.boolean() })).optional(),
  healthChecks: z.array(ManifestHealthCheckEntrySchema).optional(),
  overallStatus: z.enum(['pending', 'in-progress', 'deployed', 'failed', 'rolled-back']).optional(),
  verifierResult: ManifestVerifierResultSchema.optional(),
  approvalRef: DeploymentApprovalRefSchema.optional(),
  rollbackVerification: RollbackVerificationStatusSchema.optional(),
  scope: DeploymentScopeSchema.optional(),
}).superRefine((manifest, ctx) => {
  if (manifest.lifecyclePhase === 'deploy' || manifest.lifecyclePhase === 'verify') {
    if (!manifest.lifecycleResultRef) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['lifecycleResultRef'],
        message: 'lifecycleResultRef is required for deploy and verify manifests',
      });
    }
    if (!manifest.artifactManifestRef) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['artifactManifestRef'],
        message: 'artifactManifestRef is required for deploy and verify manifests',
      });
    }
  }
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
    runId?: string;
    correlationId?: string;
    productId: string;
    productUnitId?: string;
    version: string;
    environment: DeploymentEnvironment;
    environmentSafety?: DeploymentEnvironment;
    lifecyclePhase?: DeploymentLifecyclePhase;
    surfaces: Omit<DeploymentSurfaceStatus, 'deployedAt' | 'healthCheckPassed'>[];
    rollbackPlan: RollbackPlan;
    lifecycleResultRef?: string;
    target?: DeploymentTargetType;
    artifactManifestRef?: string;
    approvalRef?: DeploymentApprovalRef;
    rollbackVerification?: RollbackVerificationStatus;
    scope?: DeploymentScope;
  }): DeploymentManifest {
    return {
      schemaVersion: '1.0.0',
      ...(params.runId !== undefined ? { runId: params.runId } : {}),
      ...(params.correlationId !== undefined ? { correlationId: params.correlationId } : {}),
      productId: params.productId,
      ...(params.productUnitId !== undefined ? { productUnitId: params.productUnitId } : {}),
      version: params.version,
      environment: params.environment,
      environmentSafety: params.environmentSafety ?? params.environment,
      ...(params.lifecyclePhase !== undefined ? { lifecyclePhase: params.lifecyclePhase } : {}),
      deploymentId: `deploy-${Date.now()}`,
      surfaces: params.surfaces.map((surface) => ({
        ...surface,
        deployedAt: null,
        healthCheckPassed: false,
      })),
      deployedAt: new Date().toISOString(),
      rollbackPlan: params.rollbackPlan,
      ...(params.lifecycleResultRef !== undefined ? { lifecycleResultRef: params.lifecycleResultRef } : {}),
      ...(params.target !== undefined ? { target: params.target } : {}),
      ...(params.artifactManifestRef !== undefined ? { artifactManifestRef: params.artifactManifestRef } : {}),
      ...(params.approvalRef !== undefined ? { approvalRef: params.approvalRef } : {}),
      ...(params.rollbackVerification !== undefined ? { rollbackVerification: params.rollbackVerification } : {}),
      ...(params.scope !== undefined ? { scope: params.scope } : {}),
    };
  }

  /**
   * Validate a deployment manifest
   */
  validateManifest(manifest: unknown): DeploymentManifestInput {
    return DeploymentManifestSchema.parse(manifest);
  }
}
