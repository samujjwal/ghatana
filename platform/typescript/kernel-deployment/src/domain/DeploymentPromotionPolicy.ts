import { z } from 'zod';

/**
 * Deployment promotion policy
 */
export interface DeploymentPromotionPolicy {
  policyId: string;
  name: string;
  description: string;
  sourceEnvironment: string;
  targetEnvironment: string;
  requirements: PromotionRequirements;
  approval: PromotionApproval;
}

/**
 * Promotion requirements
 */
export interface PromotionRequirements {
  artifactVerification: boolean;
  securityScan: boolean;
  conformanceCheck: boolean;
  e2eTests: boolean;
  performanceTests: boolean;
  minSuccessRate: number;
}

/**
 * Promotion approval
 */
export interface PromotionApproval {
  required: boolean;
  approvers: string[];
  timeoutHours: number;
}

/**
 * Zod schema for deployment promotion policy validation
 */
export const DeploymentPromotionPolicySchema = z.object({
  policyId: z.string().min(1),
  name: z.string().min(1),
  description: z.string(),
  sourceEnvironment: z.string().min(1),
  targetEnvironment: z.string().min(1),
  requirements: z.object({
    artifactVerification: z.boolean(),
    securityScan: z.boolean(),
    conformanceCheck: z.boolean(),
    e2eTests: z.boolean(),
    performanceTests: z.boolean(),
    minSuccessRate: z.number().min(0).max(100),
  }),
  approval: z.object({
    required: z.boolean(),
    approvers: z.array(z.string()),
    timeoutHours: z.number().int().nonnegative(),
  }),
});

export type DeploymentPromotionPolicyInput = z.infer<typeof DeploymentPromotionPolicySchema>;
