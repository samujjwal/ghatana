import { z } from 'zod';

/**
 * Product deployment manifest
 */
export interface ProductDeploymentManifest {
  schemaVersion: string;
  productId: string;
  deploymentId: string;
  version: string;
  environment: string;
  deploymentStatus: 'pending' | 'in-progress' | 'succeeded' | 'failed' | 'rolled-back';
  timestamp: string;
  surfaces: DeploymentSurfaceManifest[];
  deploymentMetadata: DeploymentMetadata;
  healthChecks: HealthCheckResult[];
}

/**
 * Deployment surface manifest
 */
export interface DeploymentSurfaceManifest {
  surface: string;
  surfaceType: string;
  artifactId: string;
  deploymentTarget: string;
  deploymentStatus: 'pending' | 'deployed' | 'failed';
  deploymentDurationMs: number;
  deploymentLogPath?: string;
}

/**
 * Deployment metadata
 */
export interface DeploymentMetadata {
  sourceReleaseId: string;
  sourceReleaseVersion: string;
  deploymentStrategy: 'rolling' | 'blue-green' | 'canary';
  deploymentTrigger: string;
  triggeredBy: string;
  rollbackPlanId?: string;
}

/**
 * Health check result
 */
export interface HealthCheckResult {
  checkId: string;
  checkName: string;
  status: 'pending' | 'passed' | 'failed';
  checkedAt: string;
  details?: string;
}

/**
 * Zod schema for product deployment manifest validation
 */
export const ProductDeploymentManifestSchema = z.object({
  schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
  productId: z.string().min(1),
  deploymentId: z.string().min(1),
  version: z.string().min(1),
  environment: z.string().min(1),
  deploymentStatus: z.enum(['pending', 'in-progress', 'succeeded', 'failed', 'rolled-back']),
  timestamp: z.string().datetime(),
  surfaces: z.array(
    z.object({
      surface: z.string().min(1),
      surfaceType: z.string().min(1),
      artifactId: z.string().min(1),
      deploymentTarget: z.string().min(1),
      deploymentStatus: z.enum(['pending', 'deployed', 'failed']),
      deploymentDurationMs: z.number().int().nonnegative(),
      deploymentLogPath: z.string().optional(),
    }),
  ),
  deploymentMetadata: z.object({
    sourceReleaseId: z.string().min(1),
    sourceReleaseVersion: z.string().min(1),
    deploymentStrategy: z.enum(['rolling', 'blue-green', 'canary']),
    deploymentTrigger: z.string().min(1),
    triggeredBy: z.string().min(1),
    rollbackPlanId: z.string().optional(),
  }),
  healthChecks: z.array(
    z.object({
      checkId: z.string().min(1),
      checkName: z.string().min(1),
      status: z.enum(['pending', 'passed', 'failed']),
      checkedAt: z.string().datetime(),
      details: z.string().optional(),
    }),
  ),
});

export type ProductDeploymentManifestInput = z.infer<typeof ProductDeploymentManifestSchema>;
