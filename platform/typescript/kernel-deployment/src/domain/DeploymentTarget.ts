import { z } from 'zod';

/**
 * Deployment target
 */
export interface DeploymentTarget {
  id: string;
  name: string;
  type: DeploymentTargetType;
  config: DeploymentTargetConfig;
  capabilities: DeploymentCapability[];
}

/**
 * Deployment target type
 */
export type DeploymentTargetType = 'compose-local' | 'kubernetes' | 'helm' | 'terraform';

/**
 * Deployment target configuration
 */
export interface DeploymentTargetConfig {
  endpoint?: string;
  namespace?: string;
  region?: string;
  cluster?: string;
  credentials?: {
    type: string;
    path?: string;
    secretRef?: string;
  };
}

/**
 * Deployment capability
 */
export interface DeploymentCapability {
  type: 'rolling-update' | 'blue-green' | 'canary' | 'rollback' | 'health-check';
  supported: boolean;
}

/**
 * Zod schema for deployment target validation
 */
export const DeploymentTargetSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  type: z.enum(['compose-local', 'kubernetes', 'helm', 'terraform']),
  config: z.object({
    endpoint: z.string().optional(),
    namespace: z.string().optional(),
    region: z.string().optional(),
    cluster: z.string().optional(),
    credentials: z.object({
      type: z.string(),
      path: z.string().optional(),
      secretRef: z.string().optional(),
    }).optional(),
  }),
  capabilities: z.array(
    z.object({
      type: z.enum(['rolling-update', 'blue-green', 'canary', 'rollback', 'health-check']),
      supported: z.boolean(),
    }),
  ),
});

export type DeploymentTargetInput = z.infer<typeof DeploymentTargetSchema>;
