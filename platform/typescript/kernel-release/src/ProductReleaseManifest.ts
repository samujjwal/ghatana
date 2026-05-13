import { z } from 'zod';

/**
 * Product release manifest schema
 */
export const ProductReleaseManifestSchema = z.object({
  schemaVersion: z.string(),
  productId: z.string(),
  version: z.string(),
  releaseNotes: z.string().optional(),
  changes: z.array(z.object({
    type: z.enum(['feature', 'fix', 'breaking', 'security']),
    description: z.string(),
    affectedSurfaces: z.array(z.string()),
  })),
  securityChecks: z.object({
    sast: z.boolean(),
    dependencyScan: z.boolean(),
    containerScan: z.boolean(),
  }),
  privacyChecks: z.object({
    dataClassification: z.boolean(),
    piiAudit: z.boolean(),
  }),
  licenseChecks: z.object({
    approvedLicenses: z.boolean(),
    compliance: z.boolean(),
  }),
  conformanceChecks: z.object({
    manifest: z.boolean(),
    observability: z.boolean(),
    security: z.boolean(),
  }),
  e2eChecks: z.object({
    passed: z.boolean(),
    coverage: z.number(),
  }),
  performanceChecks: z.object({
    responseTimeP95: z.number(),
    responseTimeP99: z.number(),
    errorRate: z.number(),
  }),
});

export type ProductReleaseManifest = z.infer<typeof ProductReleaseManifestSchema>;

/**
 * Release manifest manager
 */
export class ProductReleaseManifestManager {
  createManifest(manifest: ProductReleaseManifest): ProductReleaseManifest {
    ProductReleaseManifestSchema.parse(manifest);
    return manifest;
  }

  validateManifest(manifest: ProductReleaseManifest): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!manifest.securityChecks.sast) {
      errors.push('SAST check is required');
    }
    if (!manifest.securityChecks.dependencyScan) {
      errors.push('Dependency scan is required');
    }
    if (!manifest.conformanceChecks.manifest) {
      errors.push('Manifest conformance is required');
    }
    if (!manifest.e2eChecks.passed) {
      errors.push('E2E checks must pass');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}
