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
    };
  }

  /**
   * Validate a deployment manifest
   */
  validateManifest(manifest: unknown): DeploymentManifestInput {
    return DeploymentManifestSchema.parse(manifest);
  }
}
