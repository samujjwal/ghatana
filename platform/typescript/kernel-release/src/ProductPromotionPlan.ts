import { z } from 'zod';

/**
 * Product promotion plan schema
 */
export const ProductPromotionPlanSchema = z.object({
  productId: z.string(),
  sourceEnvironment: z.string(),
  targetEnvironment: z.string(),
  promotionRequirements: z.object({
    artifactManifest: z.boolean(),
    deploymentManifest: z.boolean(),
    releaseManifest: z.boolean(),
    securityChecks: z.boolean(),
    privacyChecks: z.boolean(),
    licenseChecks: z.boolean(),
    conformanceChecks: z.boolean(),
    e2eChecks: z.boolean(),
    performanceChecks: z.boolean(),
  }),
  approvalGate: z.object({
    required: z.boolean(),
    approvers: z.array(z.string()),
    approved: z.boolean().optional(),
  }),
  rollbackPlan: z.object({
    strategy: z.string(),
    previousArtifact: z.string().optional(),
  }),
});

export type ProductPromotionPlan = z.infer<typeof ProductPromotionPlanSchema>;

/**
 * Promotion plan manager
 */
export class ProductPromotionPlanManager {
  createPromotionPlan(plan: ProductPromotionPlan): ProductPromotionPlan {
    ProductPromotionPlanSchema.parse(plan);
    return plan;
  }

  validatePromotionPlan(plan: ProductPromotionPlan): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!plan.promotionRequirements.artifactManifest) {
      errors.push('Artifact manifest is required for promotion');
    }
    if (!plan.promotionRequirements.deploymentManifest) {
      errors.push('Deployment manifest is required for promotion');
    }
    if (!plan.promotionRequirements.releaseManifest) {
      errors.push('Release manifest is required for promotion');
    }
    if (!plan.promotionRequirements.securityChecks) {
      errors.push('Security checks are required for promotion');
    }
    if (!plan.promotionRequirements.conformanceChecks) {
      errors.push('Conformance checks are required for promotion');
    }

    if (plan.approvalGate.required && !plan.approvalGate.approved) {
      errors.push('Approval gate is required but not approved');
    }

    if (!plan.rollbackPlan.strategy) {
      errors.push('Rollback plan strategy is required');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}
