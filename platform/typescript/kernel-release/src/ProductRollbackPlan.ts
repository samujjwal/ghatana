import { z } from 'zod';

/**
 * Product rollback plan schema
 */
export const ProductRollbackPlanSchema = z.object({
  productId: z.string(),
  environment: z.string(),
  currentVersion: z.string(),
  targetVersion: z.string(),
  strategy: z.enum(['previous-artifact', 'last-known-good', 'manual']),
  reason: z.string(),
  rollbackBy: z.string(),
  timestamp: z.string(),
  verificationPlan: z.object({
    healthChecks: z.boolean(),
    smokeTests: z.boolean(),
    metrics: z.boolean(),
  }),
});

export type ProductRollbackPlan = z.infer<typeof ProductRollbackPlanSchema>;

/**
 * Rollback plan manager
 */
export class ProductRollbackPlanManager {
  createRollbackPlan(plan: ProductRollbackPlan): ProductRollbackPlan {
    ProductRollbackPlanSchema.parse(plan);
    return plan;
  }

  validateRollbackPlan(plan: ProductRollbackPlan): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!plan.targetVersion) {
      errors.push('Target version is required for rollback');
    }
    if (!plan.strategy) {
      errors.push('Rollback strategy is required');
    }
    if (!plan.reason) {
      errors.push('Rollback reason is required');
    }

    if (plan.strategy === 'previous-artifact' && !plan.currentVersion) {
      errors.push('Current version is required for previous-artifact rollback strategy');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}
