import { z } from 'zod';

/**
 * Deployment plan
 */
export interface DeploymentPlan {
  schemaVersion: string;
  productId: string;
  version: string;
  environment: string;
  deploymentId: string;
  strategy: DeploymentStrategy;
  surfaces: DeploymentSurfacePlan[];
  healthChecks: HealthCheckConfig[];
  rollbackPlan: RollbackPlanConfig;
}

/**
 * Deployment strategy
 */
export type DeploymentStrategy = 'rolling' | 'blue-green' | 'canary';

/**
 * Deployment surface plan
 */
export interface DeploymentSurfacePlan {
  surface: string;
  surfaceType: string;
  artifactId: string;
  deploymentTarget: string;
  order: number;
  dependsOn: string[];
}

/**
 * Health check configuration
 */
export interface HealthCheckConfig {
  checkId: string;
  checkName: string;
  type: 'http' | 'tcp' | 'command';
  config: Record<string, unknown>;
  timeoutMs: number;
  retries: number;
}

/**
 * Rollback plan configuration
 */
export interface RollbackPlanConfig {
  strategy: 'previous-artifact' | 'blue-green' | 'canary';
  targetVersion: string;
  reason: string;
  steps: string[];
}

/**
 * Zod schema for deployment plan validation
 */
export const DeploymentPlanSchema = z.object({
  schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
  productId: z.string().min(1),
  version: z.string().min(1),
  environment: z.string().min(1),
  deploymentId: z.string().min(1),
  strategy: z.enum(['rolling', 'blue-green', 'canary']),
  surfaces: z.array(
    z.object({
      surface: z.string().min(1),
      surfaceType: z.string().min(1),
      artifactId: z.string().min(1),
      deploymentTarget: z.string().min(1),
      order: z.number().int().nonnegative(),
      dependsOn: z.array(z.string()),
    }),
  ),
  healthChecks: z.array(
    z.object({
      checkId: z.string().min(1),
      checkName: z.string().min(1),
      type: z.enum(['http', 'tcp', 'command']),
      config: z.record(z.unknown()),
      timeoutMs: z.number().int().nonnegative(),
      retries: z.number().int().nonnegative(),
    }),
  ),
  rollbackPlan: z.object({
    strategy: z.enum(['previous-artifact', 'blue-green', 'canary']),
    targetVersion: z.string().min(1),
    reason: z.string().min(1),
    steps: z.array(z.string()),
  }),
});

export type DeploymentPlanInput = z.infer<typeof DeploymentPlanSchema>;
