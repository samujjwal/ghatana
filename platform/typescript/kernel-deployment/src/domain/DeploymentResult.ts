import { z } from 'zod';

/**
 * Deployment result
 */
export interface DeploymentResult {
  schemaVersion: string;
  productId: string;
  version: string;
  environment: string;
  deploymentId: string;
  status: DeploymentStatus;
  startedAt: string;
  completedAt: string;
  surfaces: DeploymentSurfaceResult[];
  healthChecks: HealthCheckResult[];
  failure?: DeploymentFailure;
}

/**
 * Deployment status
 */
export type DeploymentStatus = 'pending' | 'in-progress' | 'succeeded' | 'failed' | 'rolled-back';

/**
 * Deployment surface result
 */
export interface DeploymentSurfaceResult {
  surface: string;
  surfaceType: string;
  artifactId: string;
  deploymentTarget: string;
  status: DeploymentStatus;
  startedAt: string;
  completedAt: string;
  durationMs: number;
  logs?: string[];
}

/**
 * Health check result
 */
export interface HealthCheckResult {
  checkId: string;
  checkName: string;
  status: 'pending' | 'passed' | 'failed';
  checkedAt: string;
  durationMs: number;
  details?: string;
}

/**
 * Deployment failure
 */
export interface DeploymentFailure {
  surface?: string;
  step?: string;
  message: string;
  cause?: string;
}

/**
 * Zod schema for deployment result validation
 */
export const DeploymentResultSchema = z.object({
  schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
  productId: z.string().min(1),
  version: z.string().min(1),
  environment: z.string().min(1),
  deploymentId: z.string().min(1),
  status: z.enum(['pending', 'in-progress', 'succeeded', 'failed', 'rolled-back']),
  startedAt: z.string().datetime(),
  completedAt: z.string().datetime(),
  surfaces: z.array(
    z.object({
      surface: z.string().min(1),
      surfaceType: z.string().min(1),
      artifactId: z.string().min(1),
      deploymentTarget: z.string().min(1),
      status: z.enum(['pending', 'in-progress', 'succeeded', 'failed', 'rolled-back']),
      startedAt: z.string().datetime(),
      completedAt: z.string().datetime(),
      durationMs: z.number().int().nonnegative(),
      logs: z.array(z.string()).optional(),
    }),
  ),
  healthChecks: z.array(
    z.object({
      checkId: z.string().min(1),
      checkName: z.string().min(1),
      status: z.enum(['pending', 'passed', 'failed']),
      checkedAt: z.string().datetime(),
      durationMs: z.number().int().nonnegative(),
      details: z.string().optional(),
    }),
  ),
  failure: z.object({
    surface: z.string().optional(),
    step: z.string().optional(),
    message: z.string(),
    cause: z.string().optional(),
  }).optional(),
});

export type DeploymentResultInput = z.infer<typeof DeploymentResultSchema>;
