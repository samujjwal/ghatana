import { z } from 'zod';

/**
 * Product release metadata
 */
export const ProductReleaseSchema = z.object({
  productId: z.string(),
  version: z.string(),
  sourceRef: z.string(),
  artifactManifest: z.string(),
  deploymentManifest: z.string(),
  releaseManifest: z.string(),
  environment: z.string(),
  timestamp: z.string(),
  releasedBy: z.string(),
});

export type ProductRelease = z.infer<typeof ProductReleaseSchema>;

/**
 * Release manager for creating and managing product releases
 */
export class ProductReleaseManager {
  /**
   * Create a release for a product
   */
  createRelease(release: ProductRelease): ProductRelease {
    ProductReleaseSchema.parse(release);
    return release;
  }

  /**
   * Validate release requirements
   */
  validateRelease(release: ProductRelease): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!release.artifactManifest) {
      errors.push('Artifact manifest is required');
    }
    if (!release.deploymentManifest) {
      errors.push('Deployment manifest is required');
    }
    if (!release.releaseManifest) {
      errors.push('Release manifest is required');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}
