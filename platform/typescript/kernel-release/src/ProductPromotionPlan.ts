import { z } from 'zod';
import { promises as fs } from 'node:fs';
import * as path from 'node:path';

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
  manifestPaths: z.object({
    artifactManifest: z.string().optional(),
    deploymentManifest: z.string().optional(),
    releaseManifest: z.string().optional(),
  }).optional(),
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
  private readonly repoRoot: string;

  constructor(repoRoot: string = process.cwd()) {
    this.repoRoot = repoRoot;
  }

  async createPromotionPlan(plan: ProductPromotionPlan): Promise<ProductPromotionPlan> {
    ProductPromotionPlanSchema.parse(plan);

    // Validate all required manifests exist
    const validation = await this.validatePromotionPlan(plan);
    if (!validation.valid) {
      throw new Error(`Promotion plan validation failed: ${validation.errors.join('; ')}`);
    }

    return plan;
  }

  async validatePromotionPlan(
    plan: ProductPromotionPlan
  ): Promise<{ valid: boolean; errors: string[] }> {
    const errors: string[] = [];

    // Check promotion requirements
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

    // Validate manifest files exist if paths provided
    if (plan.manifestPaths?.artifactManifest) {
      try {
        await fs.access(this.resolveManifestPath(plan.manifestPaths.artifactManifest));
      } catch {
        errors.push(
          `Artifact manifest not found at ${plan.manifestPaths.artifactManifest}`
        );
      }
    }

    if (plan.manifestPaths?.deploymentManifest) {
      try {
        await fs.access(this.resolveManifestPath(plan.manifestPaths.deploymentManifest));
      } catch {
        errors.push(
          `Deployment manifest not found at ${plan.manifestPaths.deploymentManifest}`
        );
      }
    }

    if (plan.manifestPaths?.releaseManifest) {
      try {
        await fs.access(this.resolveManifestPath(plan.manifestPaths.releaseManifest));
      } catch {
        errors.push(
          `Release manifest not found at ${plan.manifestPaths.releaseManifest}`
        );
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
