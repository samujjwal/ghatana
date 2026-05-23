import { z } from 'zod';
import { promises as fs } from 'node:fs';
import * as path from 'node:path';

/**
 * Product rollback plan schema
 */
export const ProductRollbackPlanSchema = z.object({
  productId: z.string(),
  environment: z.string(),
  currentVersion: z.string(),
  targetVersion: z.string(),
  strategy: z.enum(['previous-artifact', 'last-known-good', 'manual']),
  previousArtifactSelection: z
    .object({
      artifactRef: z.string().trim().min(1),
      deploymentManifestRef: z.string().trim().min(1),
      selectedBy: z.string().trim().min(1),
      selectedAt: z.string().trim().min(1),
      selectionReason: z.string().trim().min(1),
    })
    .optional(),
  approvalGateRef: z.string().trim().min(1).optional(),
  reason: z.string(),
  rollbackBy: z.string(),
  timestamp: z.string(),
  manifestPath: z.string().optional(),
  verificationPlan: z.object({
    healthChecks: z.boolean(),
    smokeTests: z.boolean(),
    metrics: z.boolean(),
  }),
  healthcareVerificationPlan: z
    .object({
      consent: z.boolean(),
      piiClassification: z.boolean(),
      auditEvidence: z.boolean(),
      fhirValidation: z.boolean(),
      tenantDataSovereignty: z.boolean(),
    })
    .optional(),
});

export type ProductRollbackPlan = z.infer<typeof ProductRollbackPlanSchema>;

/**
 * Rollback plan manager
 */
export class ProductRollbackPlanManager {
  private readonly repoRoot: string;

  constructor(repoRoot: string = process.cwd()) {
    this.repoRoot = repoRoot;
  }

  async createRollbackPlan(plan: ProductRollbackPlan): Promise<ProductRollbackPlan> {
    ProductRollbackPlanSchema.parse(plan);

    // Validate rollback plan requirements
    const validation = await this.validateRollbackPlan(plan);
    if (!validation.valid) {
      throw new Error(`Rollback plan validation failed: ${validation.errors.join('; ')}`);
    }

    return plan;
  }

  async validateRollbackPlan(
    plan: ProductRollbackPlan
  ): Promise<{ valid: boolean; errors: string[] }> {
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
    if (plan.strategy === 'previous-artifact' && !plan.previousArtifactSelection) {
      errors.push('Previous artifact selection is required for previous-artifact rollback strategy');
    }
    if (plan.productId === 'phr') {
      if (!plan.approvalGateRef) {
        errors.push('PHR rollback requires rollback approval contract evidence');
      }
      if (!plan.healthcareVerificationPlan) {
        errors.push('PHR rollback requires healthcare post-rollback verification gates');
      } else {
        const healthcareGates = [
          ['consent', plan.healthcareVerificationPlan.consent],
          ['piiClassification', plan.healthcareVerificationPlan.piiClassification],
          ['auditEvidence', plan.healthcareVerificationPlan.auditEvidence],
          ['fhirValidation', plan.healthcareVerificationPlan.fhirValidation],
          ['tenantDataSovereignty', plan.healthcareVerificationPlan.tenantDataSovereignty],
        ] as const;
        for (const [gate, enabled] of healthcareGates) {
          if (!enabled) {
            errors.push(`PHR rollback healthcare gate is required: ${gate}`);
          }
        }
      }
    }

    // Validate manifest exists if path provided
    if (plan.manifestPath) {
      try {
        await fs.access(this.resolveManifestPath(plan.manifestPath));
      } catch {
        errors.push(`Rollback manifest not found at ${plan.manifestPath}`);
      }
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  private resolveManifestPath(manifestPath: string): string {
    return path.isAbsolute(manifestPath) ? manifestPath : path.join(this.repoRoot, manifestPath);
  }
}
