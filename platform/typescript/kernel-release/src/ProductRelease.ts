import { z } from 'zod';
import { promises as fs } from 'node:fs';
import * as path from 'node:path';

/**
 * Product artifact manifest schema
 */
const ProductArtifactManifestSchema = z.object({
  schemaVersion: z.string(),
  productId: z.string(),
  phase: z.string(),
  surface: z.string(),
  timestamp: z.string(),
  artifacts: z.array(
    z.object({
      id: z.string(),
      path: z.string(),
      metadata: z.object({
        type: z.string(),
        version: z.string(),
        buildNumber: z.string().optional(),
        gitCommit: z.string().optional(),
        gitBranch: z.string().optional(),
        timestamp: z.string(),
        sizeBytes: z.number(),
      }),
      fingerprint: z.object({
        algorithm: z.string(),
        hash: z.string(),
      }),
      expected: z.boolean(),
      found: z.boolean(),
    })
  ),
});

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
  private readonly repoRoot: string;

  constructor(repoRoot: string = process.cwd()) {
    this.repoRoot = repoRoot;
  }

  /**
   * Create a release for a product
   */
  async createRelease(release: ProductRelease): Promise<ProductRelease> {
    ProductReleaseSchema.parse(release);

    // Validate all required manifests exist and are valid
    const validation = await this.validateRelease(release);
    if (!validation.valid) {
      throw new Error(`Release validation failed: ${validation.errors.join('; ')}`);
    }

    return release;
  }

  /**
   * Validate release requirements (checks manifest files exist and are valid)
   */
  async validateRelease(
    release: ProductRelease
  ): Promise<{ valid: boolean; errors: string[] }> {
    const errors: string[] = [];

    // Check artifact manifest
    try {
      const artifactPath = this.resolveManifestPath(release.artifactManifest);
      const artifactContent = await fs.readFile(artifactPath, 'utf-8');
      const artifactManifest = JSON.parse(artifactContent);
      ProductArtifactManifestSchema.parse(artifactManifest);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      errors.push(`Invalid artifact manifest at ${release.artifactManifest}: ${message}`);
    }

    // Check deployment manifest exists
    try {
      await fs.access(this.resolveManifestPath(release.deploymentManifest));
    } catch {
      errors.push(`Deployment manifest not found at ${release.deploymentManifest}`);
    }

    // Check release manifest exists
    try {
      await fs.access(this.resolveManifestPath(release.releaseManifest));
    } catch {
      errors.push(`Release manifest not found at ${release.releaseManifest}`);
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
